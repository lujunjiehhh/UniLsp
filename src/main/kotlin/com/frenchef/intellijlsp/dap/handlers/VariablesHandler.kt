package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.VariablesArguments
import com.frenchef.intellijlsp.dap.model.VariablesResponseBody
import com.google.gson.JsonElement

/**
 * Handler for the DAP "variables" request.
 */
class VariablesHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<VariablesArguments>(arguments, DapCommands.VARIABLES)
        val variables = backend.getVariables(
            variablesReference = args.variablesReference,
            filter = args.filter,
            start = args.start,
            count = args.count,
            format = args.format
        )
        return DapGson.instance.toJsonTree(VariablesResponseBody(variables))
    }
}
