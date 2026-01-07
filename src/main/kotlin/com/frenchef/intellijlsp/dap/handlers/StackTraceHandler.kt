package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.StackTraceArguments
import com.frenchef.intellijlsp.dap.model.StackTraceResponseBody
import com.google.gson.JsonElement

/**
 * Handler for the DAP "stackTrace" request.
 */
class StackTraceHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<StackTraceArguments>(arguments, DapCommands.STACK_TRACE)
        val (frames, totalFrames) = backend.getStackTrace(
            args.threadId,
            args.startFrame,
            args.levels,
            args.format
        )
        return DapGson.instance.toJsonTree(StackTraceResponseBody(frames, totalFrames))
    }
}
