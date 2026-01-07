package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.StepOutArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "stepOut" request.
 */
class StepOutHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<StepOutArguments>(arguments, DapCommands.STEP_OUT)
        backend.stepOut(args.threadId, args.granularity)
        return null
    }
}
