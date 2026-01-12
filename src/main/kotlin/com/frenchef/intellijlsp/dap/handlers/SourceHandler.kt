package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.SourceArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "source" request.
 */
class SourceHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<SourceArguments>(arguments, DapCommands.SOURCE)
        val body = backend.getSource(args.source, args.sourceReference)
        return DapGson.instance.toJsonTree(body)
    }
}
