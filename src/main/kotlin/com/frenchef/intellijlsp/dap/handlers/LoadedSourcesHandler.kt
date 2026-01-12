package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.LoadedSourcesArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "loadedSources" request.
 */
class LoadedSourcesHandler(private val backend: DebuggerBackend) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        parseOptionalArguments<LoadedSourcesArguments>(arguments, DapCommands.LOADED_SOURCES)
        return DapGson.instance.toJsonTree(backend.getLoadedSources())
    }
}
