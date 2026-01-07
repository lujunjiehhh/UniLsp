package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.ContinueArguments
import com.frenchef.intellijlsp.dap.model.ContinueResponseBody
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.google.gson.JsonElement

/**
 * Handler for the DAP "continue" request.
 */
class ContinueHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<ContinueArguments>(arguments, DapCommands.CONTINUE)
        val allThreadsContinued = backend.continueExecution(
            args.threadId,
            args.singleThread ?: false
        )
        return DapGson.instance.toJsonTree(ContinueResponseBody(allThreadsContinued))
    }
}
