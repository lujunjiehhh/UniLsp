package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.StepInArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "stepIn" request.
 */
class StepInHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<StepInArguments>(arguments, DapCommands.STEP_IN)
        backend.stepIn(args.threadId, args.targetId, args.granularity)
        return null
    }
}
