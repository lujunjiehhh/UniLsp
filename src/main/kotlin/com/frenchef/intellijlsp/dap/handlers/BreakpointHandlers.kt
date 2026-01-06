package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.*
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.runBlocking

/**
 * Handler for 'setBreakpoints' request.
 * 
 * Sets multiple breakpoints for a single source file and clears all previous
 * breakpoints in that file.
 * 
 * User Story: US2 - 断点管理
 * Task: T030 - Implement setBreakpoints request handling
 */
class SetBreakpointsHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<SetBreakpointsHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing setBreakpoints request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, SetBreakpointsArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse setBreakpoints arguments", e)
                throw DapErrors.invalidArguments("Invalid setBreakpoints arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("SetBreakpoints arguments are required")
        }
        
        val sourcePath = args.source.path ?: args.source.name ?: "unknown"
        val breakpointCount = args.breakpoints?.size ?: args.lines?.size ?: 0
        
        log.info("Setting $breakpointCount breakpoints in $sourcePath")
        
        // Convert lines to SourceBreakpoint if needed
        val sourceBreakpoints = when {
            args.breakpoints != null -> args.breakpoints
            args.lines != null -> args.lines.map { line ->
                SourceBreakpoint(line = line)
            }
            else -> emptyList()
        }
        
        // Set breakpoints via backend
        val breakpoints = try {
            runBlocking {
                backend.setBreakpoints(args.source, sourceBreakpoints)
            }
        } catch (e: Exception) {
            log.error("Failed to set breakpoints", e)
            throw DapErrors.internalError("Failed to set breakpoints: ${e.message}")
        }
        
        log.info("Set ${breakpoints.size} breakpoints, ${breakpoints.count { it.verified }} verified")
        
        // Build response
        val responseBody = SetBreakpointsResponseBody(breakpoints = breakpoints)
        
        return gson.toJsonTree(responseBody)
    }
}

/**
 * Handler for 'setFunctionBreakpoints' request.
 * 
 * Sets multiple function breakpoints and clears all previous function breakpoints.
 * 
 * User Story: US2 - 断点管理
 * Task: T031 - Implement function breakpoints (optional)
 */
class SetFunctionBreakpointsHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<SetFunctionBreakpointsHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing setFunctionBreakpoints request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, SetFunctionBreakpointsArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse setFunctionBreakpoints arguments", e)
                throw DapErrors.invalidArguments("Invalid setFunctionBreakpoints arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("SetFunctionBreakpoints arguments are required")
        }
        
        log.info("Setting ${args.breakpoints.size} function breakpoints")
        
        // Set function breakpoints via backend
        val breakpoints = try {
            runBlocking {
                backend.setFunctionBreakpoints(args.breakpoints)
            }
        } catch (e: Exception) {
            log.error("Failed to set function breakpoints", e)
            throw DapErrors.internalError("Failed to set function breakpoints: ${e.message}")
        }
        
        log.info("Set ${breakpoints.size} function breakpoints")
        
        // Build response
        val responseBody = SetFunctionBreakpointsResponseBody(breakpoints = breakpoints)
        
        return gson.toJsonTree(responseBody)
    }
}

/**
 * Handler for 'setExceptionBreakpoints' request.
 * 
 * Sets the exception breakpoints for the debug session.
 * 
 * User Story: US2 - 断点管理
 * Task: T032 - Implement exception breakpoints
 */
class SetExceptionBreakpointsHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<SetExceptionBreakpointsHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing setExceptionBreakpoints request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, SetExceptionBreakpointsArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse setExceptionBreakpoints arguments", e)
                throw DapErrors.invalidArguments("Invalid setExceptionBreakpoints arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("SetExceptionBreakpoints arguments are required")
        }
        
        log.info("Setting exception breakpoints: filters=${args.filters}")
        
        // Set exception breakpoints via backend
        val breakpoints = try {
            runBlocking {
                backend.setExceptionBreakpoints(args.filters, args.filterOptions)
            }
        } catch (e: Exception) {
            log.error("Failed to set exception breakpoints", e)
            throw DapErrors.internalError("Failed to set exception breakpoints: ${e.message}")
        }
        
        log.info("Set exception breakpoints")
        
        // Build response
        val responseBody = SetExceptionBreakpointsResponseBody(breakpoints = breakpoints)
        
        return gson.toJsonTree(responseBody)
    }
}
