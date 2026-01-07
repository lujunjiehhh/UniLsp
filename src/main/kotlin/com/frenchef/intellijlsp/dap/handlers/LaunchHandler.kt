package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.LaunchRequestArguments
import com.google.gson.JsonElement
import kotlinx.coroutines.runBlocking

/**
 * Handler for the DAP "launch" request.
 */
class LaunchHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    private val gson = DapGson.instance

    override fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments(arguments)
        val success = runBlocking {
            backend.launch(args)
        }
        if (!success) {
            throw DapErrors.launchError("backend returned false")
        }

        return null
    }

    private fun parseArguments(arguments: JsonElement?): LaunchRequestArguments {
        if (arguments == null || arguments.isJsonNull) {
            throw DapErrors.invalidArguments("launch requires arguments")
        }

        return gson.fromJson(arguments, LaunchRequestArguments::class.java)
    }
}
