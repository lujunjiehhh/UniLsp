package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.model.Capabilities
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.InitializeRequestArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "initialize" request.
 */
class InitializeHandler(private val session: DapSession) : DapRequestHandler {

    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<InitializeRequestArguments>(arguments, DapCommands.INITIALIZE)
        if (!session.onInitializeRequest(args)) {
            throw DapErrors.alreadyInitialized()
        }

        val capabilities = buildServerCapabilities()
        if (!session.onInitializeComplete(capabilities)) {
            throw DapErrors.internalError("Initialize state transition failed")
        }

        return DapGson.instance.toJsonTree(capabilities)
    }

    private fun buildServerCapabilities(): Capabilities {
        return Capabilities(
            supportsConfigurationDoneRequest = true,
            supportsTerminateRequest = true
        )
    }
}
