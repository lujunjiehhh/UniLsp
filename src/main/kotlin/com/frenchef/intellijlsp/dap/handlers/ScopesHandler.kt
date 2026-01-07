package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.ScopesArguments
import com.frenchef.intellijlsp.dap.model.ScopesResponseBody
import com.google.gson.JsonElement

/**
 * Handler for the DAP "scopes" request.
 */
class ScopesHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<ScopesArguments>(arguments, DapCommands.SCOPES)
        val scopes = backend.getScopes(args.frameId)
        return DapGson.instance.toJsonTree(ScopesResponseBody(scopes))
    }
}
