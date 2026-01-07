package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.backend.IntellijDebuggerBackend
import com.frenchef.intellijlsp.dap.handlers.AttachHandler
import com.frenchef.intellijlsp.dap.handlers.ConfigurationDoneHandler
import com.frenchef.intellijlsp.dap.handlers.DapRequestRouter
import com.frenchef.intellijlsp.dap.handlers.InitializeHandler
import com.frenchef.intellijlsp.dap.handlers.LaunchHandler
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.DapEvent
import com.frenchef.intellijlsp.dap.model.DapEvents
import com.frenchef.intellijlsp.dap.model.DapResponse
import com.frenchef.intellijlsp.dap.model.InitializedEventBody
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
    private val closeStreamsOnShutdown: Boolean = true
) {
    private val log = logger<DapServer>()
    private val gson = DapGson.instance

    private val session = DapSession()
    private val backend: DebuggerBackend = IntellijDebuggerBackend(project)
    private val router = DapRequestRouter(session)

    private val messageReader = MessageReader(input)
    private val messageWriter = MessageWriter(output)

    private val running = AtomicBoolean(false)
    private val shutdownStarted = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        registerHandlers()
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

    /**
     * Continuously reads and processes DAP JSON messages until the server stops.
     *
     * Reads messages from the framed input and forwards each parsed JSON object to
     * processMessage. The loop ends when no message is available (end of stream),
     * when the coroutine is cancelled, or when the server is no longer running.
     * Transport read errors are logged.
     */
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

    /**
     * Processes a single incoming DAP JSON message, sending any generated response and related events.
     *
     * If processing yields a response, the response is sent to the client. If the response is for the
     * INITIALIZE command and indicates success, an `initialized` event is also sent. Any exceptions
     * raised during processing are logged as internal errors and swallowed.
     *
     * @param json The parsed DAP message as a `JsonObject`.
     */
    private suspend fun processMessage(json: JsonObject) {
        try {
            val response = router.handleMessage(json)
            if (response != null) {
                sendResponse(response)
                if (response.command == DapCommands.INITIALIZE && response.success) {
                    sendInitializedEvent()
                }
            }
        } catch (e: Exception) {
            DapErrors.logInternalError("Error processing DAP message", e)
        }
    }

    /**
     * Sends the given DAP response message to the client.
     *
     * @param response The response to send.
     */
    private fun sendResponse(response: DapResponse) {
        sendMessage(response, "response")
    }

    /**
     * Sends a DAP event to the connected client.
     *
     * @param event The event to send.
     */
    private fun sendEvent(event: DapEvent) {
        sendMessage(event, "event")
    }

    /**
     * Serializes a DAP message to JSON and writes it to the outbound framed message stream.
     *
     * If sending fails, the transport error is logged and the exception is suppressed.
     *
     * @param message The DAP message object to send (for example a response or event model).
     * @param label Short label used in error messages to identify the message type (e.g., "response", "event").
     */
    private fun sendMessage(message: Any, label: String) {
        try {
            val json = gson.toJsonTree(message).asJsonObject
            messageWriter.writeMessage(json)
        } catch (e: Exception) {
            DapErrors.logTransportError("Failed to send DAP $label", e)
        }
    }

    /**
     * Sends the protocol `initialized` event to the client and advances the server session state.
     *
     * Constructs an `INITIALIZED` DAP event with a new sequence number, sends it, and then updates
     * the internal session to reflect that the initialized event was dispatched. If the session
     * fails to transition after sending the event, an internal error is logged.
     */
    private fun sendInitializedEvent() {
        val event = DapEvent(
            seq = session.nextSeq(),
            event = DapEvents.INITIALIZED,
            body = gson.toJsonTree(InitializedEventBody())
        )
        sendEvent(event)
        if (!session.onInitializedEventSent()) {
            DapErrors.logInternalError("Failed to transition session state after sending initialized event")
        }
    }

    /**
     * Registers DAP command handlers on the request router.
     *
     * Maps:
     * - `DapCommands.INITIALIZE` to `InitializeHandler(session)`
     * - `DapCommands.CONFIGURATION_DONE` to `ConfigurationDoneHandler(session)`
     * - `DapCommands.LAUNCH` to `LaunchHandler(backend)`
     * - `DapCommands.ATTACH` to `AttachHandler(backend)`
     */
    private fun registerHandlers() {
        router.registerHandler(DapCommands.INITIALIZE, InitializeHandler(session))
        router.registerHandler(DapCommands.CONFIGURATION_DONE, ConfigurationDoneHandler(session))
        router.registerHandler(DapCommands.LAUNCH, LaunchHandler(backend))
        router.registerHandler(DapCommands.ATTACH, AttachHandler(backend))
    }

    /**
     * Initiates the server shutdown exactly once.
     *
     * Marks the shutdown as started and, if this is the first invocation, calls shutdownInternal().
     */
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