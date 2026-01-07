package com.frenchef.intellijlsp.dap.model

/**
 * DAP command and event names.
 */
object DapCommands {
    const val INITIALIZE = "initialize"
    const val CONFIGURATION_DONE = "configurationDone"
    const val LAUNCH = "launch"
    const val ATTACH = "attach"
    const val DISCONNECT = "disconnect"
    const val TERMINATE = "terminate"
    const val STACK_TRACE = "stackTrace"
    const val SCOPES = "scopes"
    const val VARIABLES = "variables"
    const val EVALUATE = "evaluate"
    const val SET_EXPRESSION = "setExpression"
    const val SOURCE = "source"
    const val EXCEPTION_INFO = "exceptionInfo"
}

object DapEvents {
    const val INITIALIZED = "initialized"
}
