package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.TerminateArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "terminate" request.
 */
class TerminateHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        parseOptionalArguments<TerminateArguments>(arguments, DapCommands.TERMINATE)
        backend.terminate()
        if (!session.onTerminated()) {
            throw DapErrors.internalError("Terminate state transition failed")
        }

        return null
    }
}
