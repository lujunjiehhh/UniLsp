package com.frenchef.intellijlsp.dap.backend

import com.frenchef.intellijlsp.dap.model.*

/**
 * Debugger Backend Interface
 * 
 * Abstracts the IntelliJ Debugger API for DAP integration.
 * This interface defines the contract between DAP handlers and the actual debugger implementation.
 */
interface DebuggerBackend {
    /**
     * Release backend resources associated with a single DAP connection.
     *
     * This should NOT stop an existing IntelliJ debug session; it only detaches listeners,
     * clears caches, and frees resources to avoid leaks when the transport is closed.
     */
    fun dispose() {}
    
    // ========================================================================
    // Session Lifecycle
    // ========================================================================
    
    /**
     * Launch a debug session.
     * 
     * @param args Launch arguments
     * @return true if launch was successful
     */
    suspend fun launch(args: LaunchRequestArguments): Boolean
    
    /**
     * Attach to an existing process.
     * 
     * @param args Attach arguments
     * @return true if attach was successful
     */
    suspend fun attach(args: AttachRequestArguments): Boolean
    
    /**
     * Disconnect from the debug session.
     * 
     * @param terminateDebuggee Whether to terminate the debuggee process
     */
    suspend fun disconnect(terminateDebuggee: Boolean)
    
    /**
     * Terminate the debug session.
     */
    suspend fun terminate()

    /**
     * Restart the debug session, if supported.
     *
     * Implementations should restart using the last known launch/attach configuration when possible.
     */
    suspend fun restart()
    
    // ========================================================================
    // Breakpoints
    // ========================================================================
    
    /**
     * Set breakpoints for a source file.
     * 
     * @param source The source file
     * @param breakpoints The breakpoints to set
     * @return List of actual breakpoints (with verified status)
     */
    suspend fun setBreakpoints(source: Source, breakpoints: List<SourceBreakpoint>): List<Breakpoint>
    
    /**
     * Set function breakpoints.
     * 
     * @param breakpoints The function breakpoints to set
     * @return List of actual breakpoints (with verified status)
     */
    suspend fun setFunctionBreakpoints(breakpoints: List<FunctionBreakpoint>): List<Breakpoint>
    
    /**
     * Set exception breakpoints.
     * 
     * @param filters The exception filter IDs to enable
     * @param filterOptions Additional filter options
     * @return List of breakpoints (if applicable)
     */
    suspend fun setExceptionBreakpoints(
        filters: List<String>,
        filterOptions: List<ExceptionFilterOptions>?
    ): List<Breakpoint>?
    
    // ========================================================================
    // Execution Control
    // ========================================================================
    
    /**
     * Continue execution.
     * 
     * @param threadId The thread to continue
     * @param singleThread Whether to continue only the specified thread
     * @return true if all threads continued
     */
    suspend fun continueExecution(threadId: Int, singleThread: Boolean): Boolean
    
    /**
     * Step to next line (step over).
     * 
     * @param threadId The thread to step
     * @param granularity The stepping granularity
     */
    suspend fun next(threadId: Int, granularity: SteppingGranularity?)
    
    /**
     * Step into function call.
     * 
     * @param threadId The thread to step
     * @param targetId Optional target for step into
     * @param granularity The stepping granularity
     */
    suspend fun stepIn(threadId: Int, targetId: Int?, granularity: SteppingGranularity?)
    
    /**
     * Step out of current function.
     * 
     * @param threadId The thread to step
     * @param granularity The stepping granularity
     */
    suspend fun stepOut(threadId: Int, granularity: SteppingGranularity?)
    
    /**
     * Pause execution.
     * 
     * @param threadId The thread to pause
     */
    suspend fun pause(threadId: Int)
    
    // ========================================================================
    // Thread and Stack Information
    // ========================================================================
    
    /**
     * Get all threads.
     * 
     * @return List of threads
     */
    suspend fun getThreads(): List<Thread>
    
    /**
     * Get stack trace for a thread.
     * 
     * @param threadId The thread ID
     * @param startFrame Starting frame index
     * @param levels Number of frames to return (0 = all)
     * @param format Stack frame format options
     * @return Pair of (stack frames, total frame count)
     */
    suspend fun getStackTrace(
        threadId: Int,
        startFrame: Int?,
        levels: Int?,
        format: StackFrameFormat?
    ): Pair<List<StackFrame>, Int?>

    /**
     * Get exception details for a stopped thread.
     */
    suspend fun getExceptionInfo(threadId: Int): ExceptionInfoResponseBody

    // ========================================================================
    // Modules and Loaded Sources
    // ========================================================================

    /**
     * Get modules for the debug session (or project) for the 'modules' request.
     */
    suspend fun getModules(startModule: Int?, moduleCount: Int?): ModulesResponseBody

    /**
     * Get currently loaded sources for the 'loadedSources' request.
     */
    suspend fun getLoadedSources(): LoadedSourcesResponseBody
    
    // ========================================================================
    // Scope and Variable Information
    // ========================================================================
    
    /**
     * Get scopes for a stack frame.
     * 
     * @param frameId The frame ID
     * @return List of scopes
     */
    suspend fun getScopes(frameId: Int): List<Scope>
    
    /**
     * Get variables for a scope or structured variable.
     * 
     * @param variablesReference The variables reference
     * @param filter Filter for indexed or named variables
     * @param start Starting index for paging
     * @param count Number of variables to return
     * @param format Value format options
     * @return List of variables
     */
    suspend fun getVariables(
        variablesReference: Int,
        filter: VariablesFilter?,
        start: Int?,
        count: Int?,
        format: ValueFormat?
    ): List<Variable>

    /**
     * Set the value of a variable in the specified container.
     */
    suspend fun setVariable(
        variablesReference: Int,
        name: String,
        value: String,
        format: ValueFormat?
    ): SetVariableResponseBody
    
    /**
     * Evaluate an expression.
     * 
     * @param expression The expression to evaluate
     * @param frameId The frame context (optional)
     * @param context The evaluation context
     * @param format Value format options
     * @return Evaluation result
     */
    suspend fun evaluate(
        expression: String,
        frameId: Int?,
        context: String?,
        format: ValueFormat?
    ): EvaluateResponseBody

    // ========================================================================
    // Source
    // ========================================================================

    /**
     * Get the content of a source file.
     *
     * @param source The source descriptor (typically includes absolute path)
     * @param sourceReference A non-file reference (optional; not always used)
     */
    suspend fun getSource(source: Source?, sourceReference: Int?): SourceResponseBody
    
    // ========================================================================
    // Event Listener
    // ========================================================================
    
    /**
     * Set the event listener for debugger events.
     */
    fun setEventListener(listener: DebuggerEventListener?)

    /**
     * Set the active project for this backend.
     */
    fun setActiveProject(project: com.intellij.openapi.project.Project)
}

/**
 * Listener for debugger events.
 */
interface DebuggerEventListener {
    
    /**
     * Called when execution stops.
     */
    fun onStopped(
        reason: StoppedReason,
        threadId: Int?,
        description: String?,
        allThreadsStopped: Boolean?,
        hitBreakpointIds: List<Int>?
    )
    
    /**
     * Called when execution continues.
     */
    fun onContinued(threadId: Int, allThreadsContinued: Boolean?)
    
    /**
     * Called when a thread starts or exits.
     */
    fun onThread(reason: ThreadEventReason, threadId: Int)
    
    /**
     * Called when the debuggee produces output.
     */
    fun onOutput(
        category: OutputCategory?,
        output: String,
        source: Source?,
        line: Int?,
        column: Int?
    )
    
    /**
     * Called when the debuggee exits.
     */
    fun onExited(exitCode: Int)
    
    /**
     * Called when the debug session terminates.
     */
    fun onTerminated(restart: Boolean?)
    
    /**
     * Called when a breakpoint changes.
     */
    fun onBreakpoint(reason: BreakpointEventReason, breakpoint: Breakpoint)
}
