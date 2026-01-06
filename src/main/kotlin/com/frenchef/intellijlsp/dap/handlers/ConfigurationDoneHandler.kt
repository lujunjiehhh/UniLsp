package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapSession
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger

/**
 * Handler for 'configurationDone' request.
 * 
 * Indicates that the client has finished sending configuration requests
 * (like setBreakpoints, setExceptionBreakpoints) and the debuggee can start running.
 * 
 * User Story: US1 - VSCode 基础调试会话
 * Task: T023 - Implement configurationDone request handling
 */
class ConfigurationDoneHandler(
    private val session: DapSession
) : DapRequestHandler {
    
    private val log = logger<ConfigurationDoneHandler>()
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing configurationDone request")
        
        // Transition session state to RUNNING
        if (!session.onConfigurationDone()) {
            throw DapErrors.internalError("Failed to complete configuration")
        }
        
        log.info("Configuration done, session is now RUNNING")
        
        // No response body
        return null
    }
}
