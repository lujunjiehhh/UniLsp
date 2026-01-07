package com.frenchef.intellijlsp.dap.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * DAP Core Types
 * Based on Debug Adapter Protocol specification
 */

// ============================================================================
// Source and Location Types
// ============================================================================

/**
 * A Source is a descriptor for source code.
 */
data class Source(
    val name: String? = null,
    val path: String? = null,
    val sourceReference: Int? = null,
    val presentationHint: SourcePresentationHint? = null,
    val origin: String? = null,
    val sources: List<Source>? = null,
    val adapterData: JsonElement? = null,
    val checksums: List<Checksum>? = null
)

enum class SourcePresentationHint {
    @SerializedName("normal") NORMAL,
    @SerializedName("emphasize") EMPHASIZE,
    @SerializedName("deemphasize") DEEMPHASIZE
}

data class Checksum(
    val algorithm: ChecksumAlgorithm,
    val checksum: String
)

enum class ChecksumAlgorithm {
    @SerializedName("MD5") MD5,
    @SerializedName("SHA1") SHA1,
    @SerializedName("SHA256") SHA256,
    @SerializedName("timestamp") TIMESTAMP
}

// ============================================================================
// Thread and Stack Types
// ============================================================================

/**
 * A Thread.
 */
data class Thread(
    val id: Int,
    val name: String
)

/**
 * A Stackframe contains the source location.
 */
data class StackFrame(
    val id: Int,
    val name: String,
    val source: Source? = null,
    val line: Int,
    val column: Int,
    val endLine: Int? = null,
    val endColumn: Int? = null,
    val canRestart: Boolean? = null,
    val instructionPointerReference: String? = null,
    val moduleId: JsonElement? = null,
    val presentationHint: StackFramePresentationHint? = null
)

enum class StackFramePresentationHint {
    @SerializedName("normal") NORMAL,
    @SerializedName("label") LABEL,
    @SerializedName("subtle") SUBTLE
}

// ============================================================================
// Scope and Variable Types
// ============================================================================

/**
 * A Scope is a named container for variables.
 */
data class Scope(
    val name: String,
    val presentationHint: String? = null,
    val variablesReference: Int,
    val namedVariables: Int? = null,
    val indexedVariables: Int? = null,
    val expensive: Boolean = false,
    val source: Source? = null,
    val line: Int? = null,
    val column: Int? = null,
    val endLine: Int? = null,
    val endColumn: Int? = null
)

/**
 * A Variable is a name/value pair.
 */
data class Variable(
    val name: String,
    val value: String,
    val type: String? = null,
    val presentationHint: VariablePresentationHint? = null,
    val evaluateName: String? = null,
    val variablesReference: Int,
    val namedVariables: Int? = null,
    val indexedVariables: Int? = null,
    val memoryReference: String? = null,
    val declarationLocationReference: Int? = null,
    val valueLocationReference: Int? = null
)

/**
 * Properties of a variable that can be used to determine how to render the variable in the UI.
 */
data class VariablePresentationHint(
    val kind: String? = null,
    val attributes: List<String>? = null,
    val visibility: String? = null,
    val lazy: Boolean? = null
)

// ============================================================================
// Breakpoint Types
// ============================================================================

/**
 * Information about a breakpoint created in setBreakpoints request.
 */
data class Breakpoint(
    val id: Int? = null,
    val verified: Boolean,
    val message: String? = null,
    val source: Source? = null,
    val line: Int? = null,
    val column: Int? = null,
    val endLine: Int? = null,
    val endColumn: Int? = null,
    val instructionReference: String? = null,
    val offset: Int? = null,
    val reason: BreakpointReason? = null
)

enum class BreakpointReason {
    @SerializedName("pending") PENDING,
    @SerializedName("failed") FAILED
}

/**
 * Properties of a breakpoint or logpoint passed to setBreakpoints request.
 */
data class SourceBreakpoint(
    val line: Int,
    val column: Int? = null,
    val condition: String? = null,
    val hitCondition: String? = null,
    val logMessage: String? = null,
    val mode: String? = null
)

/**
 * Properties of a breakpoint passed to setFunctionBreakpoints request.
 */
data class FunctionBreakpoint(
    val name: String,
    val condition: String? = null,
    val hitCondition: String? = null
)

// ============================================================================
// Exception Breakpoint Types
// ============================================================================

/**
 * An ExceptionBreakpointsFilter is shown in the UI as an filter option for configuring how exceptions are dealt with.
 */
data class ExceptionBreakpointsFilter(
    val filter: String,
    val label: String,
    val description: String? = null,
    val default: Boolean? = null,
    val supportsCondition: Boolean? = null,
    val conditionDescription: String? = null
)

// ============================================================================
// Module Types
// ============================================================================

/**
 * A Module object represents a row in the modules view.
 */
data class Module(
    val id: JsonElement,
    val name: String,
    val path: String? = null,
    val isOptimized: Boolean? = null,
    val isUserCode: Boolean? = null,
    val version: String? = null,
    val symbolStatus: String? = null,
    val symbolFilePath: String? = null,
    val dateTimeStamp: String? = null,
    val addressRange: String? = null
)

// ============================================================================
// Evaluate Types
// ============================================================================

/**
 * Context in which the evaluate request is run.
 */
enum class EvaluateContext {
    @SerializedName("watch") WATCH,
    @SerializedName("repl") REPL,
    @SerializedName("hover") HOVER,
    @SerializedName("clipboard") CLIPBOARD,
    @SerializedName("variables") VARIABLES
}

// ============================================================================
// Stepping Granularity
// ============================================================================

enum class SteppingGranularity {
    @SerializedName("statement") STATEMENT,
    @SerializedName("line") LINE,
    @SerializedName("instruction") INSTRUCTION
}
