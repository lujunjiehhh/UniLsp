package com.frenchef.intellijlsp.dap.backend

import com.frenchef.intellijlsp.dap.model.*
import com.intellij.debugger.DebugEnvironment
import com.intellij.debugger.DebuggerGlobalSearchScope
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.RemoteDebugProcessHandler
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy
import com.intellij.debugger.engine.managerThread.DebuggerCommand
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.ThreadReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * IntelliJ Debugger Backend Implementation
 * 
 * Adapts IntelliJ's debugger API to the DebuggerBackend interface.
 * This is a stub implementation that will be connected to actual IntelliJ debugger APIs.
 */
class IntellijDebuggerBackend(private val project: Project) : DebuggerBackend {
    
    private val log = logger<IntellijDebuggerBackend>()
    
    private var eventListener: DebuggerEventListener? = null
    
    // ID generators
    private val breakpointIdGenerator = AtomicInteger(0)
    private val frameIdGenerator = AtomicInteger(0)
    private val variableRefGenerator = AtomicInteger(0)
    
    // Caches for mapping DAP IDs to IntelliJ objects
    private val breakpoints = ConcurrentHashMap<Int, BreakpointInfo>()
    private val frames = ConcurrentHashMap<Int, FrameInfo>()
    private val variableRefs = ConcurrentHashMap<Int, VariableRefInfo>()
    
    private val threadIdGenerator = AtomicInteger(0)
    private val threadIdByUniqueId = ConcurrentHashMap<Long, Int>()
    private val threadUniqueIdById = ConcurrentHashMap<Int, Long>()

    // Session state
    private var isLaunched = false
    private var isAttached = false

    private var debuggerSession: DebuggerSession? = null
    private var debugProcess: DebugProcess? = null

    @Volatile
    private var lastSuspendContext: SuspendContext? = null

    @Volatile
    private var pendingStopReason: StoppedReason? = null

    private val debugProcessListener = object : DebugProcessListener {
        override fun paused(suspendContext: SuspendContext) {
            lastSuspendContext = suspendContext
            val threadId = suspendContext.thread?.let { getOrCreateThreadId(it) }
            val reason = pendingStopReason ?: StoppedReason.BREAKPOINT
            pendingStopReason = null
            eventListener?.onStopped(
                reason = reason,
                threadId = threadId,
                description = null,
                allThreadsStopped = true,
                hitBreakpointIds = null
            )
        }

        override fun resumed(suspendContext: SuspendContext) {
            val threadId = suspendContext.thread?.let { getOrCreateThreadId(it) }
            lastSuspendContext = null
            if (threadId != null) {
                eventListener?.onContinued(threadId, true)
            }
        }

        override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
            lastSuspendContext = null
            eventListener?.onTerminated(false)
        }

        override fun threadStarted(proc: DebugProcess, thread: ThreadReference) {
            val threadId = getOrCreateThreadId(thread)
            eventListener?.onThread(ThreadEventReason.STARTED, threadId)
        }

        override fun threadStopped(proc: DebugProcess, thread: ThreadReference) {
            val threadId = getOrCreateThreadId(thread)
            eventListener?.onThread(ThreadEventReason.EXITED, threadId)
        }
    }
    
    // ========================================================================
    // Session Lifecycle
    // ========================================================================
    
    override suspend fun launch(args: LaunchRequestArguments): Boolean {
        log.info("Launching debug session: mainClass=${args.mainClass}, program=${args.program}")
        
        // TODO: Implement actual launch using IntelliJ's debugger API
        // This would involve:
        // 1. Creating a RunConfiguration
        // 2. Starting the debugger with that configuration
        // 3. Setting up event listeners
        
        isLaunched = true
        
        // Simulate process event
        eventListener?.onOutput(
            OutputCategory.CONSOLE,
            "Debug session started\n",
            null, null, null
        )
        
        return true
    }
    
    override suspend fun attach(args: AttachRequestArguments): Boolean {
        log.info("Attaching to process: host=${args.hostName}, port=${args.port}, pid=${args.processId}")

        val host = args.hostName ?: "127.0.0.1"
        val port = args.port ?: throw DapErrors.invalidArguments("Attach requires a port")

        val connection = RemoteConnection(true, host, port.toString(), false)
        val environment = DapDebugEnvironment(
            project = project,
            remoteConnection = connection,
            pollTimeout = args.timeout?.toLong() ?: 0L,
            sessionName = "DAP Attach ${host}:${port}",
            autoRestart = args.restart == true
        )

        val session = try {
            DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(environment)
        } catch (e: ExecutionException) {
            log.error("Failed to attach debugger session", e)
            throw DapErrors.attachError(e.message ?: "Attach failed")
        }

        if (session == null) {
            return false
        }

        debuggerSession = session
        debugProcess = session.process
        debugProcess?.addDebugProcessListener(debugProcessListener)
        isAttached = true

        return true
    }
    
    override suspend fun disconnect(terminateDebuggee: Boolean) {
        log.info("Disconnecting from debug session: terminateDebuggee=$terminateDebuggee")
        
        val process = debugProcess
        if (process != null) {
            if (terminateDebuggee) {
                process.stop(true)
            } else {
                process.stop(false)
            }
        }

        cleanup()
    }
    
    override suspend fun terminate() {
        log.info("Terminating debug session")
        
        val process = debugProcess
        if (process != null) {
            process.stop(true)
        }

        cleanup()
    }
    
    private fun cleanup() {
        isLaunched = false
        isAttached = false
        lastSuspendContext = null
        pendingStopReason = null
        breakpoints.clear()
        frames.clear()
        variableRefs.clear()
        threadIdByUniqueId.clear()
        threadUniqueIdById.clear()
        debugProcess?.removeDebugProcessListener(debugProcessListener)
        debugProcess = null
        debuggerSession = null
    }

    private fun getOrCreateThreadId(thread: ThreadReference): Int {
        val uniqueId = thread.uniqueID()
        return threadIdByUniqueId.computeIfAbsent(uniqueId) { _ ->
            val id = threadIdGenerator.incrementAndGet()
            threadUniqueIdById[id] = uniqueId
            id
        }
    }

    private fun getOrCreateThreadId(thread: ThreadReferenceProxy): Int {
        return getOrCreateThreadId(thread.threadReference)
    }

    private suspend fun <T> runInManagerThread(block: () -> T): T {
        val managerThread = debugProcess?.managerThread
            ?: throw DapErrors.internalError("Debugger manager thread not available")

        return suspendCancellableCoroutine { cont ->
            managerThread.invokeCommand(object : DebuggerCommand {
                override fun action() {
                    try {
                        cont.resume(block())
                    } catch (e: CancellationException) {
                        cont.resumeWithException(e)
                    } catch (e: Throwable) {
                        cont.resumeWithException(e)
                    }
                }

                override fun commandCancelled() {
                    cont.resumeWithException(CancellationException("Debugger command cancelled"))
                }
            })
        }
    }

    private class DapDebugEnvironment(
        private val project: Project,
        private val remoteConnection: RemoteConnection,
        private val pollTimeout: Long,
        private val sessionName: String,
        private val autoRestart: Boolean
    ) : DebugEnvironment {
        override fun createExecutionResult(): ExecutionResult? {
            val handler = RemoteDebugProcessHandler(project, autoRestart)
            return DefaultExecutionResult(null, handler)
        }

        override fun getSearchScope(): GlobalSearchScope {
            val scope = ExecutionSearchScopes.executionScope(project, null)
            return DebuggerGlobalSearchScope(scope, project)
        }

        override fun isRemote(): Boolean = true

        override fun getRemoteConnection(): RemoteConnection = remoteConnection

        override fun getPollTimeout(): Long = pollTimeout

        override fun getSessionName(): String = sessionName
    }
    
    // ========================================================================
    // Breakpoints
    // ========================================================================
    
    override suspend fun setBreakpoints(source: Source, breakpoints: List<SourceBreakpoint>): List<Breakpoint> {
        log.info("Setting ${breakpoints.size} breakpoints in ${source.path}")
        
        // Clear existing breakpoints for this source
        val sourcePath = source.path ?: return emptyList()
        this.breakpoints.entries.removeIf { it.value.sourcePath == sourcePath }
        
        // TODO: Implement actual breakpoint setting using IntelliJ's debugger API
        // This would involve:
        // 1. Using XBreakpointManager to create/update breakpoints
        // 2. Mapping line numbers (considering linesStartAt1)
        
        return breakpoints.map { bp ->
            val id = breakpointIdGenerator.incrementAndGet()
            val breakpointInfo = BreakpointInfo(
                id = id,
                sourcePath = sourcePath,
                line = bp.line,
                condition = bp.condition,
                hitCondition = bp.hitCondition,
                logMessage = bp.logMessage
            )
            this.breakpoints[id] = breakpointInfo
            
            Breakpoint(
                id = id,
                verified = true, // TODO: Check actual verification status
                source = source,
                line = bp.line,
                column = bp.column
            )
        }
    }
    
    override suspend fun setFunctionBreakpoints(breakpoints: List<FunctionBreakpoint>): List<Breakpoint> {
        log.info("Setting ${breakpoints.size} function breakpoints")
        
        // TODO: Implement function breakpoints using IntelliJ's debugger API
        
        return breakpoints.map { bp ->
            val id = breakpointIdGenerator.incrementAndGet()
            Breakpoint(
                id = id,
                verified = true,
                message = "Function breakpoint: ${bp.name}"
            )
        }
    }
    
    override suspend fun setExceptionBreakpoints(
        filters: List<String>,
        filterOptions: List<ExceptionFilterOptions>?
    ): List<Breakpoint>? {
        log.info("Setting exception breakpoints: filters=$filters")
        
        // TODO: Implement exception breakpoints using IntelliJ's debugger API
        // This would involve configuring exception breakpoints in XBreakpointManager
        
        return null
    }
    
    // ========================================================================
    // Execution Control
    // ========================================================================
    
    override suspend fun continueExecution(threadId: Int, singleThread: Boolean): Boolean {
        log.info("Continuing execution: threadId=$threadId, singleThread=$singleThread")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve calling resume() on the debug process
        
        // Clear frame and variable caches
        frames.clear()
        variableRefs.clear()
        
        eventListener?.onContinued(threadId, !singleThread)
        
        return !singleThread
    }
    
    override suspend fun next(threadId: Int, granularity: SteppingGranularity?) {
        log.info("Step next: threadId=$threadId, granularity=$granularity")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve calling stepOver() on the debug process
        
        // Clear frame and variable caches
        frames.clear()
        variableRefs.clear()
        
        // Simulate stopped event after step
        eventListener?.onStopped(
            reason = StoppedReason.STEP,
            threadId = threadId,
            description = "Step completed",
            allThreadsStopped = true,
            hitBreakpointIds = null
        )
    }
    
    override suspend fun stepIn(threadId: Int, targetId: Int?, granularity: SteppingGranularity?) {
        log.info("Step in: threadId=$threadId, targetId=$targetId, granularity=$granularity")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve calling stepInto() on the debug process
        
        frames.clear()
        variableRefs.clear()
        
        eventListener?.onStopped(
            reason = StoppedReason.STEP,
            threadId = threadId,
            description = "Step into completed",
            allThreadsStopped = true,
            hitBreakpointIds = null
        )
    }
    
    override suspend fun stepOut(threadId: Int, granularity: SteppingGranularity?) {
        log.info("Step out: threadId=$threadId, granularity=$granularity")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve calling stepOut() on the debug process
        
        frames.clear()
        variableRefs.clear()
        
        eventListener?.onStopped(
            reason = StoppedReason.STEP,
            threadId = threadId,
            description = "Step out completed",
            allThreadsStopped = true,
            hitBreakpointIds = null
        )
    }
    
    override suspend fun pause(threadId: Int) {
        log.info("Pausing: threadId=$threadId")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve calling pause() on the debug process
        
        eventListener?.onStopped(
            reason = StoppedReason.PAUSE,
            threadId = threadId,
            description = "Paused",
            allThreadsStopped = true,
            hitBreakpointIds = null
        )
    }
    
    // ========================================================================
    // Thread and Stack Information
    // ========================================================================
    
    override suspend fun getThreads(): List<Thread> {
        log.info("Getting threads")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve getting threads from the debug process
        
        // Return stub data
        return listOf(
            Thread(id = 1, name = "main")
        )
    }
    
    override suspend fun getStackTrace(
        threadId: Int,
        startFrame: Int?,
        levels: Int?,
        format: StackFrameFormat?
    ): Pair<List<StackFrame>, Int?> {
        log.info("Getting stack trace: threadId=$threadId, startFrame=$startFrame, levels=$levels")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve:
        // 1. Getting the suspended context for the thread
        // 2. Extracting stack frames
        // 3. Mapping to DAP StackFrame objects
        
        // Return stub data
        val frameId = frameIdGenerator.incrementAndGet()
        frames[frameId] = FrameInfo(
            id = frameId,
            threadId = threadId,
            index = 0
        )
        
        val stackFrames = listOf(
            StackFrame(
                id = frameId,
                name = "main",
                source = Source(
                    name = "Main.java",
                    path = "/path/to/Main.java"
                ),
                line = 10,
                column = 1
            )
        )
        
        return stackFrames to stackFrames.size
    }
    
    // ========================================================================
    // Scope and Variable Information
    // ========================================================================
    
    override suspend fun getScopes(frameId: Int): List<Scope> {
        log.info("Getting scopes for frame: $frameId")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve getting local variables and arguments from the frame
        
        val localsRef = variableRefGenerator.incrementAndGet()
        variableRefs[localsRef] = VariableRefInfo(
            id = localsRef,
            frameId = frameId,
            scopeType = ScopeType.LOCALS
        )
        
        val argsRef = variableRefGenerator.incrementAndGet()
        variableRefs[argsRef] = VariableRefInfo(
            id = argsRef,
            frameId = frameId,
            scopeType = ScopeType.ARGUMENTS
        )
        
        return listOf(
            Scope(
                name = "Locals",
                presentationHint = "locals",
                variablesReference = localsRef,
                expensive = false
            ),
            Scope(
                name = "Arguments",
                presentationHint = "arguments",
                variablesReference = argsRef,
                expensive = false
            )
        )
    }
    
    override suspend fun getVariables(
        variablesReference: Int,
        filter: VariablesFilter?,
        start: Int?,
        count: Int?,
        format: ValueFormat?
    ): List<Variable> {
        log.info("Getting variables: ref=$variablesReference, filter=$filter, start=$start, count=$count")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve:
        // 1. Looking up the variable reference
        // 2. Getting child variables if it's a structured type
        // 3. Formatting values according to format options
        
        // Return stub data
        return listOf(
            Variable(
                name = "x",
                value = "42",
                type = "int",
                variablesReference = 0
            ),
            Variable(
                name = "message",
                value = "\"Hello, World!\"",
                type = "String",
                variablesReference = 0
            )
        )
    }
    
    override suspend fun evaluate(
        expression: String,
        frameId: Int?,
        context: String?,
        format: ValueFormat?
    ): EvaluateResponseBody {
        log.info("Evaluating expression: '$expression' in frame $frameId, context=$context")
        
        // TODO: Implement using IntelliJ's debugger API
        // This would involve:
        // 1. Getting the evaluation context from the frame
        // 2. Evaluating the expression
        // 3. Formatting the result
        
        // Return stub data
        return EvaluateResponseBody(
            result = "evaluated: $expression",
            type = "String",
            variablesReference = 0
        )
    }
    
    // ========================================================================
    // Event Listener
    // ========================================================================
    
    override fun setEventListener(listener: DebuggerEventListener?) {
        this.eventListener = listener
    }
    
    // ========================================================================
    // Internal Data Classes
    // ========================================================================
    
    private data class BreakpointInfo(
        val id: Int,
        val sourcePath: String,
        val line: Int,
        val condition: String?,
        val hitCondition: String?,
        val logMessage: String?
    )
    
    private data class FrameInfo(
        val id: Int,
        val threadId: Int,
        val index: Int
    )
    
    private data class VariableRefInfo(
        val id: Int,
        val frameId: Int,
        val scopeType: ScopeType? = null,
        val parentRef: Int? = null
    )
    
    private enum class ScopeType {
        LOCALS, ARGUMENTS, REGISTERS
    }
}
