package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapSession
import com.google.gson.JsonElement

/**
 * Handler for the DAP "configurationDone" request.
 *
 * This request is valid after the client finishes setting breakpoints and other
 * configuration, and before launch/attach moves the session into running state.
 * It advances the session from CONFIGURING to RUNNING.
 */
class ConfigurationDoneHandler(
    private val session: DapSession
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        if (!session.onConfigurationDone()) {
            throw DapErrors.internalError(
                "configurationDone request not allowed in current session state: ${session.getState()}"
            )
        }

        return null
    }
}
