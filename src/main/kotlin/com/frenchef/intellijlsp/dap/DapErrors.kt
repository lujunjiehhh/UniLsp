package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.model.DapErrorId
import com.frenchef.intellijlsp.dap.model.DapException
import com.intellij.openapi.diagnostic.logger

/**
 * DAP Error Handling Utilities
 * 
 * Provides standardized error mapping, logging conventions, and error response generation
 * for the DAP server implementation.
 */
object DapErrors {
    
    private val log = logger<DapErrors>()

    private fun logError(category: String, message: String, exception: Throwable? = null) {
        val fullMessage = "[DAP:$category] $message"
        if (exception != null) {
            log.error(fullMessage, exception)
        } else {
            log.error(fullMessage)
        }
    }
    
    // ========================================================================
    // Error Categories
    // ========================================================================
    
    /**
     * Error categories for logging and diagnostics.
     */
    enum class ErrorCategory {
        /** Protocol-level errors (invalid messages, state violations) */
        PROTOCOL,
        /** Debugger backend errors (launch failures, evaluation errors) */
        BACKEND,
        /** Transport-level errors (connection issues, I/O errors) */
        TRANSPORT,
        /** Internal errors (unexpected exceptions) */
        INTERNAL
    }
    
    // ========================================================================
    // Error Logging
    // ========================================================================
    
    /**
     * Log a protocol error.
     */
    fun logProtocolError(message: String, details: String? = null) {
        log.warn("[DAP:PROTOCOL] $message${details?.let { " - $it" } ?: ""}")
    }
    
    /**
     * Log a backend error.
     */
    fun logBackendError(message: String, exception: Throwable? = null) {
        logError("BACKEND", message, exception)
    }
    
    /**
     * Log a transport error.
     */
    fun logTransportError(message: String, exception: Throwable? = null) {
        logError("TRANSPORT", message, exception)
    }
    
    /**
     * Log an internal error.
     */
    fun logInternalError(message: String, exception: Throwable? = null) {
        logError("INTERNAL", message, exception)
    }
    
    /**
     * Log a debug message.
     */
    fun logDebug(message: String) {
        log.debug("[DAP] $message")
    }
    
    /**
     * Log an info message.
     */
    fun logInfo(message: String) {
        log.info("[DAP] $message")
    }
    
    // ========================================================================
    // Exception Factories
    // ========================================================================
    
    /**
     * Create a "not initialized" exception.
     */
    fun notInitialized(): DapException {
        return DapException("Session not initialized", DapErrorId.NOT_INITIALIZED)
    }
    
    /**
     * Create an "already initialized" exception.
     */
    fun alreadyInitialized(): DapException {
        return DapException("Session already initialized", DapErrorId.ALREADY_INITIALIZED)
    }
    
    /**
     * Create a "session terminated" exception.
     */
    fun sessionTerminated(): DapException {
        return DapException("Session terminated", DapErrorId.SESSION_TERMINATED)
    }
    
    /**
     * Create a "not stopped" exception.
     */
    fun notStopped(): DapException {
        return DapException("notStopped", DapErrorId.NOT_STOPPED)
    }
    
    /**
     * Create an "invalid arguments" exception.
     */
    fun invalidArguments(message: String): DapException {
        return DapException("Invalid arguments: $message", DapErrorId.INVALID_ARGUMENTS)
    }
    
    /**
     * Create a "thread not found" exception.
     */
    fun threadNotFound(threadId: Int): DapException {
        return DapException("Thread not found: $threadId", DapErrorId.THREAD_NOT_FOUND)
    }
    
    /**
     * Create a "frame not found" exception.
     */
    fun frameNotFound(frameId: Int): DapException {
        return DapException("Frame not found: $frameId", DapErrorId.FRAME_NOT_FOUND)
    }
    
    /**
     * Create a "variable not found" exception.
     */
    fun variableNotFound(variablesReference: Int): DapException {
        return DapException("Variable reference not found: $variablesReference", DapErrorId.VARIABLE_NOT_FOUND)
    }
    
    /**
     * Create a "breakpoint not found" exception.
     */
    fun breakpointNotFound(breakpointId: Int): DapException {
        return DapException("Breakpoint not found: $breakpointId", DapErrorId.BREAKPOINT_NOT_FOUND)
    }
    
    /**
     * Create an "evaluate error" exception.
     */
    fun evaluateError(expression: String, reason: String): DapException {
        return DapException("Failed to evaluate '$expression': $reason", DapErrorId.EVALUATE_ERROR)
    }
    
    /**
     * Create a "launch error" exception.
     */
    fun launchError(reason: String): DapException {
        return DapException("Failed to launch: $reason", DapErrorId.LAUNCH_ERROR)
    }
    
    /**
     * Create an "attach error" exception.
     */
    fun attachError(reason: String): DapException {
        return DapException("Failed to attach: $reason", DapErrorId.ATTACH_ERROR)
    }
    
    /**
     * Create an "internal error" exception.
     */
    fun internalError(message: String): DapException {
        return DapException("Internal error: $message", DapErrorId.INTERNAL_ERROR)
    }
    
    // ========================================================================
    // Error Mapping
    // ========================================================================
    
    /**
     * Map an exception to a DAP exception.
     */
    fun mapException(e: Throwable): DapException {
        return when (e) {
            is DapException -> e
            is IllegalArgumentException -> invalidArguments(e.message ?: "Unknown")
            is IllegalStateException -> internalError(e.message ?: "Invalid state")
            is UnsupportedOperationException -> DapException(
                "Operation not supported: ${e.message ?: "unspecified"}",
                DapErrorId.INTERNAL_ERROR
            )
            else -> internalError(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Determine the error category for an exception.
     */
    fun categorize(e: Throwable): ErrorCategory {
        return when (e) {
            is DapException -> when (e.errorId) {
                DapErrorId.NOT_INITIALIZED,
                DapErrorId.ALREADY_INITIALIZED,
                DapErrorId.SESSION_TERMINATED,
                DapErrorId.NOT_STOPPED,
                DapErrorId.INVALID_ARGUMENTS,
                DapErrorId.UNKNOWN_COMMAND -> ErrorCategory.PROTOCOL
                
                DapErrorId.THREAD_NOT_FOUND,
                DapErrorId.FRAME_NOT_FOUND,
                DapErrorId.VARIABLE_NOT_FOUND,
                DapErrorId.BREAKPOINT_NOT_FOUND,
                DapErrorId.EVALUATE_ERROR,
                DapErrorId.LAUNCH_ERROR,
                DapErrorId.ATTACH_ERROR -> ErrorCategory.BACKEND
                
                else -> ErrorCategory.INTERNAL
            }
            is java.io.IOException -> ErrorCategory.TRANSPORT
            else -> ErrorCategory.INTERNAL
        }
    }
}
