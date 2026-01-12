package com.frenchef.intellijlsp.dap.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * DAP Request Arguments
 * Based on Debug Adapter Protocol specification
 */

// ============================================================================
// Initialize Request Arguments
// ============================================================================

/**
 * Arguments for 'initialize' request.
 */
data class InitializeRequestArguments(
    @SerializedName("clientID")
    val clientId: String? = null,
    val clientName: String? = null,
    @SerializedName("adapterID")
    val adapterId: String,
    val locale: String? = null,
    val linesStartAt1: Boolean? = true,
    val columnsStartAt1: Boolean? = true,
    val pathFormat: PathFormat? = PathFormat.PATH,
    val supportsVariableType: Boolean? = null,
    val supportsVariablePaging: Boolean? = null,
    val supportsRunInTerminalRequest: Boolean? = null,
    val supportsMemoryReferences: Boolean? = null,
    val supportsProgressReporting: Boolean? = null,
    val supportsInvalidatedEvent: Boolean? = null,
    val supportsMemoryEvent: Boolean? = null,
    val supportsArgsCanBeInterpretedByShell: Boolean? = null,
    val supportsStartDebuggingRequest: Boolean? = null,
    val supportsANSIStyling: Boolean? = null,
    /**
     * The absolute path to the project folder to activate.
     * Non-standard extension to support multi-project environments.
     */
    val projectFolder: String? = null
)

enum class PathFormat {
    @SerializedName("path") PATH,
    @SerializedName("uri") URI
}

// ============================================================================
// Launch/Attach Request Arguments
// ============================================================================

/**
 * Arguments for 'launch' request.
 * This is a base class - actual arguments depend on the debug adapter.
 */
data class LaunchRequestArguments(
    val noDebug: Boolean? = null,
    @SerializedName("__restart")
    val restart: JsonElement? = null,
    // Common JVM debug arguments
    val mainClass: String? = null,
    val args: List<String>? = null,
    val cwd: String? = null,
    val env: Map<String, String>? = null,
    val stopOnEntry: Boolean? = null,
    val console: String? = null,
    // Additional custom arguments stored as raw JSON
    val program: String? = null,
    val classPaths: List<String>? = null,
    val modulePaths: List<String>? = null,
    val vmArgs: String? = null
)

/**
 * Arguments for 'attach' request.
 */
data class AttachRequestArguments(
    @SerializedName("__restart")
    val restart: JsonElement? = null,
    // Common attach arguments
    val hostName: String? = null,
    val port: Int? = null,
    val processId: Int? = null,
    val timeout: Int? = null
)

// ============================================================================
// Configuration Done Request Arguments
// ============================================================================

/**
 * Arguments for 'configurationDone' request.
 */
class ConfigurationDoneArguments

// ============================================================================
// Disconnect/Terminate Request Arguments
// ============================================================================

/**
 * Arguments for 'disconnect' request.
 */
data class DisconnectArguments(
    val restart: Boolean? = null,
    val terminateDebuggee: Boolean? = null,
    val suspendDebuggee: Boolean? = null
)

/**
 * Arguments for 'terminate' request.
 */
data class TerminateArguments(
    val restart: Boolean? = null
)

// ============================================================================
// Breakpoint Request Arguments
// ============================================================================

/**
 * Arguments for 'setBreakpoints' request.
 */
data class SetBreakpointsArguments(
    val source: Source,
    val breakpoints: List<SourceBreakpoint>? = null,
    val lines: List<Int>? = null,
    val sourceModified: Boolean? = null
)

/**
 * Arguments for 'setFunctionBreakpoints' request.
 */
data class SetFunctionBreakpointsArguments(
    val breakpoints: List<FunctionBreakpoint>
)

/**
 * Arguments for 'setExceptionBreakpoints' request.
 */
data class SetExceptionBreakpointsArguments(
    val filters: List<String>,
    val filterOptions: List<ExceptionFilterOptions>? = null,
    val exceptionOptions: List<ExceptionOptions>? = null
)

data class ExceptionFilterOptions(
    val filterId: String,
    val condition: String? = null,
    val mode: String? = null
)

data class ExceptionOptions(
    val path: List<ExceptionPathSegment>? = null,
    val breakMode: ExceptionBreakMode
)

data class ExceptionPathSegment(
    val negate: Boolean? = null,
    val names: List<String>
)

enum class ExceptionBreakMode {
    @SerializedName("never") NEVER,
    @SerializedName("always") ALWAYS,
    @SerializedName("unhandled") UNHANDLED,
    @SerializedName("userUnhandled") USER_UNHANDLED
}

// ============================================================================
// Thread/Stack Request Arguments
// ============================================================================

/**
 * Arguments for 'threads' request.
 * This request does not have any arguments.
 */
class ThreadsArguments

/**
 * Arguments for 'stackTrace' request.
 */
data class StackTraceArguments(
    val threadId: Int,
    val startFrame: Int? = null,
    val levels: Int? = null,
    val format: StackFrameFormat? = null
)

data class StackFrameFormat(
    val parameters: Boolean? = null,
    val parameterTypes: Boolean? = null,
    val parameterNames: Boolean? = null,
    val parameterValues: Boolean? = null,
    val line: Boolean? = null,
    val module: Boolean? = null,
    val includeAll: Boolean? = null
)

// ============================================================================
// Scope/Variable Request Arguments
// ============================================================================

/**
 * Arguments for 'scopes' request.
 */
data class ScopesArguments(
    val frameId: Int
)

/**
 * Arguments for 'variables' request.
 */
data class VariablesArguments(
    val variablesReference: Int,
    val filter: VariablesFilter? = null,
    val start: Int? = null,
    val count: Int? = null,
    val format: ValueFormat? = null
)

enum class VariablesFilter {
    @SerializedName("indexed") INDEXED,
    @SerializedName("named") NAMED
}

data class ValueFormat(
    val hex: Boolean? = null
)

// ============================================================================
// Evaluate Request Arguments
// ============================================================================

/**
 * Arguments for 'evaluate' request.
 */
data class EvaluateArguments(
    val expression: String,
    val frameId: Int? = null,
    val context: String? = null,
    val format: ValueFormat? = null
)

// ============================================================================
// Execution Control Request Arguments
// ============================================================================

/**
 * Arguments for 'continue' request.
 */
data class ContinueArguments(
    val threadId: Int,
    val singleThread: Boolean? = null
)

/**
 * Arguments for 'next' request.
 */
data class NextArguments(
    val threadId: Int,
    val singleThread: Boolean? = null,
    val granularity: SteppingGranularity? = null
)

/**
 * Arguments for 'stepIn' request.
 */
data class StepInArguments(
    val threadId: Int,
    val singleThread: Boolean? = null,
    val targetId: Int? = null,
    val granularity: SteppingGranularity? = null
)

/**
 * Arguments for 'stepOut' request.
 */
data class StepOutArguments(
    val threadId: Int,
    val singleThread: Boolean? = null,
    val granularity: SteppingGranularity? = null
)

/**
 * Arguments for 'pause' request.
 */
data class PauseArguments(
    val threadId: Int
)

// ============================================================================
// Cancel Request Arguments
// ============================================================================

/**
 * Arguments for 'cancel' request.
 */
data class CancelArguments(
    val requestId: Int? = null,
    val progressId: String? = null
)

// ============================================================================
// Source Request Arguments
// ============================================================================

/**
 * Arguments for 'source' request.
 */
data class SourceArguments(
    val source: Source? = null,
    val sourceReference: Int? = null
)

// ============================================================================
// SetVariable Request Arguments
// ============================================================================

/**
 * Arguments for 'setVariable' request.
 */
data class SetVariableArguments(
    val variablesReference: Int,
    val name: String,
    val value: String,
    val format: ValueFormat? = null
)

// ============================================================================
// Modules / LoadedSources / ExceptionInfo / Restart
// ============================================================================

/**
 * Arguments for 'modules' request.
 */
data class ModulesArguments(
    val startModule: Int? = null,
    val moduleCount: Int? = null
)

/**
 * Arguments for 'loadedSources' request.
 */
class LoadedSourcesArguments

/**
 * Arguments for 'exceptionInfo' request.
 */
data class ExceptionInfoArguments(
    val threadId: Int
)

/**
 * Arguments for 'restart' request.
 */
class RestartArguments
