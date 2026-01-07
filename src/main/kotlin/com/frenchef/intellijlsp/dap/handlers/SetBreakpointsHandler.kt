package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.SetBreakpointsArguments
import com.frenchef.intellijlsp.dap.model.SetBreakpointsResponseBody
import com.frenchef.intellijlsp.dap.model.SourceBreakpoint
import com.google.gson.JsonElement

/**
 * Handler for the DAP "setBreakpoints" request.
 */
class SetBreakpointsHandler(
    private val backend: DebuggerBackend
) : DapRequestHandler {
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<SetBreakpointsArguments>(arguments, DapCommands.SET_BREAKPOINTS)
        val requestedBreakpoints = args.breakpoints
            ?: args.lines?.map { line -> SourceBreakpoint(line = line) }
            ?: emptyList()
        val resolvedBreakpoints = backend.setBreakpoints(args.source, requestedBreakpoints)
        return DapGson.instance.toJsonTree(SetBreakpointsResponseBody(resolvedBreakpoints))
    }
}
