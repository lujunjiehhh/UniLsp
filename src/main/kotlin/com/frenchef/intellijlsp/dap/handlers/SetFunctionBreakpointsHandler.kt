package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.SetFunctionBreakpointsArguments
import com.frenchef.intellijlsp.dap.model.SetFunctionBreakpointsResponseBody
import com.google.gson.JsonElement

/**
 * Handler for the DAP "setFunctionBreakpoints" request.
 */
class SetFunctionBreakpointsHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<SetFunctionBreakpointsArguments>(arguments, DapCommands.SET_FUNCTION_BREAKPOINTS)
        val breakpoints = backend.setFunctionBreakpoints(args.breakpoints)
        return DapGson.instance.toJsonTree(SetFunctionBreakpointsResponseBody(breakpoints))
    }
}
