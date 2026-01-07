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
    /**
     * Finalizes configuration for the current DAP session in response to a `configurationDone` request.
     *
     * @param arguments Optional request arguments; this handler ignores any provided payload.
     * @throws Throwable when configuration cannot be completed — an internal DAP error is raised with the current session state.
     * @return `null` on success.
     */
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        if (!session.onConfigurationDone()) {
            throw DapErrors.internalError(
                "configurationDone request not allowed in current session state: ${session.getState()}"
            )
        }

        return null
    }
}