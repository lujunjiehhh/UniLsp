package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.NextArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "next" request.
 */
class NextHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<NextArguments>(arguments, DapCommands.NEXT)
        backend.next(args.threadId, args.granularity)
        return null
    }
}
