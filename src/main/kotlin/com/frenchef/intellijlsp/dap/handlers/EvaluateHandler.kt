package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.EvaluateArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "evaluate" request.
 */
class EvaluateHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<EvaluateArguments>(arguments, DapCommands.EVALUATE)
        val result = backend.evaluate(
            expression = args.expression,
            frameId = args.frameId,
            context = args.context,
            format = args.format
        )
        return DapGson.instance.toJsonTree(result)
    }
}
