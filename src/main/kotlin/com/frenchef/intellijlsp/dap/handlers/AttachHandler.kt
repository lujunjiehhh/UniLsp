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
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<AttachRequestArguments>(arguments, DapCommands.ATTACH)
        val success = backend.attach(args)
        if (!success) {
            throw DapErrors.attachError("backend returned false")
        }

        return null
    }
}
