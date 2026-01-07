package com.frenchef.intellijlsp.dap.model

import com.google.gson.JsonElement

/**
 * DAP Response Bodies
 * Based on Debug Adapter Protocol specification
 */

// ============================================================================
// Initialize Response
// ============================================================================

/**
 * Response to 'initialize' request - contains capabilities.
 */
data class Capabilities(
    val supportsConfigurationDoneRequest: Boolean? = null,
    val supportsFunctionBreakpoints: Boolean? = null,
    val supportsConditionalBreakpoints: Boolean? = null,
    val supportsHitConditionalBreakpoints: Boolean? = null,
    val supportsEvaluateForHovers: Boolean? = null,
    val exceptionBreakpointFilters: List<ExceptionBreakpointsFilter>? = null,
    val supportsStepBack: Boolean? = null,
    val supportsSetVariable: Boolean? = null,
    val supportsRestartFrame: Boolean? = null,
    val supportsGotoTargetsRequest: Boolean? = null,
    val supportsStepInTargetsRequest: Boolean? = null,
    val supportsCompletionsRequest: Boolean? = null,
    val completionTriggerCharacters: List<String>? = null,
    val supportsModulesRequest: Boolean? = null,
    val additionalModuleColumns: List<ColumnDescriptor>? = null,
    val supportedChecksumAlgorithms: List<ChecksumAlgorithm>? = null,
    val supportsRestartRequest: Boolean? = null,
    val supportsExceptionOptions: Boolean? = null,
    val supportsValueFormattingOptions: Boolean? = null,
    val supportsExceptionInfoRequest: Boolean? = null,
    val supportTerminateDebuggee: Boolean? = null,
    val supportSuspendDebuggee: Boolean? = null,
    val supportsDelayedStackTraceLoading: Boolean? = null,
    val supportsLoadedSourcesRequest: Boolean? = null,
    val supportsLogPoints: Boolean? = null,
    val supportsTerminateThreadsRequest: Boolean? = null,
    val supportsSetExpression: Boolean? = null,
    val supportsTerminateRequest: Boolean? = null,
    val supportsDataBreakpoints: Boolean? = null,
    val supportsReadMemoryRequest: Boolean? = null,
    val supportsWriteMemoryRequest: Boolean? = null,
    val supportsDisassembleRequest: Boolean? = null,
    val supportsCancelRequest: Boolean? = null,
    val supportsBreakpointLocationsRequest: Boolean? = null,
    val supportsClipboardContext: Boolean? = null,
    val supportsSteppingGranularity: Boolean? = null,
    val supportsInstructionBreakpoints: Boolean? = null,
    val supportsExceptionFilterOptions: Boolean? = null,
    val supportsSingleThreadExecutionRequests: Boolean? = null,
    val supportsANSIStyling: Boolean? = null
)

data class ColumnDescriptor(
    val attributeName: String,
    val label: String,
    val format: String? = null,
    val type: ColumnDescriptorType? = null,
    val width: Int? = null
)

enum class ColumnDescriptorType {
    string, number, boolean, unixTimestampUTC
}

// ============================================================================
// SetBreakpoints Response
// ============================================================================

/**
 * Response to 'setBreakpoints' request.
 */
data class SetBreakpointsResponseBody(
    val breakpoints: List<Breakpoint>
)

// ============================================================================
// SetFunctionBreakpoints Response
// ============================================================================

/**
 * Response to 'setFunctionBreakpoints' request.
 */
data class SetFunctionBreakpointsResponseBody(
    val breakpoints: List<Breakpoint>
)

// ============================================================================
// SetExceptionBreakpoints Response
// ============================================================================

/**
 * Response to 'setExceptionBreakpoints' request.
 */
data class SetExceptionBreakpointsResponseBody(
    val breakpoints: List<Breakpoint>? = null
)

// ============================================================================
// Threads Response
// ============================================================================

/**
 * Response to 'threads' request.
 */
data class ThreadsResponseBody(
    val threads: List<Thread>
)

// ============================================================================
// StackTrace Response
// ============================================================================

/**
 * Response to 'stackTrace' request.
 */
data class StackTraceResponseBody(
    val stackFrames: List<StackFrame>,
    val totalFrames: Int? = null
)

// ============================================================================
// Scopes Response
// ============================================================================

/**
 * Response to 'scopes' request.
 */
data class ScopesResponseBody(
    val scopes: List<Scope>
)

// ============================================================================
// Variables Response
// ============================================================================

/**
 * Response to 'variables' request.
 */
data class VariablesResponseBody(
    val variables: List<Variable>
)

// ============================================================================
// Evaluate Response
// ============================================================================

/**
 * Response to 'evaluate' request.
 */
data class EvaluateResponseBody(
    val result: String,
    val type: String? = null,
    val presentationHint: VariablePresentationHint? = null,
    val variablesReference: Int,
    val namedVariables: Int? = null,
    val indexedVariables: Int? = null,
    val memoryReference: String? = null
)

// ============================================================================
// Continue Response
// ============================================================================

/**
 * Response to 'continue' request.
 */
data class ContinueResponseBody(
    val allThreadsContinued: Boolean? = null
)

// ============================================================================
// Empty Response Bodies (for requests that don't return data)
// ============================================================================

/**
 * Response to 'configurationDone' request.
 */
class ConfigurationDoneResponseBody

/**
 * Response to 'disconnect' request.
 */
class DisconnectResponseBody

/**
 * Response to 'terminate' request.
 */
class TerminateResponseBody

/**
 * Response to 'next' request.
 */
class NextResponseBody

/**
 * Response to 'stepIn' request.
 */
class StepInResponseBody

/**
 * Response to 'stepOut' request.
 */
class StepOutResponseBody

/**
 * Response to 'pause' request.
 */
class PauseResponseBody
