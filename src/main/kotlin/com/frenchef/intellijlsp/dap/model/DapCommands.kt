package com.frenchef.intellijlsp.dap.model

/**
 * DAP command and event names.
 */
object DapCommands {
    const val INITIALIZE = "initialize"
    const val CONFIGURATION_DONE = "configurationDone"
    const val LAUNCH = "launch"
    const val ATTACH = "attach"
}

object DapEvents {
    const val INITIALIZED = "initialized"
}
