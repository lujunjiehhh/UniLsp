package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.model.Capabilities
import com.frenchef.intellijlsp.dap.model.InitializeRequestArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "initialize" request.
 */
class InitializeHandler(private val session: DapSession) : DapRequestHandler {
    private val gson = DapGson.instance

    override fun handle(arguments: JsonElement?): JsonElement? {
        if (arguments == null || arguments.isJsonNull) {
            throw DapErrors.invalidArguments("initialize requires arguments")
        }

        val args = gson.fromJson(arguments, InitializeRequestArguments::class.java)

        if (!session.onInitializeRequest(args)) {
            throw DapErrors.alreadyInitialized()
        }

        val capabilities = Capabilities(
            supportsConfigurationDoneRequest = true,
            supportsTerminateRequest = true
        )

        session.onInitializeComplete(capabilities)

        return gson.toJsonTree(capabilities)
    }
}
