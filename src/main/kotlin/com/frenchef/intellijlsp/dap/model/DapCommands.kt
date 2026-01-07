package com.frenchef.intellijlsp.dap.model

/**
 * DAP command and event names.
 */
object DapCommands {
    const val INITIALIZE = "initialize"
    const val CONFIGURATION_DONE = "configurationDone"
    const val LAUNCH = "launch"
    const val ATTACH = "attach"
    const val SET_BREAKPOINTS = "setBreakpoints"
    const val THREADS = "threads"
    const val STACK_TRACE = "stackTrace"
    const val CONTINUE = "continue"
    const val NEXT = "next"
    const val STEP_IN = "stepIn"
    const val STEP_OUT = "stepOut"
    const val PAUSE = "pause"
    const val SCOPES = "scopes"
    const val VARIABLES = "variables"
    const val EVALUATE = "evaluate"
    const val SET_EXPRESSION = "setExpression"
    const val SOURCE = "source"
    const val EXCEPTION_INFO = "exceptionInfo"
}

object DapEvents {
    const val INITIALIZED = "initialized"
    const val STOPPED = "stopped"
    const val CONTINUED = "continued"
    const val THREAD = "thread"
    const val BREAKPOINT = "breakpoint"
    const val OUTPUT = "output"
    const val EXITED = "exited"
    const val TERMINATED = "terminated"
}
