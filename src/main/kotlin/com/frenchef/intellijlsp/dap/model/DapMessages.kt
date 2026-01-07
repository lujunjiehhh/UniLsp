package com.frenchef.intellijlsp.dap.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * DAP Protocol Message Types
 * Based on Debug Adapter Protocol specification
 * 
 * DAP uses a similar message format to JSON-RPC but with different semantics:
 * - seq: Sequence number (message ID), starts at 1
 * - type: "request", "response", or "event"
 */

// ============================================================================
// Base Protocol Messages
// ============================================================================

/**
 * Base class for all DAP protocol messages.
 */
sealed class DapMessage {
    abstract val seq: Int
    abstract val type: String
}

/**
 * DAP message kind mapping for the wire "type" field.
 *
 * Mapping rules:
 * - "request"  -> [DapRequest]
 * - "response" -> [DapResponse]
 * - "event"    -> [DapEvent]
 */
enum class DapMessageKind(val wireType: String) {
    REQUEST("request"),
    RESPONSE("response"),
    EVENT("event");

    companion object {
        private val byWireType = values().associateBy { it.wireType }

        fun fromWireType(type: String): DapMessageKind? {
            return byWireType[type]
        }
    }
}

/**
 * A client or debug adapter initiated request.
 */
data class DapRequest(
    override val seq: Int,
    override val type: String = "request",
    val command: String,
    val arguments: JsonElement? = null
) : DapMessage()

/**
 * Response to a request.
 */
data class DapResponse(
    override val seq: Int,
    override val type: String = "response",
    @SerializedName("request_seq")
    val requestSeq: Int,
    val success: Boolean,
    val command: String,
    val message: String? = null,
    val body: JsonElement? = null
) : DapMessage()

/**
 * A debug adapter initiated event.
 */
data class DapEvent(
    override val seq: Int,
    override val type: String = "event",
    val event: String,
    val body: JsonElement? = null
) : DapMessage()

/**
 * Error response body structure.
 */
data class DapErrorBody(
    val error: DapErrorMessage? = null
)

/**
 * Structured error message in DAP.
 */
data class DapErrorMessage(
    val id: Int,
    val format: String,
    val variables: Map<String, String>? = null,
    val sendTelemetry: Boolean? = null,
    val showUser: Boolean? = null,
    val url: String? = null,
    val urlLabel: String? = null
)

// ============================================================================
// DAP Error Codes (predefined message values)
// ============================================================================

object DapErrorCodes {
    const val CANCELLED = "cancelled"
    const val NOT_STOPPED = "notStopped"
}
