package com.frenchef.intellijlsp.dap.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * DAP Event Bodies
 * Based on Debug Adapter Protocol specification
 */

// ============================================================================
// Initialized Event
// ============================================================================

/**
 * Body for 'initialized' event.
 * This event has no body.
 */
class InitializedEventBody

// ============================================================================
// Stopped Event
// ============================================================================

/**
 * Body for 'stopped' event.
 */
data class StoppedEventBody(
    val reason: StoppedReason,
    val description: String? = null,
    val threadId: Int? = null,
    val preserveFocusHint: Boolean? = null,
    val text: String? = null,
    val allThreadsStopped: Boolean? = null,
    val hitBreakpointIds: List<Int>? = null
)

enum class StoppedReason {
    @SerializedName("step") STEP,
    @SerializedName("breakpoint") BREAKPOINT,
    @SerializedName("exception") EXCEPTION,
    @SerializedName("pause") PAUSE,
    @SerializedName("entry") ENTRY,
    @SerializedName("goto") GOTO,
    @SerializedName("function breakpoint") FUNCTION_BREAKPOINT,
    @SerializedName("data breakpoint") DATA_BREAKPOINT,
    @SerializedName("instruction breakpoint") INSTRUCTION_BREAKPOINT
}

// ============================================================================
// Continued Event
// ============================================================================

/**
 * Body for 'continued' event.
 */
data class ContinuedEventBody(
    val threadId: Int,
    val allThreadsContinued: Boolean? = null
)

// ============================================================================
// Exited Event
// ============================================================================

/**
 * Body for 'exited' event.
 */
data class ExitedEventBody(
    val exitCode: Int
)

// ============================================================================
// Terminated Event
// ============================================================================

/**
 * Body for 'terminated' event.
 */
data class TerminatedEventBody(
    val restart: JsonElement? = null
)

// ============================================================================
// Thread Event
// ============================================================================

/**
 * Body for 'thread' event.
 */
data class ThreadEventBody(
    val reason: ThreadEventReason,
    val threadId: Int
)

enum class ThreadEventReason {
    @SerializedName("started") STARTED,
    @SerializedName("exited") EXITED
}

// ============================================================================
// Output Event
// ============================================================================

/**
 * Body for 'output' event.
 */
data class OutputEventBody(
    val category: OutputCategory? = null,
    val output: String,
    val group: OutputGroup? = null,
    val variablesReference: Int? = null,
    val source: Source? = null,
    val line: Int? = null,
    val column: Int? = null,
    val data: JsonElement? = null,
    val locationReference: Int? = null
)

enum class OutputCategory {
    @SerializedName("console") CONSOLE,
    @SerializedName("important") IMPORTANT,
    @SerializedName("stdout") STDOUT,
    @SerializedName("stderr") STDERR,
    @SerializedName("telemetry") TELEMETRY
}

enum class OutputGroup {
    @SerializedName("start") START,
    @SerializedName("startCollapsed") START_COLLAPSED,
    @SerializedName("end") END
}

// ============================================================================
// Breakpoint Event
// ============================================================================

/**
 * Body for 'breakpoint' event.
 */
data class BreakpointEventBody(
    val reason: BreakpointEventReason,
    val breakpoint: Breakpoint
)

enum class BreakpointEventReason {
    @SerializedName("changed") CHANGED,
    @SerializedName("new") NEW,
    @SerializedName("removed") REMOVED
}

// ============================================================================
// Module Event
// ============================================================================

/**
 * Body for 'module' event.
 */
data class ModuleEventBody(
    val reason: ModuleEventReason,
    val module: Module
)

enum class ModuleEventReason {
    @SerializedName("new") NEW,
    @SerializedName("changed") CHANGED,
    @SerializedName("removed") REMOVED
}

// ============================================================================
// Loaded Source Event
// ============================================================================

/**
 * Body for 'loadedSource' event.
 */
data class LoadedSourceEventBody(
    val reason: LoadedSourceEventReason,
    val source: Source
)

enum class LoadedSourceEventReason {
    @SerializedName("new") NEW,
    @SerializedName("changed") CHANGED,
    @SerializedName("removed") REMOVED
}

// ============================================================================
// Process Event
// ============================================================================

/**
 * Body for 'process' event.
 */
data class ProcessEventBody(
    val name: String,
    val systemProcessId: Int? = null,
    val isLocalProcess: Boolean? = null,
    val startMethod: ProcessStartMethod? = null,
    val pointerSize: Int? = null
)

enum class ProcessStartMethod {
    @SerializedName("launch") LAUNCH,
    @SerializedName("attach") ATTACH,
    @SerializedName("attachForSuspendedLaunch") ATTACH_FOR_SUSPENDED_LAUNCH
}

// ============================================================================
// Capabilities Event
// ============================================================================

/**
 * Body for 'capabilities' event.
 */
data class CapabilitiesEventBody(
    val capabilities: Capabilities
)

// ============================================================================
// Progress Events
// ============================================================================

/**
 * Body for 'progressStart' event.
 */
data class ProgressStartEventBody(
    val progressId: String,
    val title: String,
    val requestId: Int? = null,
    val cancellable: Boolean? = null,
    val message: String? = null,
    val percentage: Int? = null
)

/**
 * Body for 'progressUpdate' event.
 */
data class ProgressUpdateEventBody(
    val progressId: String,
    val message: String? = null,
    val percentage: Int? = null
)

/**
 * Body for 'progressEnd' event.
 */
data class ProgressEndEventBody(
    val progressId: String,
    val message: String? = null
)

// ============================================================================
// Invalidated Event
// ============================================================================

/**
 * Body for 'invalidated' event.
 */
data class InvalidatedEventBody(
    val areas: List<InvalidatedArea>? = null,
    val threadId: Int? = null,
    val stackFrameId: Int? = null
)

enum class InvalidatedArea {
    @SerializedName("all") ALL,
    @SerializedName("stacks") STACKS,
    @SerializedName("threads") THREADS,
    @SerializedName("variables") VARIABLES
}

// ============================================================================
// Memory Event
// ============================================================================

/**
 * Body for 'memory' event.
 */
data class MemoryEventBody(
    val memoryReference: String,
    val offset: Int,
    val count: Int
)
