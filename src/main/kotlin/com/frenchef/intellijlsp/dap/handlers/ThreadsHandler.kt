package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.ThreadsResponseBody
import com.google.gson.JsonElement

/**
 * Handler for the DAP "threads" request.
 */
class ThreadsHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val threads = backend.getThreads()
        return DapGson.instance.toJsonTree(ThreadsResponseBody(threads))
    }
}
