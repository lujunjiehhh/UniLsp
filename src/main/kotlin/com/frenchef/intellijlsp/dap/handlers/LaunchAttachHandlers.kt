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
 * Handler for 'launch' request.
 * 
 * Starts debugging by launching a new debuggee process.
 * 
 * User Story: US1 - VSCode 基础调试会话
 * Task: T022 - Implement launch/attach request handling
 */
class LaunchHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<LaunchHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing launch request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, LaunchRequestArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse launch arguments", e)
                throw DapErrors.invalidArguments("Invalid launch arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Launch arguments are required")
        }
        
        log.info("Launch request: mainClass=${args.mainClass}, program=${args.program}, noDebug=${args.noDebug}")
        
        // Launch the debuggee
        val success = try {
            runBlocking {
                backend.launch(args)
            }
        } catch (e: Exception) {
            log.error("Failed to launch debuggee", e)
            throw DapErrors.launchError(e.message ?: "Unknown error")
        }
        
        if (!success) {
            throw DapErrors.launchError("Backend returned false")
        }
        
        log.info("Launch successful")
        
        // No response body for launch
        return null
    }
}

/**
 * Handler for 'attach' request.
 * 
 * Starts debugging by attaching to an existing debuggee process.
 * 
 * User Story: US1 - VSCode 基础调试会话
 * Task: T022 - Implement launch/attach request handling
 */
class AttachHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<AttachHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing attach request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, AttachRequestArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse attach arguments", e)
                throw DapErrors.invalidArguments("Invalid attach arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Attach arguments are required")
        }
        
        log.info("Attach request: host=${args.hostName}, port=${args.port}, pid=${args.processId}")
        
        // Attach to the debuggee
        val success = try {
            runBlocking {
                backend.attach(args)
            }
        } catch (e: Exception) {
            log.error("Failed to attach to debuggee", e)
            throw DapErrors.attachError(e.message ?: "Unknown error")
        }
        
        if (!success) {
            throw DapErrors.attachError("Backend returned false")
        }
        
        log.info("Attach successful")
        
        // No response body for attach
        return null
    }
}
