package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.ExceptionInfoArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "exceptionInfo" request.
 */
class ExceptionInfoHandler(private val backend: DebuggerBackend) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<ExceptionInfoArguments>(arguments, DapCommands.EXCEPTION_INFO)
        return DapGson.instance.toJsonTree(backend.getExceptionInfo(args.threadId))
    }
}
