package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapSession
import com.google.gson.JsonElement

/**
 * Handler for the DAP "configurationDone" request.
 */
class ConfigurationDoneHandler(
    private val session: DapSession
) : DapRequestHandler {
    override fun handle(arguments: JsonElement?): JsonElement? {
        if (!session.onConfigurationDone()) {
            throw DapErrors.internalError("ConfigurationDone state transition failed")
        }

        return null
    }
}
