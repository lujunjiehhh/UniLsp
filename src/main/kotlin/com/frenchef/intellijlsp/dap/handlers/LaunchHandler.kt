package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.LaunchRequestArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "launch" request.
 */
class LaunchHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    /**
     * Handles a DAP "launch" request by parsing the incoming JSON into LaunchRequestArguments and invoking the debugger backend's launch.
     *
     * @param arguments Raw JSON arguments for the launch request; may be null.
     * @return `null` on successful launch.
     * @throws DapErrors.launchError if the backend reports failure.
     */
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<LaunchRequestArguments>(arguments, DapCommands.LAUNCH)
        val success = backend.launch(args)
        if (!success) {
            throw DapErrors.launchError("backend returned false")
        }

        return null
    }
}