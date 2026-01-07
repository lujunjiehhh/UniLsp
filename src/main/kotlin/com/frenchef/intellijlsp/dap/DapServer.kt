package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.backend.DebuggerEventListener
import com.frenchef.intellijlsp.dap.backend.IntellijDebuggerBackend
import com.frenchef.intellijlsp.dap.handlers.AttachHandler
import com.frenchef.intellijlsp.dap.handlers.ContinueHandler
import com.frenchef.intellijlsp.dap.handlers.ConfigurationDoneHandler
import com.frenchef.intellijlsp.dap.handlers.DapRequestRouter
import com.frenchef.intellijlsp.dap.handlers.DisconnectHandler
import com.frenchef.intellijlsp.dap.handlers.EvaluateHandler
import com.frenchef.intellijlsp.dap.handlers.InitializeHandler
import com.frenchef.intellijlsp.dap.handlers.LaunchHandler
import com.frenchef.intellijlsp.dap.handlers.NextHandler
import com.frenchef.intellijlsp.dap.handlers.PauseHandler
import com.frenchef.intellijlsp.dap.handlers.ScopesHandler
import com.frenchef.intellijlsp.dap.handlers.SetBreakpointsHandler
import com.frenchef.intellijlsp.dap.handlers.StackTraceHandler
import com.frenchef.intellijlsp.dap.handlers.StepInHandler
import com.frenchef.intellijlsp.dap.handlers.StepOutHandler
import com.frenchef.intellijlsp.dap.handlers.TerminateHandler
import com.frenchef.intellijlsp.dap.handlers.ThreadsHandler
import com.frenchef.intellijlsp.dap.handlers.VariablesHandler
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.DapEvent
import com.frenchef.intellijlsp.dap.model.DapEvents
import com.frenchef.intellijlsp.dap.model.DapResponse
import com.frenchef.intellijlsp.dap.model.BreakpointEventBody
import com.frenchef.intellijlsp.dap.model.ContinuedEventBody
import com.frenchef.intellijlsp.dap.model.DapErrorId
import com.frenchef.intellijlsp.dap.model.DapException
import com.frenchef.intellijlsp.dap.model.ExitedEventBody
import com.frenchef.intellijlsp.dap.model.InitializedEventBody
import com.frenchef.intellijlsp.dap.model.OutputEventBody
import com.frenchef.intellijlsp.dap.model.StoppedEventBody
import com.frenchef.intellijlsp.dap.model.TerminatedEventBody
import com.frenchef.intellijlsp.dap.model.ThreadEventBody
import com.frenchef.intellijlsp.protocol.MessageReader
import com.frenchef.intellijlsp.protocol.MessageWriter
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DAP Server
 *
 * Manages the DAP session lifecycle and routes requests over a framed stream.
 */
class DapServer(
    private val project: Project,
    private val input: InputStream,
    private val output: OutputStream,
    private val onExit: (() -> Unit)? = null,
    private val closeStreamsOnShutdown: Boolean = true,
    private val telemetry: DapTelemetry = NoopDapTelemetry
) {
    private val log = logger<DapServer>()
    private val session = DapSession()
    private val backend: DebuggerBackend = IntellijDebuggerBackend(project)
    private val router = DapRequestRouter(session)

    private val messageReader = MessageReader(input)
    private val messageWriter = MessageWriter(output)

    private val running = AtomicBoolean(false)
    private val shutdownStarted = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val debuggerEventListener = object : DebuggerEventListener {
        override fun onStopped(
            reason: com.frenchef.intellijlsp.dap.model.StoppedReason,
            threadId: Int?,
            description: String?,
            allThreadsStopped: Boolean?,
            hitBreakpointIds: List<Int>?
        ) {
            if (!running.get()) {
                return
            }

            if (threadId != null) {
                if (!session.onStopped(threadId)) {
                    DapErrors.logInternalError(
                        "Failed to transition session to STOPPED for thread $threadId"
                    )
                }
            } else {
                log.warn("Stopped event received without thread id")
            }

            val body = StoppedEventBody(
                reason = reason,
                description = description,
                threadId = threadId,
                allThreadsStopped = allThreadsStopped,
                hitBreakpointIds = hitBreakpointIds
            )
            scope.launch {
                sendEvent(
                    DapEvent(
                        seq = session.nextSeq(),
                        event = DapEvents.STOPPED,
                        body = DapGson.instance.toJsonTree(body)
                    )
                )
            }
        }

        override fun onContinued(threadId: Int, allThreadsContinued: Boolean?) {
            if (!running.get()) {
                return
            }

            if (session.isStopped()) {
                if (!session.onContinued()) {
                    DapErrors.logInternalError(
                        "Failed to transition session to RUNNING for thread $threadId"
                    )
                }
            } else {
                log.warn("Continued event received while session state is ${session.getState()}")
            }

            val body = ContinuedEventBody(
                threadId = threadId,
                allThreadsContinued = allThreadsContinued
            )
            scope.launch {
                sendEvent(
                    DapEvent(
                        seq = session.nextSeq(),
                        event = DapEvents.CONTINUED,
                        body = DapGson.instance.toJsonTree(body)
                    )
                )
            }
        }

        override fun onThread(
            reason: com.frenchef.intellijlsp.dap.model.ThreadEventReason,
            threadId: Int
        ) {
            if (!running.get()) {
                return
            }

            val body = ThreadEventBody(
                reason = reason,
                threadId = threadId
            )
            scope.launch {
                sendEvent(
                    DapEvent(
                        seq = session.nextSeq(),
                        event = DapEvents.THREAD,
                        body = DapGson.instance.toJsonTree(body)
                    )
                )
            }
        }

        override fun onOutput(
            category: com.frenchef.intellijlsp.dap.model.OutputCategory?,
            output: String,
            source: com.frenchef.intellijlsp.dap.model.Source?,
            line: Int?,
            column: Int?
        ) {
            if (!running.get()) {
                return
            }

            val body = OutputEventBody(
                category = category,
                output = output,
                source = source,
                line = line,
                column = column
            )
            scope.launch {
                sendEvent(
                    DapEvent(
                        seq = session.nextSeq(),
                        event = DapEvents.OUTPUT,
                        body = DapGson.instance.toJsonTree(body)
                    )
                )
            }
        }

        override fun onExited(exitCode: Int) {
            if (!running.get()) {
                return
            }

            val body = ExitedEventBody(exitCode)
            scope.launch {
                sendEvent(
                    DapEvent(
                        seq = session.nextSeq(),
                        event = DapEvents.EXITED,
                        body = DapGson.instance.toJsonTree(body)
                    )
                )
            }
        }

        override fun onTerminated(restart: Boolean?) {
            if (!running.get()) {
                return
            }

            session.onTerminated()
            val body = TerminatedEventBody(
                restart = restart?.let { DapGson.instance.toJsonTree(it) }
            )
            scope.launch {
                sendEvent(
                    DapEvent(
                        seq = session.nextSeq(),
                        event = DapEvents.TERMINATED,
                        body = DapGson.instance.toJsonTree(body)
                    )
                )
            }
        }

        override fun onBreakpoint(
            reason: com.frenchef.intellijlsp.dap.model.BreakpointEventReason,
            breakpoint: com.frenchef.intellijlsp.dap.model.Breakpoint
        ) {
            if (!running.get()) {
                return
            }

            val body = BreakpointEventBody(
                reason = reason,
                breakpoint = breakpoint
            )
            scope.launch {
                sendEvent(
                    DapEvent(
                        seq = session.nextSeq(),
                        event = DapEvents.BREAKPOINT,
                        body = DapGson.instance.toJsonTree(body)
                    )
                )
            }
        }
    }

    init {
        registerHandlers()
        backend.setEventListener(debuggerEventListener)
    }

    /**
     * Start the DAP server loop.
     */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("DAP server already running")
            return
        }

        scope.launch {
            try {
                messageLoop()
            } catch (e: Exception) {
                DapErrors.logTransportError("DAP server message loop failed", e)
            } finally {
                shutdownOnce()
            }
        }
    }

    /**
     * Stop the DAP server loop.
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        scope.cancel()
        shutdownOnce()
    }

    private suspend fun messageLoop() {
        while (running.get()) {
            try {
                val json = withContext(Dispatchers.IO) {
                    messageReader.readMessage()
                } ?: break

                processMessage(json)
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                DapErrors.logTransportError("Error reading DAP message", e)
            }
        }
    }

    private suspend fun processMessage(json: JsonObject) {
        try {
            val type = json.get("type")?.asString
            if (type == "request") {
                val command = json.get("command")?.asString ?: "unknown"
                val seq = json.get("seq")?.asInt ?: 0
                recordTelemetry {
                    telemetry.onRequest(command, seq)
                }
            }

            val response = router.handleMessage(json)
            if (response != null) {
                sendResponse(response)
                if (response.command == DapCommands.INITIALIZE && response.success) {
                    sendInitializedEvent()
                }
            }
        } catch (e: DapException) {
            recordTelemetry {
                telemetry.onError(DapErrors.categorize(e), "Error processing DAP message", e)
            }
            DapErrors.logInternalError("Error processing DAP message", e)
            if (e.errorId == DapErrorId.INTERNAL_ERROR) {
                running.set(false)
            }
        } catch (e: Exception) {
            recordTelemetry {
                telemetry.onError(DapErrors.ErrorCategory.INTERNAL, "Error processing DAP message", e)
            }
            DapErrors.logInternalError("Error processing DAP message", e)
        }
    }

    private suspend fun sendResponse(response: DapResponse) {
        recordTelemetry {
            telemetry.onResponse(response.command, response.success, response.seq, response.requestSeq)
        }
        sendMessage(response, "response")
    }

    private suspend fun sendEvent(event: DapEvent) {
        recordTelemetry {
            telemetry.onEvent(event.event, event.seq)
        }
        sendMessage(event, "event")
    }

    private suspend fun sendMessage(message: Any, label: String) {
        try {
            val json = DapGson.instance.toJsonTree(message).asJsonObject
            withContext(Dispatchers.IO) {
                messageWriter.writeMessage(json)
            }
        } catch (e: Exception) {
            recordTelemetry {
                telemetry.onError(DapErrors.ErrorCategory.TRANSPORT, "Failed to send DAP $label", e)
            }
            DapErrors.logTransportError("Failed to send DAP $label", e)
        }
    }

    private fun recordTelemetry(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("DAP telemetry hook failed", e)
        }
    }

    private suspend fun sendInitializedEvent() {
        val event = DapEvent(
            seq = session.nextSeq(),
            event = DapEvents.INITIALIZED,
            body = DapGson.instance.toJsonTree(InitializedEventBody())
        )
        sendEvent(event)
        if (!session.onInitializedEventSent()) {
            throw DapErrors.internalError(
                "Failed to transition session state after sending initialized event"
            )
        }
    }

    private fun registerHandlers() {
        router.registerHandler(DapCommands.INITIALIZE, InitializeHandler(session))
        router.registerHandler(DapCommands.CONFIGURATION_DONE, ConfigurationDoneHandler(session))
        router.registerHandler(DapCommands.LAUNCH, LaunchHandler(backend))
        router.registerHandler(DapCommands.ATTACH, AttachHandler(backend))
        router.registerHandler(DapCommands.SET_BREAKPOINTS, SetBreakpointsHandler(backend))
        router.registerHandler(DapCommands.THREADS, ThreadsHandler(backend))
        router.registerHandler(DapCommands.STACK_TRACE, StackTraceHandler(backend))
        router.registerHandler(DapCommands.CONTINUE, ContinueHandler(backend))
        router.registerHandler(DapCommands.NEXT, NextHandler(backend))
        router.registerHandler(DapCommands.STEP_IN, StepInHandler(backend))
        router.registerHandler(DapCommands.STEP_OUT, StepOutHandler(backend))
        router.registerHandler(DapCommands.PAUSE, PauseHandler(backend))
        router.registerHandler(DapCommands.SCOPES, ScopesHandler(backend))
        router.registerHandler(DapCommands.VARIABLES, VariablesHandler(backend))
        router.registerHandler(DapCommands.EVALUATE, EvaluateHandler(backend))
        router.registerHandler(DapCommands.DISCONNECT, DisconnectHandler(session, backend))
        router.registerHandler(DapCommands.TERMINATE, TerminateHandler(session, backend))
    }

    private fun shutdownOnce() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return
        }

        shutdownInternal()
    }

    private fun shutdownInternal() {
        try {
            if (!session.isTerminated()) {
                runBlocking {
                    backend.disconnect(false)
                }
            }
            session.reset()
        } catch (e: Exception) {
            DapErrors.logInternalError("Error during DAP shutdown", e)
        } finally {
            backend.setEventListener(null)
            if (closeStreamsOnShutdown) {
                try {
                    input.close()
                } catch (_: Exception) {
                }
                try {
                    output.close()
                } catch (_: Exception) {
                }
            }

            onExit?.invoke()
        }
    }
}
