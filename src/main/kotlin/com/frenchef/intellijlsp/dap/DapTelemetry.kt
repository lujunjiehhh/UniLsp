package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.DapErrors.ErrorCategory

/**
 * Optional telemetry hooks for observing DAP traffic and errors.
 */
interface DapTelemetry {
    fun onRequest(command: String, seq: Int)
    fun onResponse(command: String, success: Boolean, seq: Int, requestSeq: Int)
    fun onEvent(event: String, seq: Int)
    fun onError(category: ErrorCategory, message: String, exception: Throwable? = null)
}

object NoopDapTelemetry : DapTelemetry {
    override fun onRequest(command: String, seq: Int) {}
    override fun onResponse(command: String, success: Boolean, seq: Int, requestSeq: Int) {}
    override fun onEvent(event: String, seq: Int) {}
    override fun onError(category: ErrorCategory, message: String, exception: Throwable?) {}
}
