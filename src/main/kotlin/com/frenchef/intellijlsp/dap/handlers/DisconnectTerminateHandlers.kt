package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.DisconnectArguments
import com.frenchef.intellijlsp.dap.model.TerminateArguments
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.runBlocking

/**
 * Handler for 'disconnect' request.
 * 
 * Disconnects from the debuggee and optionally terminates it.
 * 
 * User Story: US1 - VSCode 基础调试会话
 * Task: T024 - Implement disconnect/terminate handling
 */
class DisconnectHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<DisconnectHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing disconnect request")
        
        // Parse arguments (optional)
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, DisconnectArguments::class.java)
            } catch (e: Exception) {
                log.warn("Failed to parse disconnect arguments, using defaults", e)
                DisconnectArguments()
            }
        } else {
            DisconnectArguments()
        }
        
        val terminateDebuggee = args.terminateDebuggee ?: true
        
        log.info("Disconnect request: terminateDebuggee=$terminateDebuggee, restart=${args.restart}")
        
        // Disconnect from debuggee
        try {
            runBlocking {
                backend.disconnect(terminateDebuggee)
            }
        } catch (e: Exception) {
            log.error("Error during disconnect", e)
            // Don't throw - best effort disconnect
        }
        
        // Transition session state
        session.onTerminated()
        
        log.info("Disconnect complete")
        
        // No response body
        return null
    }
}

/**
 * Handler for 'terminate' request.
 * 
 * Terminates the debuggee.
 * 
 * User Story: US1 - VSCode 基础调试会话
 * Task: T024 - Implement disconnect/terminate handling
 */
class TerminateHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<TerminateHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing terminate request")
        
        // Parse arguments (optional)
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, TerminateArguments::class.java)
            } catch (e: Exception) {
                log.warn("Failed to parse terminate arguments, using defaults", e)
                TerminateArguments()
            }
        } else {
            TerminateArguments()
        }
        
        log.info("Terminate request: restart=${args.restart}")
        
        // Terminate debuggee
        try {
            runBlocking {
                backend.terminate()
            }
        } catch (e: Exception) {
            log.error("Error during terminate", e)
            // Don't throw - best effort terminate
        }
        
        // Transition session state
        session.onTerminated()
        
        log.info("Terminate complete")
        
        // No response body
        return null
    }
}
