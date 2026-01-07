package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.DisconnectArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "disconnect" request.
 */
class DisconnectHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseOptionalArguments<DisconnectArguments>(arguments, DapCommands.DISCONNECT)
        backend.disconnect(args.terminateDebuggee == true)
        if (!session.onTerminated()) {
            throw DapErrors.internalError("Disconnect state transition failed")
        }

        return null
    }
}
