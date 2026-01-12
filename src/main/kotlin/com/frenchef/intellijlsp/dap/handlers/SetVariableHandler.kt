package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.SetVariableArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "setVariable" request.
 */
class SetVariableHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<SetVariableArguments>(arguments, DapCommands.SET_VARIABLE)
        val body = backend.setVariable(
            variablesReference = args.variablesReference,
            name = args.name,
            value = args.value,
            format = args.format
        )
        return DapGson.instance.toJsonTree(body)
    }
}
