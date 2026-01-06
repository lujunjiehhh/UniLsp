package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.model.*
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger

/**
 * DAP Event Emitter
 * 
 * Responsible for sending DAP events to the client.
 * Events are asynchronous notifications that don't expect a response.
 * 
 * User Story: US1 - VSCode 基础调试会话
 * Task: T021 - Implement initialized event emission
 */
class DapEventEmitter(
    private val session: DapSession,
    private val messageSender: (DapEvent) -> Unit
) {
    
    private val log = logger<DapEventEmitter>()
    private val gson = DapGson.instance
    
    // ========================================================================
    // Session Events
    // ========================================================================
    
    /**
     * Send 'initialized' event.
     * 
     * This event indicates that the debug adapter is ready to accept
     * configuration requests (e.g. setBreakpoints, setExceptionBreakpoints).
     */
    fun sendInitializedEvent() {
        DapErrors.logInfo("Sending initialized event")
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "initialized",
            body = gson.toJsonTree(InitializedEventBody())
        )
        
        messageSender(event)
        
        // Transition session state
        session.onInitializedEventSent()
    }
    
    /**
     * Send 'terminated' event.
     * 
     * This event indicates that debugging has terminated.
     */
    fun sendTerminatedEvent(restart: Boolean? = null) {
        DapErrors.logInfo("Sending terminated event: restart=$restart")
        
        val body = TerminatedEventBody(
            restart = restart?.let { gson.toJsonTree(it) }
        )
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "terminated",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
    }
    
    /**
     * Send 'exited' event.
     * 
     * This event indicates that the debuggee has exited.
     */
    fun sendExitedEvent(exitCode: Int) {
        DapErrors.logInfo("Sending exited event: exitCode=$exitCode")
        
        val body = ExitedEventBody(exitCode = exitCode)
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "exited",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
    }
    
    // ========================================================================
    // Execution Events
    // ========================================================================
    
    /**
     * Send 'stopped' event.
     * 
     * This event indicates that the execution of the debuggee has stopped.
     */
    fun sendStoppedEvent(
        reason: StoppedReason,
        threadId: Int? = null,
        description: String? = null,
        text: String? = null,
        allThreadsStopped: Boolean? = null,
        preserveFocusHint: Boolean? = null,
        hitBreakpointIds: List<Int>? = null
    ) {
        DapErrors.logInfo("Sending stopped event: reason=$reason, threadId=$threadId")
        
        val body = StoppedEventBody(
            reason = reason,
            description = description,
            threadId = threadId,
            preserveFocusHint = preserveFocusHint,
            text = text,
            allThreadsStopped = allThreadsStopped,
            hitBreakpointIds = hitBreakpointIds
        )
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "stopped",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
        
        // Update session state
        if (threadId != null) {
            session.onStopped(threadId)
        }
    }
    
    /**
     * Send 'continued' event.
     * 
     * This event indicates that the execution of the debuggee has continued.
     */
    fun sendContinuedEvent(threadId: Int, allThreadsContinued: Boolean? = null) {
        DapErrors.logInfo("Sending continued event: threadId=$threadId, allThreadsContinued=$allThreadsContinued")
        
        val body = ContinuedEventBody(
            threadId = threadId,
            allThreadsContinued = allThreadsContinued
        )
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "continued",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
        
        // Update session state
        session.onContinued()
    }
    
    // ========================================================================
    // Thread Events
    // ========================================================================
    
    /**
     * Send 'thread' event.
     * 
     * This event indicates that a thread has started or exited.
     */
    fun sendThreadEvent(reason: ThreadEventReason, threadId: Int) {
        DapErrors.logDebug("Sending thread event: reason=$reason, threadId=$threadId")
        
        val body = ThreadEventBody(
            reason = reason,
            threadId = threadId
        )
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "thread",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
    }
    
    // ========================================================================
    // Output Events
    // ========================================================================
    
    /**
     * Send 'output' event.
     * 
     * This event indicates that the debuggee has produced output.
     */
    fun sendOutputEvent(
        category: OutputCategory? = null,
        output: String,
        group: OutputGroup? = null,
        variablesReference: Int? = null,
        source: Source? = null,
        line: Int? = null,
        column: Int? = null,
        data: JsonElement? = null
    ) {
        DapErrors.logDebug("Sending output event: category=$category, output=${output.take(50)}...")
        
        val body = OutputEventBody(
            category = category,
            output = output,
            group = group,
            variablesReference = variablesReference,
            source = source,
            line = line,
            column = column,
            data = data
        )
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "output",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
    }
    
    // ========================================================================
    // Breakpoint Events
    // ========================================================================
    
    /**
     * Send 'breakpoint' event.
     * 
     * This event indicates that some information about a breakpoint has changed.
     */
    fun sendBreakpointEvent(reason: BreakpointEventReason, breakpoint: Breakpoint) {
        DapErrors.logDebug("Sending breakpoint event: reason=$reason, breakpoint=${breakpoint.id}")
        
        val body = BreakpointEventBody(
            reason = reason,
            breakpoint = breakpoint
        )
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "breakpoint",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
    }
    
    // ========================================================================
    // Module Events
    // ========================================================================
    
    /**
     * Send 'module' event.
     * 
     * This event indicates that some information about a module has changed.
     */
    fun sendModuleEvent(reason: ModuleEventReason, module: Module) {
        DapErrors.logDebug("Sending module event: reason=$reason, module=${module.name}")
        
        val body = ModuleEventBody(
            reason = reason,
            module = module
        )
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "module",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
    }
    
    // ========================================================================
    // Capabilities Events
    // ========================================================================
    
    /**
     * Send 'capabilities' event.
     * 
     * This event indicates that one or more capabilities have changed.
     */
    fun sendCapabilitiesEvent(capabilities: Capabilities) {
        DapErrors.logDebug("Sending capabilities event")
        
        val body = CapabilitiesEventBody(capabilities = capabilities)
        
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "capabilities",
            body = gson.toJsonTree(body)
        )
        
        messageSender(event)
    }
}
