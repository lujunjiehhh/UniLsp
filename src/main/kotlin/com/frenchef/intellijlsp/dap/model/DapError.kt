package com.frenchef.intellijlsp.dap.model

/**
 * Exception thrown by DAP handlers and core components.
 */
class DapException(
    message: String,
    val errorId: Int = DapErrorId.INTERNAL_ERROR
) : Exception(message)

/**
 * DAP error IDs for structured error responses.
 */
object DapErrorId {
    const val UNKNOWN_COMMAND = 1001
    const val NOT_INITIALIZED = 1002
    const val ALREADY_INITIALIZED = 1003
    const val SESSION_TERMINATED = 1004
    const val NOT_STOPPED = 1005
    const val INVALID_ARGUMENTS = 1006
    const val INTERNAL_ERROR = 1007
    const val BREAKPOINT_NOT_FOUND = 1008
    const val THREAD_NOT_FOUND = 1009
    const val FRAME_NOT_FOUND = 1010
    const val VARIABLE_NOT_FOUND = 1011
    const val EVALUATE_ERROR = 1012
    const val LAUNCH_ERROR = 1013
    const val ATTACH_ERROR = 1014
}
