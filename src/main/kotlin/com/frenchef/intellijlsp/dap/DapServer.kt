package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.backend.DebuggerEventListener
import com.frenchef.intellijlsp.dap.backend.IntellijDebuggerBackend
import com.frenchef.intellijlsp.dap.handlers.*
import com.frenchef.intellijlsp.dap.model.*
import com.frenchef.intellijlsp.protocol.MessageReader
import com.frenchef.intellijlsp.protocol.MessageWriter
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DAP Server
 * 
 * Main entry point for the Debug Adapter Protocol server.
 * Manages the DAP session lifecycle, request routing, and event emission.
 * 
 * Phase 4: Integration
 * Task: T060 - Implement DAP server main loop
 */
class DapServer(
    private val project: Project,
    private val input: InputStream,
    private val output: OutputStream
) {
    
    private val log = logger<DapServer>()
    private val gson = DapGson.instance
    
    // Core components
    private val session = DapSession()
    private val backend: DebuggerBackend = IntellijDebuggerBackend(project)
    private val router = DapRequestRouter(session)
    
    // I/O components
    private val messageReader = MessageReader(input)
    private val messageWriter = MessageWriter(output)
    
    // Event emitter
    private val eventEmitter = DapEventEmitter(session) { event ->
        sendEvent(event)
    }
    
    // Server state
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Register request handlers
        registerHandlers()
        
        // Set up backend event listener
        backend.setEventListener(BackendEventAdapter())
    }
    
    /**
     * Start the DAP server.
     */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("DAP server already running")
            return
        }
        
        DapErrors.logInfo("Starting DAP server")
        
        scope.launch {
            try {
                messageLoop()
            } catch (e: Exception) {
                DapErrors.logInternalError("DAP server message loop failed", e)
            } finally {
                shutdown()
            }
        }
    }
    
    /**
     * Stop the DAP server.
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) {
            log.debug("DAP server already stopped")
            return
        }
        
        DapErrors.logInfo("Stopping DAP server")
        
        scope.cancel()
        shutdown()
    }
    
    /**
     * Main message processing loop.
     */
    private suspend fun messageLoop() {
        DapErrors.logInfo("DAP server message loop started")
        
        while (running.get()) {
            try {
                // Read next message
                val json = withContext(Dispatchers.IO) {
                    messageReader.readMessage()
                } ?: break // EOF
                
                // Process message
                processMessage(json)
                
            } catch (e: CancellationException) {
                log.debug("Message loop cancelled")
                break
            } catch (e: Exception) {
                DapErrors.logTransportError("Error in message loop", e)
                // Continue processing other messages
            }
        }
        
        DapErrors.logInfo("DAP server message loop ended")
    }
    
    /**
     * Process a single DAP message.
     */
    private fun processMessage(json: JsonObject) {
        try {
            // Route the message
            val response = router.handleMessage(json)
            
            // Send response if present
            if (response != null) {
                sendResponse(response)
            }
            
            // Handle post-initialize event
            if (json.get("command")?.asString == "initialize" && response?.success == true) {
                // Send initialized event after initialize response
                eventEmitter.sendInitializedEvent()
            }
            
        } catch (e: DapException) {
            DapErrors.logProtocolError("DAP exception", e.message)
            // Error response already sent by router
        } catch (e: Exception) {
            DapErrors.logInternalError("Unexpected error processing message", e)
        }
    }
    
    /**
     * Send a response message.
     */
    private fun sendResponse(response: DapResponse) {
        try {
            val json = gson.toJsonTree(response).asJsonObject
            messageWriter.writeMessage(json)
        } catch (e: Exception) {
            DapErrors.logTransportError("Failed to send response", e)
        }
    }
    
    /**
     * Send an event message.
     */
    private fun sendEvent(event: DapEvent) {
        try {
            val json = gson.toJsonTree(event).asJsonObject
            messageWriter.writeMessage(json)
        } catch (e: Exception) {
            DapErrors.logTransportError("Failed to send event", e)
        }
    }
    
    /**
     * Register all request handlers.
     */
    private fun registerHandlers() {
        // Session lifecycle
        router.registerHandler("initialize", InitializeHandler(session))
        router.registerHandler("launch", LaunchHandler(session, backend))
        router.registerHandler("attach", AttachHandler(session, backend))
        router.registerHandler("configurationDone", ConfigurationDoneHandler(session))
        router.registerHandler("disconnect", DisconnectHandler(session, backend))
        router.registerHandler("terminate", TerminateHandler(session, backend))
        
        // Breakpoints
        router.registerHandler("setBreakpoints", SetBreakpointsHandler(session, backend))
        router.registerHandler("setFunctionBreakpoints", SetFunctionBreakpointsHandler(session, backend))
        router.registerHandler("setExceptionBreakpoints", SetExceptionBreakpointsHandler(session, backend))
        
        // Execution control
        router.registerHandler("continue", ContinueHandler(session, backend))
        router.registerHandler("next", NextHandler(session, backend))
        router.registerHandler("stepIn", StepInHandler(session, backend))
        router.registerHandler("stepOut", StepOutHandler(session, backend))
        router.registerHandler("pause", PauseHandler(session, backend))
        
        // Thread and stack
        router.registerHandler("threads", ThreadsHandler(session, backend))
        router.registerHandler("stackTrace", StackTraceHandler(session, backend))
        
        // Scopes and variables
        router.registerHandler("scopes", ScopesHandler(session, backend))
        router.registerHandler("variables", VariablesHandler(session, backend))
        router.registerHandler("evaluate", EvaluateHandler(session, backend))
        
        DapErrors.logInfo("Registered ${router.javaClass.declaredFields.size} DAP request handlers")
    }
    
    /**
     * Shutdown the server and clean up resources.
     */
    private fun shutdown() {
        try {
            // Disconnect backend if still connected
            if (!session.isTerminated()) {
                runBlocking {
                    backend.disconnect(false)
                }
            }
            
            // Reset session
            session.reset()
            
            DapErrors.logInfo("DAP server shutdown complete")
        } catch (e: Exception) {
            DapErrors.logInternalError("Error during shutdown", e)
        }
    }
    
    /**
     * Adapter to forward backend events to the event emitter.
     */
    private inner class BackendEventAdapter : DebuggerEventListener {
        
        override fun onStopped(
            reason: StoppedReason,
            threadId: Int?,
            description: String?,
            allThreadsStopped: Boolean?,
            hitBreakpointIds: List<Int>?
        ) {
            eventEmitter.sendStoppedEvent(
                reason = reason,
                threadId = threadId,
                description = description,
                allThreadsStopped = allThreadsStopped,
                hitBreakpointIds = hitBreakpointIds
            )
        }
        
        override fun onContinued(threadId: Int, allThreadsContinued: Boolean?) {
            eventEmitter.sendContinuedEvent(threadId, allThreadsContinued)
        }
        
        override fun onThread(reason: ThreadEventReason, threadId: Int) {
            eventEmitter.sendThreadEvent(reason, threadId)
        }
        
        override fun onOutput(
            category: OutputCategory?,
            output: String,
            source: Source?,
            line: Int?,
            column: Int?
        ) {
            eventEmitter.sendOutputEvent(
                category = category,
                output = output,
                source = source,
                line = line,
                column = column
            )
        }
        
        override fun onExited(exitCode: Int) {
            eventEmitter.sendExitedEvent(exitCode)
        }
        
        override fun onTerminated(restart: Boolean?) {
            eventEmitter.sendTerminatedEvent(restart)
        }
        
        override fun onBreakpoint(reason: BreakpointEventReason, breakpoint: Breakpoint) {
            eventEmitter.sendBreakpointEvent(reason, breakpoint)
        }
    }
}
