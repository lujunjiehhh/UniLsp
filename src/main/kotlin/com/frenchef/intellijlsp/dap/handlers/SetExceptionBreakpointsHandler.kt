package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.SetExceptionBreakpointsArguments
import com.frenchef.intellijlsp.dap.model.SetExceptionBreakpointsResponseBody
import com.google.gson.JsonElement

/**
 * Handler for the DAP "setExceptionBreakpoints" request.
 */
class SetExceptionBreakpointsHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<SetExceptionBreakpointsArguments>(arguments, DapCommands.SET_EXCEPTION_BREAKPOINTS)
        val breakpoints = backend.setExceptionBreakpoints(args.filters, args.filterOptions)
        return DapGson.instance.toJsonTree(SetExceptionBreakpointsResponseBody(breakpoints))
    }
}
