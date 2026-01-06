package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.model.*
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger

/**
 * Handler for 'initialize' request.
 * 
 * This is the first request sent by the client to configure the debug adapter
 * with client capabilities and to retrieve server capabilities.
 * 
 * User Story: US1 - VSCode 基础调试会话
 * Task: T020 - Implement initialize handling and capabilities response
 */
class InitializeHandler(
    private val session: DapSession
) : DapRequestHandler {
    
    private val log = logger<InitializeHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing initialize request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, InitializeRequestArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse initialize arguments", e)
                throw DapErrors.invalidArguments("Invalid initialize arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Initialize arguments are required")
        }
        
        log.info("Initialize request from client: ${args.clientName} (${args.clientId}), adapter=${args.adapterId}")
        log.info("Client capabilities: linesStartAt1=${args.linesStartAt1}, columnsStartAt1=${args.columnsStartAt1}")
        
        // Transition session state
        if (!session.onInitializeRequest(args)) {
            throw DapErrors.alreadyInitialized()
        }
        
        // Build server capabilities
        val capabilities = buildCapabilities()
        
        // Complete initialization
        if (!session.onInitializeComplete(capabilities)) {
            throw DapErrors.internalError("Failed to complete initialization")
        }
        
        log.info("Initialize complete, returning capabilities")
        
        // Return capabilities
        return gson.toJsonTree(capabilities)
    }
    
    /**
     * Build server capabilities based on what we support.
     */
    private fun buildCapabilities(): Capabilities {
        return Capabilities(
            // Configuration
            supportsConfigurationDoneRequest = true,
            
            // Breakpoints
            supportsFunctionBreakpoints = false, // TODO: Implement in future
            supportsConditionalBreakpoints = true,
            supportsHitConditionalBreakpoints = false, // TODO: Implement in future
            supportsLogPoints = false, // TODO: Implement in future
            
            // Exception breakpoints
            exceptionBreakpointFilters = listOf(
                ExceptionBreakpointsFilter(
                    filter = "all",
                    label = "All Exceptions",
                    description = "Break on all exceptions",
                    default = false
                ),
                ExceptionBreakpointsFilter(
                    filter = "uncaught",
                    label = "Uncaught Exceptions",
                    description = "Break on uncaught exceptions",
                    default = true
                )
            ),
            
            // Evaluation
            supportsEvaluateForHovers = true,
            supportsClipboardContext = false,
            
            // Stepping
            supportsStepBack = false,
            supportsStepInTargetsRequest = false,
            supportsSteppingGranularity = true,
            
            // Variables
            supportsSetVariable = false, // TODO: Implement in future
            supportsSetExpression = false, // TODO: Implement in future
            supportsValueFormattingOptions = true,
            
            // Stack frames
            supportsDelayedStackTraceLoading = true,
            
            // Threads
            supportsSingleThreadExecutionRequests = false,
            supportsTerminateThreadsRequest = false,
            
            // Session control
            supportsRestartRequest = false,
            supportsRestartFrame = false,
            supportsTerminateRequest = true,
            supportTerminateDebuggee = true,
            supportSuspendDebuggee = false,
            
            // Advanced features (not implemented in MVP)
            supportsGotoTargetsRequest = false,
            supportsCompletionsRequest = false,
            supportsModulesRequest = false,
            supportsExceptionOptions = false,
            supportsExceptionInfoRequest = false,
            supportsDataBreakpoints = false,
            supportsReadMemoryRequest = false,
            supportsWriteMemoryRequest = false,
            supportsDisassembleRequest = false,
            supportsCancelRequest = false,
            supportsBreakpointLocationsRequest = false,
            supportsInstructionBreakpoints = false,
            supportsExceptionFilterOptions = false,
            supportsLoadedSourcesRequest = false,
            
            // UI hints
            supportsANSIStyling = false
        )
    }
}
