package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.RestartArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "restart" request.
 */
class RestartHandler(private val backend: DebuggerBackend) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        parseOptionalArguments<RestartArguments>(arguments, DapCommands.RESTART)
        backend.restart()
        return null
    }
}
