package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.ModulesArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "modules" request.
 */
class ModulesHandler(private val backend: DebuggerBackend) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseOptionalArguments<ModulesArguments>(arguments, DapCommands.MODULES)
        return DapGson.instance.toJsonTree(backend.getModules(args.startModule, args.moduleCount))
    }
}
