package com.frenchef.intellijlsp.dap.backend

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.model.*
import com.google.gson.JsonPrimitive
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.lang.Language
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import com.sun.jdi.event.ExceptionEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * IntelliJ Debugger Backend Implementation
 *
 * Adapts IntelliJ's debugger API to the DebuggerBackend interface.
 */
class IntellijDebuggerBackend(private var project: Project) : DebuggerBackend {

    private val log = logger<IntellijDebuggerBackend>()

    @Volatile
    private var eventListener: DebuggerEventListener? = null

    // ID generators
    private val breakpointIdGenerator = AtomicInteger(0)
    private val frameIdGenerator = AtomicInteger(0)
    private val variableRefGenerator = AtomicInteger(0)
    private val threadIdGenerator = AtomicInteger(0)

    // Caches for mapping DAP IDs to IntelliJ objects
    private val breakpoints = ConcurrentHashMap<Int, BreakpointInfo>()
    private val frames = ConcurrentHashMap<Int, FrameInfo>()
    private val variableRefs = ConcurrentHashMap<Int, VariableRefInfo>()
    private val threadIdsByStackName = ConcurrentHashMap<String, Int>()
    private val stacksByThreadId = ConcurrentHashMap<Int, XExecutionStack>()
    private val typeFqNameCache = ConcurrentHashMap<String, String?>()

    @Volatile
    private var debugSession: XDebugSession? = null

    @Volatile
    private var debugProcess: XDebugProcess? = null

    @Volatile
    private var lastRunConfigurationSettings: RunnerAndConfigurationSettings? = null

    // Session state
    private var isLaunched = false
    private var isAttached = false

    @Volatile
    private var lastStoppedThreadId: Int? = null

    @Volatile
    private var lastResumeAction: ResumeAction? = null

    private enum class ResumeAction {
        CONTINUE,
        STEP_OVER,
        STEP_IN,
        STEP_OUT,
        PAUSE
    }

    private val managerListener = object : XDebuggerManagerListener {
        override fun processStarted(debugProcess: XDebugProcess) {
            attachSession(debugProcess.session)
        }

        override fun processStopped(debugProcess: XDebugProcess) {
            if (this@IntellijDebuggerBackend.debugProcess === debugProcess) {
                detachSession()
            }
        }

        override fun currentSessionChanged(
            previousSession: XDebugSession?,
            currentSession: XDebugSession?
        ) {
            if (currentSession != null) {
                attachSession(currentSession)
            }
        }
    }

    private val sessionListener = object : XDebugSessionListener {
        override fun sessionPaused() {
            val session = debugSession ?: return
            val threadId = session.suspendContext?.activeExecutionStack?.let { getOrCreateThreadId(it) }
            lastStoppedThreadId = threadId

            val reason = when (lastResumeAction) {
                ResumeAction.STEP_OVER,
                ResumeAction.STEP_IN,
                ResumeAction.STEP_OUT -> StoppedReason.STEP
                ResumeAction.PAUSE -> StoppedReason.PAUSE
                else -> StoppedReason.BREAKPOINT
            }
            lastResumeAction = null

            eventListener?.onStopped(
                reason = reason,
                threadId = threadId,
                description = null,
                allThreadsStopped = true,
                hitBreakpointIds = null
            )
        }

        override fun sessionResumed() {
            frames.clear()
            variableRefs.clear()

            val threadId = lastStoppedThreadId
                ?: debugSession?.suspendContext?.activeExecutionStack?.let { getOrCreateThreadId(it) }
                ?: 1
            eventListener?.onContinued(threadId, true)
        }

        override fun sessionStopped() {
            eventListener?.onTerminated(false)
        }
    }

    private var projectConnection: com.intellij.util.messages.MessageBusConnection? = null

    private val processListener = object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            val category = when (outputType) {
                ProcessOutputTypes.STDOUT -> OutputCategory.STDOUT
                ProcessOutputTypes.STDERR -> OutputCategory.STDERR
                else -> OutputCategory.CONSOLE
            }
            eventListener?.onOutput(category, event.text, null, null, null)
        }

        override fun processTerminated(event: ProcessEvent) {
            eventListener?.onExited(event.exitCode)
        }
    }

    init {
        setupProjectConnection()
    }

    private fun setupProjectConnection() {
        projectConnection?.disconnect()
        projectConnection = project.messageBus.connect().apply {
            subscribe(XDebuggerManager.TOPIC, managerListener)
        }
        ApplicationManager.getApplication().invokeLater {
            XDebuggerManager.getInstance(project).currentSession?.let { attachSession(it) }
        }
    }

    override fun dispose() {
        try {
            projectConnection?.disconnect()
        } catch (e: Exception) {
            log.warn("Failed to disconnect project message bus connection", e)
        } finally {
            projectConnection = null
        }

        try {
            detachSession()
        } catch (e: Exception) {
            log.warn("Failed to detach debug session listeners", e)
        }

        try {
            cleanup()
        } catch (e: Exception) {
            log.warn("Failed to cleanup backend state", e)
        }

        eventListener = null
    }

    override fun setActiveProject(project: Project) {
        if (this.project === project) return
        
        log.info("Switching backend project from ${this.project.name} to ${project.name}")
        this.project = project
        
        // Clear caches
        breakpoints.clear()
        frames.clear()
        variableRefs.clear()
        threadIdsByStackName.clear()
        stacksByThreadId.clear()
        threadIdGenerator.set(0)
        lastStoppedThreadId = null
        
        // Reset session
        detachSession()
        
        // Re-setup connection
        setupProjectConnection()
    }

    // ========================================================================
    // Session Lifecycle
    // ========================================================================
    override suspend fun launch(args: LaunchRequestArguments): Boolean {
        log.info("Launching debug session: mainClass=${args.mainClass}, program=${args.program}")

        val settings = selectConfiguration(args.mainClass ?: args.program)
            ?: throw DapErrors.launchError(
                "No run configuration selected or matching '${args.mainClass ?: args.program}'"
            )
        lastRunConfigurationSettings = settings

        try {
            executeConfiguration(settings)
        } catch (e: Exception) {
            throw DapErrors.launchError(e.message ?: "Launch failed")
        }

        isLaunched = true
        eventListener?.onOutput(
            OutputCategory.CONSOLE,
            "Debug session launch requested\n",
            null,
            null,
            null
        )
        return true
    }

    override suspend fun attach(args: AttachRequestArguments): Boolean {
        log.info("Attaching to process: host=${args.hostName}, port=${args.port}, pid=${args.processId}")

        val hasExplicitTarget = args.hostName != null || args.port != null || args.processId != null
        if (!hasExplicitTarget) {
            val existing = debugSession ?: XDebuggerManager.getInstance(project).currentSession
            if (existing == null) {
                throw DapErrors.attachError("No active debug session to attach")
            }
            runOnEdt<Unit> { attachSession(existing) }
            isAttached = true
            eventListener?.onOutput(
                OutputCategory.CONSOLE,
                "Attached to existing debug session\n",
                null,
                null,
                null
            )
            return true
        }

        val settings = selectConfiguration(null)
            ?: throw DapErrors.attachError("No run configuration selected for attach")
        lastRunConfigurationSettings = settings

        try {
            executeConfiguration(settings)
        } catch (e: Exception) {
            throw DapErrors.attachError(e.message ?: "Attach failed")
        }

        isAttached = true
        eventListener?.onOutput(
            OutputCategory.CONSOLE,
            "Attach requested via selected run configuration\n",
            null,
            null,
            null
        )
        return true
    }

    override suspend fun disconnect(terminateDebuggee: Boolean) {
        log.info("Disconnecting from debug session: terminateDebuggee=$terminateDebuggee")
        val session = debugSession
        if (session != null) {
            // session.stop() 在 IntelliJ 2025.3+ 中不能在 EDT 上调用
            withContext(Dispatchers.Default) { session.stop() }
        }
        cleanup()
    }

    override suspend fun terminate() {
        log.info("Terminating debug session")
        val session = debugSession
        if (session != null) {
            // session.stop() 在 IntelliJ 2025.3+ 中不能在 EDT 上调用
            ApplicationManager.getApplication().executeOnPooledThread {
                session.stop()
            }
        }
        cleanup()
    }

    override suspend fun restart() {
        val settings = lastRunConfigurationSettings
            ?: throw DapErrors.internalError("restart is not supported (no run configuration associated with session)")

        log.info("Restarting debug session using configuration: ${settings.name}")

        val session = debugSession
        if (session != null) {
            // session.stop() 在 IntelliJ 2025.3+ 中不能在 EDT 上调用
            withContext(Dispatchers.Default) { session.stop() }
            // Detach immediately to avoid races with subsequent restarts/requests.
            detachSession()
        }

        // Keep existing breakpoints; a restart should not wipe IDE/DAP breakpoints.
        frames.clear()
        variableRefs.clear()

        try {
            executeConfiguration(settings)
        } catch (e: Exception) {
            throw DapErrors.internalError(e.message ?: "Restart failed")
        }
    }

    private fun cleanup() {
        isLaunched = false
        isAttached = false
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        breakpoints.values.forEach { info ->
            runCatching { breakpointManager.removeBreakpoint(info.breakpoint) }
        }
        breakpoints.clear()
        frames.clear()
        variableRefs.clear()
        threadIdsByStackName.clear()
        stacksByThreadId.clear()
        threadIdGenerator.set(0)
        lastStoppedThreadId = null
    }

    // ========================================================================
    // Breakpoints
    // ========================================================================

    override suspend fun setBreakpoints(source: Source, breakpoints: List<SourceBreakpoint>): List<Breakpoint> {
        log.info("Setting ${breakpoints.size} breakpoints in ${source.path}")

        val sourcePath = source.path ?: return emptyList()
        val file = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        if (file == null) {
            return breakpoints.map { bp ->
                Breakpoint(
                    id = null,
                    verified = false,
                    message = "File not found: $sourcePath",
                    source = source,
                    line = bp.line,
                    column = bp.column
                )
            }
        }

        clearBreakpointsForPath(sourcePath)

        val resolved = mutableListOf<Breakpoint>()
        for (bp in breakpoints) {
            val lineZeroBased = (bp.line - 1).coerceAtLeast(0)
            val lineBreakpointType = findLineBreakpointType(file, lineZeroBased)
            if (lineBreakpointType == null) {
                resolved.add(
                    Breakpoint(
                        id = null,
                        verified = false,
                        message = "Cannot set breakpoint at ${file.path}:${bp.line}",
                        source = source,
                        line = bp.line,
                        column = bp.column
                    )
                )
                continue
            }

            val xBreakpoint = try {
                createLineBreakpoint(
                    lineBreakpointType,
                    file,
                    lineZeroBased,
                    bp.condition,
                    bp.logMessage
                )
            } catch (e: Exception) {
                resolved.add(
                    Breakpoint(
                        id = null,
                        verified = false,
                        message = "Failed to create breakpoint at ${file.path}:${bp.line}: ${e.message ?: "unknown error"}",
                        source = source,
                        line = bp.line,
                        column = bp.column
                    )
                )
                continue
            }

            val id = breakpointIdGenerator.incrementAndGet()
            this.breakpoints[id] = BreakpointInfo(
                id = id,
                kind = BreakpointKind.LINE,
                sourcePath = sourcePath,
                line = bp.line,
                breakpoint = xBreakpoint
            )
            resolved.add(
                Breakpoint(
                    id = id,
                    verified = true,
                    source = source,
                    line = bp.line,
                    column = bp.column
                )
            )
        }

        return resolved
    }

    override suspend fun setFunctionBreakpoints(breakpoints: List<FunctionBreakpoint>): List<Breakpoint> {
        log.info("Setting ${breakpoints.size} function breakpoints")
        clearBreakpointsByKind(BreakpointKind.FUNCTION)

        val resolved = mutableListOf<Breakpoint>()
        for (bp in breakpoints) {
            val location = resolveFunctionLocation(bp.name)
            if (location == null) {
                resolved.add(
                    Breakpoint(
                        id = null,
                        verified = false,
                        message = "Function not found: ${bp.name}"
                    )
                )
                continue
            }

            val type = findFunctionBreakpointType(location.file) ?: findLineBreakpointType(location.file, location.line)
            val canPut = if (type != null) {
                ApplicationManager.getApplication().runReadAction(
                    Computable { type.canPutAt(location.file, location.line, project) }
                )
            } else {
                false
            }
            if (type == null || !canPut) {
                resolved.add(
                    Breakpoint(
                        id = null,
                        verified = false,
                        message = "Cannot set function breakpoint at ${location.file.path}:${location.line + 1}"
                    )
                )
                continue
            }

            val xBreakpoint = createMethodBreakpoint(
                type,
                location.file,
                location.line,
                location.className,
                location.methodName,
                bp.condition
            )
            val id = breakpointIdGenerator.incrementAndGet()
            this.breakpoints[id] = BreakpointInfo(
                id = id,
                kind = BreakpointKind.FUNCTION,
                sourcePath = location.file.path,
                line = location.line + 1,
                breakpoint = xBreakpoint
            )
            resolved.add(
                Breakpoint(
                    id = id,
                    verified = true,
                    message = bp.hitCondition?.let { "hitCondition is not supported" }
                )
            )
        }

        return resolved
    }

    override suspend fun setExceptionBreakpoints(
        filters: List<String>,
        filterOptions: List<ExceptionFilterOptions>?
    ): List<Breakpoint>? {
        log.info("Setting exception breakpoints: filters=$filters")
        clearBreakpointsByKind(BreakpointKind.EXCEPTION)

        val exceptionType = findExceptionBreakpointType()
        if (exceptionType == null) {
            return filters.map { filter ->
                Breakpoint(
                    id = null,
                    verified = false,
                    message = "No exception breakpoint type available: $filter"
                )
            }
        }

        val knownFilters = setOf("all", "any", "caught", "uncaught", "unhandled", "userunhandled")
        val classTargets = mutableListOf<String>()
        filters.filter { it.lowercase() !in knownFilters }.forEach { classTargets.add(it) }
        filterOptions?.mapNotNull { it.filterId }
            ?.filter { it.lowercase() !in knownFilters }
            ?.forEach { classTargets.add(it) }

        val targets = if (classTargets.isEmpty()) listOf<String?>(null) else classTargets.distinct()
        val results = mutableListOf<Breakpoint>()
        for (target in targets) {
            val properties = exceptionType.createProperties()
            if (properties != null) {
                configureExceptionProperties(properties, filters, target)
            }

            val xBreakpoint = runOnEdt {
                @Suppress("UNCHECKED_CAST")
                addBreakpointUnchecked(
                    exceptionType,
                    properties as? XBreakpointProperties<*>
                )
            }

            val id = breakpointIdGenerator.incrementAndGet()
            this.breakpoints[id] = BreakpointInfo(
                id = id,
                kind = BreakpointKind.EXCEPTION,
                sourcePath = null,
                line = null,
                breakpoint = xBreakpoint
            )
            results.add(
                Breakpoint(
                    id = id,
                    verified = true,
                    message = target?.let { "Exception breakpoint for $it" }
                )
            )
        }

        return results
    }

    // ========================================================================
    // Execution Control
    // ========================================================================
    override suspend fun continueExecution(threadId: Int, singleThread: Boolean): Boolean {
        log.info("Continuing execution: threadId=$threadId, singleThread=$singleThread")
        val session = requireSession()
        lastResumeAction = ResumeAction.CONTINUE
        frames.clear()
        variableRefs.clear()
        runOnEdt { session.resume() }
        return !singleThread
    }

    override suspend fun next(threadId: Int, granularity: SteppingGranularity?) {
        log.info("Step next: threadId=$threadId, granularity=$granularity")
        val session = requireSession()
        lastResumeAction = ResumeAction.STEP_OVER
        frames.clear()
        variableRefs.clear()
        runOnEdt { session.stepOver(false) }
    }

    override suspend fun stepIn(threadId: Int, targetId: Int?, granularity: SteppingGranularity?) {
        log.info("Step in: threadId=$threadId, targetId=$targetId, granularity=$granularity")
        val session = requireSession()
        lastResumeAction = ResumeAction.STEP_IN
        frames.clear()
        variableRefs.clear()
        runOnEdt { session.stepInto() }
    }

    override suspend fun stepOut(threadId: Int, granularity: SteppingGranularity?) {
        log.info("Step out: threadId=$threadId, granularity=$granularity")
        val session = requireSession()
        lastResumeAction = ResumeAction.STEP_OUT
        frames.clear()
        variableRefs.clear()
        runOnEdt { session.stepOut() }
    }

    override suspend fun pause(threadId: Int) {
        log.info("Pausing: threadId=$threadId")
        val session = requireSession()
        lastResumeAction = ResumeAction.PAUSE
        runOnEdt { session.pause() }
    }

    // ========================================================================
    // Thread and Stack Information
    // ========================================================================

    override suspend fun getThreads(): List<Thread> {
        log.info("Getting threads")
        val session = requireSession()

        // Prefer the Java debugger "running stacks" API when available, as it tends to return all JVM threads
        // (not only the active/suspended one) and works for both RUNNING and STOPPED states.
        val runningStacks = runCatching { collectRunningStacks(session.debugProcess) }
            .onFailure { error -> log.debug("collectRunningStacks failed: ${error.message}", error) }
            .getOrDefault(emptyList())

        val stacks = if (runningStacks.isNotEmpty()) {
            runningStacks
        } else if (session.isSuspended) {
            val suspendContext = session.suspendContext
            if (suspendContext != null) {
                collectExecutionStacks(suspendContext)
            } else {
                // suspendContext 为 null 但 session 仍然标记为 suspended.
                log.info("Session is suspended but suspendContext is null; returning empty thread list")
                emptyList()
            }
        } else {
            emptyList()
        }

        log.info("Got ${stacks.size} execution stacks")

        val threads = stacks.map { stack ->
            val id = getOrCreateThreadId(stack)
            Thread(id = id, name = formatThreadName(stack.displayName))
        }
        if (threads.isNotEmpty()) {
            return threads
        }

        // Fall back to last known thread list if the debug process can't provide running stacks.
        return stacksByThreadId.entries
            .sortedBy { it.key }
            .map { (id, stack) -> Thread(id = id, name = formatThreadName(stack.displayName)) }
    }

    override suspend fun getStackTrace(
        threadId: Int,
        startFrame: Int?,
        levels: Int?,
        format: StackFrameFormat?
    ): Pair<List<StackFrame>, Int?> {
        log.info("Getting stack trace: threadId=$threadId, startFrame=$startFrame, levels=$levels")
        val stack = stacksByThreadId[threadId] ?: throw DapErrors.threadNotFound(threadId)
        val start = startFrame ?: 0
        val framesList = collectStackFrames(stack, start, levels)

        val dapFrames = framesList.mapIndexed { index, frame ->
            val id = frameIdGenerator.incrementAndGet()
            frames[id] = FrameInfo(
                id = id,
                threadId = threadId,
                index = start + index,
                frame = frame,
                executionStack = stack
            )
            toDapStackFrame(frame, id)
        }

        val total = if (levels == null) dapFrames.size else null
        return dapFrames to total
    }

    override suspend fun getExceptionInfo(threadId: Int): ExceptionInfoResponseBody {
        log.info("Getting exception info: threadId=$threadId")
        stacksByThreadId[threadId] ?: throw DapErrors.threadNotFound(threadId)

        val session = requireSession()
        val javaDebugProcess = session.debugProcess as? JavaDebugProcess
            ?: throw DapErrors.internalError("exceptionInfo is only supported for Java debug sessions")

        val debugProcess = javaDebugProcess.debuggerSession.process
        val debuggerContext = debugProcess.debuggerContext
        val suspendContext = debuggerContext.suspendContext
            ?: throw DapErrors.internalError("No suspend context available for exceptionInfo")
        val managerThread = debuggerContext.managerThread
            ?: throw DapErrors.internalError("Debugger manager thread is not available")

        val deferred = CompletableDeferred<ExceptionInfoResponseBody>()
        managerThread.schedule(object : DebuggerCommandImpl() {
            override fun action() {
                try {
                    val exception = DebuggerUtilsEx.getEventDescriptors(suspendContext)
                        .asSequence()
                        .mapNotNull { it.second as? ExceptionEvent }
                        .mapNotNull { it.exception() as? ObjectReference }
                        .firstOrNull()

                    if (exception == null) {
                        deferred.complete(
                            ExceptionInfoResponseBody(
                                exceptionId = "unknown",
                                description = "No exception event is available in current suspend context",
                                breakMode = ExceptionBreakMode.ALWAYS
                            )
                        )
                        return
                    }

                    val exceptionId = exception.referenceType().name()
                    val message = runCatching { extractThrowableMessage(exception) }.getOrNull()
                    val description = if (!message.isNullOrBlank()) "$exceptionId: $message" else exceptionId

                    deferred.complete(
                        ExceptionInfoResponseBody(
                            exceptionId = exceptionId,
                            description = description,
                            breakMode = ExceptionBreakMode.ALWAYS
                        )
                    )
                } catch (e: Exception) {
                    deferred.completeExceptionally(e)
                }
            }

            override fun commandCancelled() {
                deferred.completeExceptionally(DapErrors.internalError("Failed to compute exception info"))
            }
        })

        return awaitWithTimeout("exception info", deferred)
    }

    private fun extractThrowableMessage(exception: ObjectReference): String? {
        val messageField = exception.referenceType().allFields().firstOrNull { it.name() == "detailMessage" } ?: return null
        val message = exception.getValue(messageField) as? StringReference ?: return null
        return message.value()
    }

    override suspend fun getModules(startModule: Int?, moduleCount: Int?): ModulesResponseBody {
        val modules = ApplicationManager.getApplication().runReadAction(Computable {
            val items = mutableListOf<Module>()
            val seenIds = linkedSetOf<String>()

            fun addModule(id: String, name: String, path: String?, isUserCode: Boolean?) {
                if (!seenIds.add(id)) {
                    return
                }
                items.add(
                    Module(
                        id = JsonPrimitive(id),
                        name = name,
                        path = path,
                        isUserCode = isUserCode
                    )
                )
            }

            val projectBasePath = project.basePath?.replace('\\', '/')?.trimEnd('/')

            // IntelliJ project modules
            ModuleManager.getInstance(project).modules.forEach { module ->
                addModule(
                    id = "module:${module.name}",
                    name = module.name,
                    path = module.moduleFilePath,
                    isUserCode = true
                )
            }

            // Classpath roots (JARs / output dirs / dependency roots)
            val classpathRoots = linkedSetOf<String>()
            ModuleManager.getInstance(project).modules.forEach { module ->
                OrderEnumerator.orderEntries(module)
                    .withoutSdk()
                    .withoutModuleSourceEntries()
                    .recursively()
                    .classesRoots
                    .forEach { root ->
                        val localPath = JarFileSystem.getInstance().getVirtualFileForJar(root)?.path ?: root.path
                        val normalized = localPath.replace('\\', '/')
                        // Filter out non-local file system paths (e.g., jrt://)
                        if (normalized.startsWith("/") || normalized.matches(Regex("^[A-Za-z]:/.*"))) {
                            classpathRoots.add(localPath)
                        }
                    }
            }

            classpathRoots.forEach { path ->
                val normalized = path.replace('\\', '/')
                val isUser = projectBasePath != null && normalized.startsWith("$projectBasePath/")
                val name = File(path).name.ifBlank { path }
                addModule(
                    id = "classpath:$normalized",
                    name = name,
                    path = path,
                    isUserCode = isUser
                )
            }

            items
        })

        val start = startModule ?: 0
        val count = moduleCount ?: Int.MAX_VALUE
        val sliced = if (start >= modules.size || count <= 0) {
            emptyList()
        } else {
            modules.drop(start).take(count)
        }

        return ModulesResponseBody(modules = sliced, totalModules = modules.size)
    }

    override suspend fun getLoadedSources(): LoadedSourcesResponseBody {
        val paths = linkedSetOf<String>()
        breakpoints.values.mapNotNullTo(paths) { it.sourcePath?.takeIf(String::isNotBlank) }
        val framePaths = ApplicationManager.getApplication().runReadAction(Computable {
            frames.values.mapNotNull { it.frame.sourcePosition?.file?.path?.takeIf(String::isNotBlank) }
        })
        paths.addAll(framePaths)

        val sources = paths
            .sorted()
            .map { path ->
                Source(
                    name = File(path).name,
                    path = path
                )
            }

        return LoadedSourcesResponseBody(sources = sources)
    }

    // ========================================================================
    // Scope and Variable Information
    // ========================================================================

    override suspend fun getScopes(frameId: Int): List<Scope> {
        log.info("Getting scopes for frame: $frameId")
        val frameInfo = frames[frameId] ?: throw DapErrors.frameNotFound(frameId)
        val localsRef = variableRefGenerator.incrementAndGet()
        variableRefs[localsRef] = VariableRefInfo(id = localsRef, container = frameInfo.frame)

        return listOf(
            Scope(
                name = "Locals",
                presentationHint = "locals",
                variablesReference = localsRef,
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
        val refInfo = variableRefs[variablesReference]
            ?: throw DapErrors.variableNotFound(variablesReference)

        val candidates = collectVariableCandidates(refInfo.container)
        val filtered = applyVariableFilter(candidates, filter)
        val sliced = sliceVariables(filtered, start, count)

        return sliced.map { candidate ->
            when (candidate) {
                is VariableCandidate.Group -> {
                    val groupRef = variableRefGenerator.incrementAndGet()
                    variableRefs[groupRef] = VariableRefInfo(id = groupRef, container = candidate.group)
                    Variable(
                        name = candidate.group.name,
                        value = candidate.group.comment ?: "",
                        type = null,
                        variablesReference = groupRef
                    )
                }
                is VariableCandidate.Value -> {
                    val presentation = presentValue(candidate.value)
                    val childRef = if (presentation.hasChildren) {
                        val refId = variableRefGenerator.incrementAndGet()
                        variableRefs[refId] = VariableRefInfo(id = refId, container = candidate.value)
                        refId
                    } else {
                        0
                    }
                    Variable(
                        name = candidate.name,
                        value = presentation.value,
                        type = presentation.type,
                        variablesReference = childRef
                    )
                }
            }
        }
    }

    override suspend fun setVariable(
        variablesReference: Int,
        name: String,
        value: String,
        format: ValueFormat?
    ): SetVariableResponseBody {
        log.info("Setting variable: ref=$variablesReference, name=$name")
        val refInfo = variableRefs[variablesReference]
            ?: throw DapErrors.variableNotFound(variablesReference)

        val candidates = collectVariableCandidates(refInfo.container)
        val target = candidates.asSequence()
            .filterIsInstance<VariableCandidate.Value>()
            .firstOrNull { it.name == name }
            ?: throw DapErrors.invalidArguments("Variable '$name' not found for reference $variablesReference")

        val modifier = target.value.modifier
            ?: throw DapErrors.internalError("Variable '$name' is not settable")

        val deferred = CompletableDeferred<Unit>()
        val expression = SimpleXExpression(value)
        runOnEdt {
            modifier.setValue(expression, object : XValueModifier.XModificationCallback {
                override fun valueModified() {
                    deferred.complete(Unit)
                }

                override fun errorOccurred(errorMessage: String) {
                    deferred.completeExceptionally(DapErrors.invalidArguments(errorMessage))
                }
            })
        }
        awaitWithTimeout("set variable", deferred)

        val presentation = presentValue(target.value)
        val newRef = if (presentation.hasChildren) {
            val refId = variableRefGenerator.incrementAndGet()
            variableRefs[refId] = VariableRefInfo(id = refId, container = target.value, parentRef = variablesReference)
            refId
        } else {
            0
        }

        return SetVariableResponseBody(
            value = presentation.value,
            type = presentation.type,
            variablesReference = newRef.takeIf { it != 0 }
        )
    }

    override suspend fun evaluate(
        expression: String,
        frameId: Int?,
        context: String?,
        format: ValueFormat?
    ): EvaluateResponseBody {
        log.info("Evaluating expression: '$expression' in frame $frameId, context=$context")

        val session = requireSession()
        val frame = frameId?.let { frames[it]?.frame } ?: session.currentStackFrame
        val evaluator = frame?.evaluator ?: session.debugProcess.evaluator
        if (evaluator == null) {
            throw DapErrors.evaluateError(expression, "No evaluator available")
        }

        val evaluatedValue = evaluateExpression(evaluator, expression, frame?.sourcePosition)
        val presentation = presentValue(evaluatedValue)
        val variablesReference = if (presentation.hasChildren) {
            val refId = variableRefGenerator.incrementAndGet()
            variableRefs[refId] = VariableRefInfo(id = refId, container = evaluatedValue)
            refId
        } else {
            0
        }

        return EvaluateResponseBody(
            result = presentation.value,
            type = presentation.type,
            variablesReference = variablesReference
        )
    }

    override suspend fun getSource(source: Source?, sourceReference: Int?): SourceResponseBody {
        val path = source?.path?.takeIf { it.isNotBlank() }
            ?: throw DapErrors.invalidArguments("source.path is required (sourceReference not supported)")

        val file = LocalFileSystem.getInstance().findFileByPath(path)
            ?: throw DapErrors.invalidArguments("File not found: $path")

        val content = ApplicationManager.getApplication().runReadAction(Computable {
            val document = FileDocumentManager.getInstance().getDocument(file)
            document?.text
        }) ?: withContext(Dispatchers.IO) {
            file.inputStream.use { input ->
                input.readBytes().toString(file.charset)
            }
        }

        return SourceResponseBody(
            content = content,
            mimeType = "text/plain"
        )
    }

    // ========================================================================
    // Event Listener
    // ========================================================================

    override fun setEventListener(listener: DebuggerEventListener?) {
        this.eventListener = listener
    }

    // ========================================================================
    // Helpers
    // ========================================================================
    private fun selectConfiguration(name: String?): com.intellij.execution.RunnerAndConfigurationSettings? {
        val runManager = RunManager.getInstance(project)
        if (name != null) {
            runManager.allSettings.firstOrNull { it.name == name }?.let { return it }
        }
        return runManager.selectedConfiguration ?: runManager.allSettings.firstOrNull()
    }

    private suspend fun executeConfiguration(settings: RunnerAndConfigurationSettings) {
        val executor = DefaultDebugExecutor.getDebugExecutorInstance()
        runOnEdt {
            val environment = ExecutionEnvironmentBuilder(project, executor)
                .runProfile(settings.configuration)
                .build()
            val runner = ProgramRunner.getRunner(executor.id, settings.configuration)
                ?: throw IllegalStateException("No runner for executor ${executor.id}")
            runner.execute(environment)
        }
    }

    private fun guessRunConfigurationSettings(session: XDebugSession): RunnerAndConfigurationSettings? {
        return runCatching {
            val environment = session.javaClass.methods.firstOrNull { method ->
                method.name == "getExecutionEnvironment" && method.parameterTypes.isEmpty()
            }?.invoke(session)
            val settingsFromEnvironment = environment?.javaClass?.methods?.firstOrNull { method ->
                method.name == "getRunnerAndConfigurationSettings" && method.parameterTypes.isEmpty()
            }?.invoke(environment)
            if (settingsFromEnvironment is RunnerAndConfigurationSettings) {
                return settingsFromEnvironment
            }

            val runProfile = session.javaClass.methods.firstOrNull { method ->
                method.name == "getRunProfile" && method.parameterTypes.isEmpty()
            }?.invoke(session)
            if (runProfile is com.intellij.execution.configurations.RunProfile) {
                val runManager = RunManager.getInstance(project)
                return runManager.allSettings.firstOrNull { it.configuration === runProfile }
                    ?: runManager.allSettings.firstOrNull { it.name == runProfile.name }
            }

            null
        }.getOrNull()
    }

    private fun attachSession(session: XDebugSession) {
        if (debugSession === session) {
            return
        }
        debugSession?.removeSessionListener(sessionListener)
        debugSession = session
        debugProcess = session.debugProcess
        guessRunConfigurationSettings(session)?.let { lastRunConfigurationSettings = it }
        session.addSessionListener(sessionListener, project)
        debugProcess?.processHandler?.addProcessListener(processListener)
        frames.clear()
        variableRefs.clear()
        threadIdsByStackName.clear()
        stacksByThreadId.clear()
        threadIdGenerator.set(0)
        lastStoppedThreadId = null
    }

    private fun detachSession() {
        debugSession?.removeSessionListener(sessionListener)
        debugProcess?.processHandler?.removeProcessListener(processListener)
        debugSession = null
        debugProcess = null
        frames.clear()
        variableRefs.clear()
        threadIdsByStackName.clear()
        stacksByThreadId.clear()
        threadIdGenerator.set(0)
        lastStoppedThreadId = null
    }

    private fun requireSession(): XDebugSession {
        return debugSession ?: throw DapErrors.internalError("No active debug session")
    }

    private fun clearBreakpointsForPath(sourcePath: String) {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        val toRemove = breakpoints.filterValues {
            it.kind == BreakpointKind.LINE && it.sourcePath == sourcePath
        }
        toRemove.forEach { (_, info) ->
            runCatching { breakpointManager.removeBreakpoint(info.breakpoint) }
        }
        toRemove.keys.forEach { breakpoints.remove(it) }
    }

    private fun clearBreakpointsByKind(kind: BreakpointKind) {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        val toRemove = breakpoints.filterValues { it.kind == kind }
        toRemove.forEach { (_, info) ->
            runCatching { breakpointManager.removeBreakpoint(info.breakpoint) }
        }
        toRemove.keys.forEach { breakpoints.remove(it) }
    }

    private fun findLineBreakpointType(file: VirtualFile, lineZeroBased: Int): XLineBreakpointType<*>? {
        return ApplicationManager.getApplication().runReadAction(
            Computable {
                XDebuggerUtil.getInstance()
                    .lineBreakpointTypes
                    .firstOrNull { it.canPutAt(file, lineZeroBased, project) }
            }
        )
    }

    private fun findFunctionBreakpointType(file: VirtualFile): XLineBreakpointType<*>? {
        val types = XBreakpointType.EXTENSION_POINT_NAME.extensionList
        val kotlinType = types.firstOrNull { it.id == "kotlin-function" } as? XLineBreakpointType<*>
        val javaType = types.firstOrNull { it.id == "java-method" } as? XLineBreakpointType<*>
        val extension = file.extension?.lowercase()
        return if (extension == "kt" || extension == "kts") {
            kotlinType ?: javaType
        } else {
            javaType ?: kotlinType
        }
    }

    private suspend fun createLineBreakpoint(
        lineBreakpointType: XLineBreakpointType<*>,
        file: VirtualFile,
        lineZeroBased: Int,
        condition: String?,
        logMessage: String?
    ): XLineBreakpoint<*> {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        return runOnEdt<XLineBreakpoint<*>> {
            ApplicationManager.getApplication().runWriteAction(
                Computable {
                    @Suppress("UNCHECKED_CAST")
                    val type = lineBreakpointType as XLineBreakpointType<XBreakpointProperties<*>>
                    val properties = type.createBreakpointProperties(file, lineZeroBased)
                    val created = breakpointManager.addLineBreakpoint(type, file.url, lineZeroBased, properties)
                    condition?.let { created.setCondition(it) }
                    logMessage?.let {
                        created.setLogMessage(true)
                        created.setLogExpression(it)
                    }
                    created
                }
            )
        }
    }

    private suspend fun createMethodBreakpoint(
        lineBreakpointType: XLineBreakpointType<*>,
        file: VirtualFile,
        lineZeroBased: Int,
        className: String?,
        methodName: String?,
        condition: String?
    ): XLineBreakpoint<*> {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        return runOnEdt<XLineBreakpoint<*>> {
            ApplicationManager.getApplication().runWriteAction(
                Computable {
                    @Suppress("UNCHECKED_CAST")
                    val type = lineBreakpointType as XLineBreakpointType<XBreakpointProperties<*>>
                    val properties = type.createBreakpointProperties(file, lineZeroBased)
                    applyMethodProperties(properties, className, methodName)
                    val created = breakpointManager.addLineBreakpoint(type, file.url, lineZeroBased, properties)
                    condition?.let { created.setCondition(it) }
                    created
                }
            )
        }
    }

    private fun resolveFunctionLocation(name: String): FunctionLocation? {
        val parsed = parseFunctionName(name)
        val methodName = parsed.methodName
        if (methodName.isBlank()) {
            return null
        }

        return ApplicationManager.getApplication().runReadAction(
            Computable {
                val scope = GlobalSearchScope.projectScope(project)
                val methods = PsiShortNamesCache.getInstance(project).getMethodsByName(methodName, scope)
                val method = if (parsed.classHint != null) {
                    methods.firstOrNull { matchesClassHint(it, parsed.classHint) }
                } else {
                    methods.firstOrNull()
                } ?: return@Computable null

                val psiFile = method.containingFile ?: return@Computable null
                val vFile = psiFile.virtualFile ?: return@Computable null
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return@Computable null
                val line = document.getLineNumber(method.textOffset).coerceAtLeast(0)
                val className = method.containingClass?.qualifiedName ?: method.containingClass?.name
                FunctionLocation(vFile, line, className, method.name)
            }
        )
    }

    private data class FunctionLocation(
        val file: VirtualFile,
        val line: Int,
        val className: String?,
        val methodName: String?
    )

    private data class ParsedFunctionName(val classHint: String?, val methodName: String)

    private fun parseFunctionName(name: String): ParsedFunctionName {
        val trimmed = name.trim().removeSuffix("()")
        val sanitized = trimmed.substringBefore('(')
        val separators = listOf("::", "#", ".")
        var bestIndex = -1
        var bestSep = ""
        for (sep in separators) {
            val idx = sanitized.lastIndexOf(sep)
            if (idx > bestIndex) {
                bestIndex = idx
                bestSep = sep
            }
        }
        return if (bestIndex >= 0) {
            val classHint = sanitized.substring(0, bestIndex).trim().ifBlank { null }
            val methodName = sanitized.substring(bestIndex + bestSep.length).trim()
            ParsedFunctionName(classHint, methodName)
        } else {
            ParsedFunctionName(null, sanitized)
        }
    }

    private fun matchesClassHint(method: PsiMethod, classHint: String?): Boolean {
        if (classHint.isNullOrBlank()) {
            return true
        }
        val containingClass = method.containingClass
        val qualifiedName = containingClass?.qualifiedName
        val simpleName = containingClass?.name
        if (qualifiedName != null) {
            if (qualifiedName == classHint || qualifiedName.endsWith(".${classHint}")) {
                return true
            }
        }
        if (simpleName != null) {
            if (simpleName == classHint || simpleName.removeSuffix("Kt") == classHint) {
                return true
            }
        }

        val fileName = method.containingFile?.virtualFile?.nameWithoutExtension
        if (fileName != null) {
            if (classHint.endsWith(fileName) || classHint.endsWith("${fileName}Kt")) {
                return true
            }
        }

        return false
    }

    private fun applyMethodProperties(properties: Any?, className: String?, methodName: String?) {
        if (properties == null) {
            return
        }
        if (!className.isNullOrBlank()) {
            setPropertyIfPresent(properties, listOf("myClassPattern", "classPattern", "className"), className)
        }
        if (!methodName.isNullOrBlank()) {
            setPropertyIfPresent(properties, listOf("myMethodName", "methodName", "name"), methodName)
        }
    }

    private fun findExceptionBreakpointType(): XBreakpointType<*, *>? {
        val types = XBreakpointType.EXTENSION_POINT_NAME.extensionList
        return types.firstOrNull { it.id == "java-exception" }
            ?: types.firstOrNull { type ->
                type.id.contains("exception", ignoreCase = true) ||
                    type.javaClass.simpleName.contains("Exception", ignoreCase = true)
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun addBreakpointUnchecked(
        type: XBreakpointType<*, *>,
        properties: XBreakpointProperties<*>?
    ): XBreakpoint<*> {
        val manager = XDebuggerManager.getInstance(project).breakpointManager
        return manager.addBreakpoint(
            type as XBreakpointType<XBreakpoint<XBreakpointProperties<*>>, XBreakpointProperties<*>>,
            properties as XBreakpointProperties<*>
        )
    }

    private fun configureExceptionProperties(
        properties: Any,
        filters: List<String>,
        className: String?
    ) {
        val normalized = filters.map { it.lowercase() }
        val wantsAll = normalized.isEmpty() || normalized.any { it == "all" || it == "any" }
        val wantsCaught = wantsAll || normalized.any { it == "caught" }
        val wantsUncaught = wantsAll || normalized.any { it == "uncaught" || it == "unhandled" }

        setPropertyIfPresent(
            properties,
            listOf("notifyCaught", "caught", "notifyCaughtExceptions", "NOTIFY_CAUGHT"),
            wantsCaught
        )
        setPropertyIfPresent(
            properties,
            listOf("notifyUncaught", "uncaught", "notifyUncaughtExceptions", "NOTIFY_UNCAUGHT"),
            wantsUncaught
        )

        if (!className.isNullOrBlank()) {
            setPropertyIfPresent(
                properties,
                listOf("exceptionClassName", "className", "classPattern", "qualifiedName", "myQualifiedName"),
                className
            )
            val packageName = className.substringBeforeLast('.', "")
            if (packageName.isNotBlank()) {
                setPropertyIfPresent(properties, listOf("myPackageName", "packageName"), packageName)
            }
        }
    }

    private fun setPropertyIfPresent(target: Any, names: List<String>, value: Any?) {
        val cls = target.javaClass
        for (name in names) {
            val setterName = "set" + name.replaceFirstChar { it.uppercase() }
            val method = cls.methods.firstOrNull {
                it.name == setterName && it.parameterCount == 1
            }
            if (method != null) {
                try {
                    method.invoke(target, value)
                    return
                } catch (e: Exception) {
                    log.debug("Failed to set $name on ${cls.simpleName}", e)
                }
            }

            val field = cls.declaredFields.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (field != null) {
                try {
                    field.isAccessible = true
                    field.set(target, value)
                    return
                } catch (e: Exception) {
                    log.debug("Failed to set field $name on ${cls.simpleName}", e)
                }
            }
        }
    }

    private fun getOrCreateThreadId(stack: XExecutionStack): Int {
        val key = stack.displayName
        val existing = threadIdsByStackName[key]
        if (existing != null) {
            stacksByThreadId[existing] = stack
            return existing
        }
        val id = threadIdGenerator.incrementAndGet()
        threadIdsByStackName[key] = id
        stacksByThreadId[id] = stack
        return id
    }

    private fun formatThreadName(displayName: String): String {
        val trimmed = displayName.trim()
        val firstQuote = trimmed.indexOf('"')
        if (firstQuote >= 0) {
            val secondQuote = trimmed.indexOf('"', firstQuote + 1)
            if (secondQuote > firstQuote + 1) {
                val quoted = trimmed.substring(firstQuote + 1, secondQuote)
                if (quoted.isNotBlank()) {
                    return quoted
                }
            }
        }

        val atIndex = trimmed.indexOf('@')
        if (atIndex > 0) {
            val prefix = trimmed.substring(0, atIndex).trim()
            if (prefix.isNotBlank()) {
                return prefix
            }
        }

        return trimmed
    }

    private suspend fun collectExecutionStacks(context: XSuspendContext): List<XExecutionStack> {
        val immediate = context.executionStacks.toList()
        val collected = mutableListOf<XExecutionStack>()
        val deferred = CompletableDeferred<List<XExecutionStack>>()
        val container = object : XSuspendContext.XExecutionStackContainer {
            override fun addExecutionStack(executionStacks: List<out XExecutionStack>, last: Boolean) {
                collected.addAll(executionStacks)
                if (last && !deferred.isCompleted) {
                    deferred.complete(collected.toList())
                }
            }

            override fun errorOccurred(errorMessage: String) {
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(DapErrors.internalError(errorMessage))
                }
            }
        }
        runOnEdt { context.computeExecutionStacks(container) }
        val computed = try {
            awaitWithTimeout("execution stacks", deferred)
        } catch (e: Exception) {
            log.warn("computeExecutionStacks failed: ${e.message}")
            collected.toList()
        }

        val combined = (immediate + computed).distinctBy { it.displayName }
        return if (combined.isNotEmpty()) combined else immediate
    }

    private suspend fun collectRunningStacks(process: XDebugProcess): List<XExecutionStack> {
        val collected = mutableListOf<XExecutionStack>()
        val deferred = CompletableDeferred<List<XExecutionStack>>()
        val container = object : XSuspendContext.XExecutionStackContainer {
            override fun addExecutionStack(executionStacks: List<out XExecutionStack>, last: Boolean) {
                collected.addAll(executionStacks)
                if (last && !deferred.isCompleted) {
                    deferred.complete(collected.toList())
                }
            }

            override fun errorOccurred(errorMessage: String) {
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(DapErrors.internalError(errorMessage))
                }
            }
        }
        // 使用反射调用 computeRunningExecutionStacks
        // 该方法标记为 @ApiStatus.Internal,编译时不可直接访问
        runOnEdt<Unit> {
            val method = process.javaClass.methods.firstOrNull { m ->
                m.name == "computeRunningExecutionStacks" &&
                    m.parameterTypes.size == 1 &&
                    XSuspendContext.XExecutionStackContainer::class.java.isAssignableFrom(m.parameterTypes[0])
            }
            if (method != null) {
                log.info("Invoking computeRunningExecutionStacks via reflection on ${process.javaClass.name}")
                runCatching { method.invoke(process, container) }
                    .onFailure { error ->
                        if (!deferred.isCompleted) {
                            deferred.completeExceptionally(error)
                        }
                    }
            } else {
                log.warn("computeRunningExecutionStacks not available; returning empty thread list")
                deferred.complete(emptyList())
            }
        }
        
        // 等待结果,允许一定的超时
        val result = try {
            awaitWithTimeout("running execution stacks", deferred)
        } catch (e: Exception) {
            log.warn("computeRunningExecutionStacks timed out or failed: ${e.message}")
            collected.toList()
        }
        
        return result
    }

    // NOTE: Java debugger fallback removed because it requires DebuggerManagerThread context.

    private suspend fun collectStackFrames(
        stack: XExecutionStack,
        startFrame: Int,
        levels: Int?
    ): List<XStackFrame> {
        val framesList = mutableListOf<XStackFrame>()
        if (startFrame == 0) {
            val topFrame = runOnEdt { stack.topFrame }
            if (topFrame != null) {
                framesList.add(topFrame)
            }
        }

        val deferred = CompletableDeferred<List<XStackFrame>>()
        val container = object : XExecutionStack.XStackFrameContainer {
            override fun addStackFrames(stackFrames: List<out XStackFrame>, last: Boolean) {
                framesList.addAll(stackFrames)
                if (last && !deferred.isCompleted) {
                    deferred.complete(framesList)
                }
            }

            override fun errorOccurred(errorMessage: String) {
                deferred.completeExceptionally(DapErrors.internalError(errorMessage))
            }
        }

        val firstIndex = if (startFrame == 0) 1 else startFrame
        runOnEdt { stack.computeStackFrames(firstIndex, container) }
        val allFrames = awaitWithTimeout("stack frames", deferred)
        val limited = if (levels != null) allFrames.take(levels) else allFrames
        return limited
    }

    private fun toDapStackFrame(frame: XStackFrame, frameId: Int): StackFrame {
        val position = frame.sourcePosition
        val source = position?.file?.let {
            Source(
                name = it.name,
                path = it.path
            )
        }
        val line = position?.line?.takeIf { it >= 0 }?.plus(1) ?: 1
        return StackFrame(
            id = frameId,
            name = source?.name ?: "frame",
            source = source,
            line = line,
            column = 1
        )
    }

    private sealed class VariableCandidate {
        data class Value(val name: String, val value: XValue) : VariableCandidate()
        data class Group(val group: XValueGroup) : VariableCandidate()
    }
    private suspend fun collectVariableCandidates(container: XValueContainer): List<VariableCandidate> {
        val deferred = CompletableDeferred<List<VariableCandidate>>()
        val candidates = mutableListOf<VariableCandidate>()
        val node = object : XCompositeNode {
            override fun addChildren(children: XValueChildrenList, last: Boolean) {
                children.topGroups.forEach { candidates.add(VariableCandidate.Group(it)) }
                children.topValues.forEach { candidates.add(VariableCandidate.Value(it.name, it)) }
                for (i in 0 until children.size()) {
                    candidates.add(VariableCandidate.Value(children.getName(i), children.getValue(i)))
                }
                children.bottomGroups.forEach { candidates.add(VariableCandidate.Group(it)) }
                if (last && !deferred.isCompleted) {
                    deferred.complete(candidates)
                }
            }

            override fun tooManyChildren(remaining: Int) {
                if (!deferred.isCompleted) {
                    deferred.complete(candidates)
                }
            }

            override fun setAlreadySorted(alreadySorted: Boolean) {
            }

            override fun setErrorMessage(errorMessage: String) {
                deferred.completeExceptionally(DapErrors.internalError(errorMessage))
            }

            override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {
                deferred.completeExceptionally(DapErrors.internalError(errorMessage))
            }

            override fun setMessage(
                message: String,
                icon: javax.swing.Icon?,
                attributes: com.intellij.ui.SimpleTextAttributes,
                link: XDebuggerTreeNodeHyperlink?
            ) {
            }
        }

        runOnEdt { container.computeChildren(node) }
        return awaitWithTimeout("variables", deferred)
    }

    private fun applyVariableFilter(
        candidates: List<VariableCandidate>,
        filter: VariablesFilter?
    ): List<VariableCandidate> {
        if (filter == null) {
            return candidates
        }
        return when (filter) {
            VariablesFilter.NAMED -> candidates.filter {
                when (it) {
                    is VariableCandidate.Value -> it.name.toIntOrNull() == null
                    is VariableCandidate.Group -> true
                }
            }
            VariablesFilter.INDEXED -> candidates.filter {
                when (it) {
                    is VariableCandidate.Value -> it.name.toIntOrNull() != null
                    is VariableCandidate.Group -> false
                }
            }
        }
    }

    private fun sliceVariables(
        candidates: List<VariableCandidate>,
        start: Int?,
        count: Int?
    ): List<VariableCandidate> {
        val from = start ?: 0
        if (from >= candidates.size) {
            return emptyList()
        }
        val to = if (count != null) (from + count).coerceAtMost(candidates.size) else candidates.size
        return candidates.subList(from, to)
    }

    private data class ValuePresentationInfo(
        val type: String?,
        val value: String,
        val hasChildren: Boolean
    )

    private data class SimpleXExpression(
        private val text: String,
        private val mode: EvaluationMode = EvaluationMode.CODE_FRAGMENT
    ) : XExpression {
        override fun getExpression(): String = text
        override fun getLanguage(): Language? = null
        override fun getCustomInfo(): String? = null
        override fun getMode(): EvaluationMode = mode
    }

    private suspend fun presentValue(value: XValue): ValuePresentationInfo {
        val updates = Channel<ValuePresentationInfo>(capacity = Channel.CONFLATED)
        val node = object : XValueNode {
            override fun setPresentation(
                icon: javax.swing.Icon?,
                type: String?,
                valueText: String,
                hasChildren: Boolean
            ) {
                updates.trySend(ValuePresentationInfo(type, valueText, hasChildren))
            }

            override fun setPresentation(
                icon: javax.swing.Icon?,
                presentation: XValuePresentation,
                hasChildren: Boolean
            ) {
                val renderer = PresentationRenderer()
                presentation.renderValue(renderer)
                updates.trySend(
                    ValuePresentationInfo(
                        presentation.type,
                        renderer.result(),
                        hasChildren
                    )
                )
            }

            override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
            }
        }

        runOnEdt { value.computePresentation(node, XValuePlace.TREE) }

        val first = try {
            withTimeout(READ_TIMEOUT_MS) { updates.receive() }
        } catch (e: TimeoutCancellationException) {
            throw DapErrors.internalError("Timeout while resolving value presentation")
        }

        var best = first
        val extraWaitMs = if (first.type == null && first.hasChildren) {
            VALUE_PRESENTATION_LONG_WAIT_MS
        } else {
            VALUE_PRESENTATION_SHORT_WAIT_MS
        }
        val deadlineMs = System.currentTimeMillis() + extraWaitMs
        while (true) {
            val remaining = deadlineMs - System.currentTimeMillis()
            if (remaining <= 0) break
            val next = withTimeoutOrNull(remaining) { updates.receive() } ?: break
            best = next
        }
        updates.close()

        return best.copy(type = normalizeType(best.type, best.value))
    }

    private fun normalizeType(rawType: String?, renderedValue: String): String? {
        val typeCandidate = rawType
            ?.substringBefore('@')
            ?.takeIf { it.isNotBlank() }
            ?: renderedValue.substringBefore('@').takeIf { it != renderedValue }

        val primitiveType = typeCandidate?.takeIf { it in JAVA_PRIMITIVE_TYPES }
            ?: inferPrimitiveTypeFromRenderedValue(renderedValue)
        if (primitiveType != null) {
            return primitiveType
        }

        if (typeCandidate == null) {
            return null
        }
        if (typeCandidate.contains('.')) {
            return typeCandidate
        }
        return resolveFullyQualifiedTypeIfUnique(typeCandidate) ?: typeCandidate
    }

    private fun inferPrimitiveTypeFromRenderedValue(renderedValue: String): String? {
        return when {
            renderedValue == "true" || renderedValue == "false" -> "boolean"
            renderedValue.startsWith("\"") && renderedValue.endsWith("\"") -> "java.lang.String"
            renderedValue.startsWith("'") && renderedValue.endsWith("'") && renderedValue.length >= 3 -> "char"
            renderedValue.matches(Regex("^-?\\d+$")) -> "int"
            renderedValue.matches(Regex("^-?\\d+\\.\\d+([eE][-+]?\\d+)?$")) -> "double"
            else -> null
        }
    }

    private fun resolveFullyQualifiedTypeIfUnique(simpleName: String): String? {
        return typeFqNameCache.computeIfAbsent(simpleName) { name ->
            if (DumbService.isDumb(project)) {
                return@computeIfAbsent null
            }
            ApplicationManager.getApplication().runReadAction(Computable {
                val classes = PsiShortNamesCache.getInstance(project)
                    .getClassesByName(name, GlobalSearchScope.allScope(project))
                val qualifiedNames = classes.mapNotNull { it.qualifiedName }.distinct()
                qualifiedNames.singleOrNull()
            })
        }
    }

    private class PresentationRenderer : XValuePresentation.XValueTextRenderer {
        private val sb = StringBuilder()
        override fun renderValue(value: String) {
            sb.append(value)
        }

        override fun renderStringValue(value: String) {
            sb.append('"').append(value).append('"')
        }

        override fun renderNumericValue(value: String) {
            sb.append(value)
        }

        override fun renderKeywordValue(value: String) {
            sb.append(value)
        }

        override fun renderValue(value: String, key: com.intellij.openapi.editor.colors.TextAttributesKey) {
            sb.append(value)
        }

        override fun renderStringValue(
            value: String,
            additionalSpecialCharsToHighlight: String?,
            maxLength: Int
        ) {
            sb.append('"').append(value).append('"')
        }

        override fun renderComment(comment: String) {
            if (sb.isNotEmpty()) {
                sb.append(' ')
            }
            sb.append(comment)
        }

        override fun renderSpecialSymbol(symbol: String) {
            sb.append(symbol)
        }

        override fun renderError(error: String) {
            sb.append(error)
        }

        fun result(): String = sb.toString()
    }

    private suspend fun evaluateExpression(
        evaluator: XDebuggerEvaluator,
        expression: String,
        position: XSourcePosition?
    ): XValue {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(result: XValue) {
                    continuation.resume(result)
                }

                override fun errorOccurred(errorMessage: String) {
                    continuation.resumeWithException(
                        DapErrors.evaluateError(expression, errorMessage)
                    )
                }

                override fun invalidExpression(error: String) {
                    continuation.resumeWithException(
                        DapErrors.evaluateError(expression, error)
                    )
                }
            }

            ApplicationManager.getApplication().invokeLater {
                try {
                    evaluator.evaluate(expression, callback, position)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    private suspend fun <T> awaitWithTimeout(
        label: String,
        deferred: CompletableDeferred<T>
    ): T {
        return try {
            withTimeout(READ_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            throw DapErrors.internalError("Timeout while resolving $label")
        }
    }

    private suspend fun <T> runOnEdt(block: () -> T): T {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            return block()
        }
        return suspendCancellableCoroutine { continuation ->
            app.invokeLater {
                try {
                    continuation.resume(block())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    // ========================================================================
    // Internal Data Classes
    // ========================================================================

    private enum class BreakpointKind {
        LINE,
        FUNCTION,
        EXCEPTION
    }

    private data class BreakpointInfo(
        val id: Int,
        val kind: BreakpointKind,
        val sourcePath: String?,
        val line: Int?,
        val breakpoint: XBreakpoint<*>
    )

    private data class FrameInfo(
        val id: Int,
        val threadId: Int,
        val index: Int,
        val frame: XStackFrame,
        val executionStack: XExecutionStack
    )

    private data class VariableRefInfo(
        val id: Int,
        val container: XValueContainer,
        val parentRef: Int? = null
    )

    private enum class ScopeType {
        LOCALS, ARGUMENTS, REGISTERS
    }

    private companion object {
        const val READ_TIMEOUT_MS = 5_000L
        private const val VALUE_PRESENTATION_SHORT_WAIT_MS = 50L
        private const val VALUE_PRESENTATION_LONG_WAIT_MS = 750L
        private val JAVA_PRIMITIVE_TYPES = setOf(
            "boolean",
            "byte",
            "short",
            "char",
            "int",
            "long",
            "float",
            "double"
        )
    }
}
