package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.PauseArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "pause" request.
 */
class PauseHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<PauseArguments>(arguments, DapCommands.PAUSE)
        backend.pause(args.threadId)
        return null
    }
}
