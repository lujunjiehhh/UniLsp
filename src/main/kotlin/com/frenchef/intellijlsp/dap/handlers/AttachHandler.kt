package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.AttachRequestArguments
import com.google.gson.JsonElement
import kotlinx.coroutines.runBlocking

/**
 * Handler for the DAP "attach" request.
 */
class AttachHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    private val gson = DapGson.instance

    override fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments(arguments)
        val success = runBlocking {
            backend.attach(args)
        }
        if (!success) {
            throw DapErrors.attachError("backend returned false")
        }

        return null
    }

    private fun parseArguments(arguments: JsonElement?): AttachRequestArguments {
        if (arguments == null || arguments.isJsonNull) {
            throw DapErrors.invalidArguments("attach requires arguments")
        }

        return gson.fromJson(arguments, AttachRequestArguments::class.java)
    }
}
