package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.AttachRequestArguments
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.google.gson.JsonElement

/**
 * Handler for the DAP "attach" request.
 */
class AttachHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    /**
     * Handle an incoming DAP "attach" request.
     *
     * Parses the provided JSON arguments into AttachRequestArguments and invokes the debugger backend to perform the attach operation.
     *
     * @param arguments JSON payload for the attach request; parsed into an AttachRequestArguments instance.
     * @return `null` on success.
     * @throws DapErrors.attachError if the backend reports failure (returns `false`).
     */
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<AttachRequestArguments>(arguments, DapCommands.ATTACH)
        val success = backend.attach(args)
        if (!success) {
            throw DapErrors.attachError("backend returned false")
        }

        return null
    }
}