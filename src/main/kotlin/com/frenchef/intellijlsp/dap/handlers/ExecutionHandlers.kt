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
 * Handler for 'continue' request.
 * 
 * Resumes execution of the debuggee.
 * 
 * User Story: US3 - 执行控制
 * Task: T040 - Implement continue request handling
 */
class ContinueHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<ContinueHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing continue request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, ContinueArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse continue arguments", e)
                throw DapErrors.invalidArguments("Invalid continue arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Continue arguments are required")
        }
        
        log.info("Continue request: threadId=${args.threadId}, singleThread=${args.singleThread}")
        
        // Continue execution via backend
        val allThreadsContinued = try {
            runBlocking {
                backend.continueExecution(args.threadId, args.singleThread ?: false)
            }
        } catch (e: Exception) {
            log.error("Failed to continue execution", e)
            throw DapErrors.internalError("Failed to continue: ${e.message}")
        }
        
        log.info("Continue successful, allThreadsContinued=$allThreadsContinued")
        
        // Build response
        val responseBody = ContinueResponseBody(allThreadsContinued = allThreadsContinued)
        
        return gson.toJsonTree(responseBody)
    }
}

/**
 * Handler for 'next' request (step over).
 * 
 * Steps to the next line in the current function.
 * 
 * User Story: US3 - 执行控制
 * Task: T041 - Implement step over (next) handling
 */
class NextHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<NextHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing next request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, NextArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse next arguments", e)
                throw DapErrors.invalidArguments("Invalid next arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Next arguments are required")
        }
        
        log.info("Next request: threadId=${args.threadId}, granularity=${args.granularity}")
        
        // Step next via backend
        try {
            runBlocking {
                backend.next(args.threadId, args.granularity)
            }
        } catch (e: Exception) {
            log.error("Failed to step next", e)
            throw DapErrors.internalError("Failed to step next: ${e.message}")
        }
        
        log.info("Next successful")
        
        // No response body
        return null
    }
}

/**
 * Handler for 'stepIn' request.
 * 
 * Steps into a function call.
 * 
 * User Story: US3 - 执行控制
 * Task: T042 - Implement step into handling
 */
class StepInHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<StepInHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing stepIn request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, StepInArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse stepIn arguments", e)
                throw DapErrors.invalidArguments("Invalid stepIn arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("StepIn arguments are required")
        }
        
        log.info("StepIn request: threadId=${args.threadId}, targetId=${args.targetId}, granularity=${args.granularity}")
        
        // Step in via backend
        try {
            runBlocking {
                backend.stepIn(args.threadId, args.targetId, args.granularity)
            }
        } catch (e: Exception) {
            log.error("Failed to step in", e)
            throw DapErrors.internalError("Failed to step in: ${e.message}")
        }
        
        log.info("StepIn successful")
        
        // No response body
        return null
    }
}

/**
 * Handler for 'stepOut' request.
 * 
 * Steps out of the current function.
 * 
 * User Story: US3 - 执行控制
 * Task: T043 - Implement step out handling
 */
class StepOutHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<StepOutHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing stepOut request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, StepOutArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse stepOut arguments", e)
                throw DapErrors.invalidArguments("Invalid stepOut arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("StepOut arguments are required")
        }
        
        log.info("StepOut request: threadId=${args.threadId}, granularity=${args.granularity}")
        
        // Step out via backend
        try {
            runBlocking {
                backend.stepOut(args.threadId, args.granularity)
            }
        } catch (e: Exception) {
            log.error("Failed to step out", e)
            throw DapErrors.internalError("Failed to step out: ${e.message}")
        }
        
        log.info("StepOut successful")
        
        // No response body
        return null
    }
}

/**
 * Handler for 'pause' request.
 * 
 * Pauses execution of the debuggee.
 * 
 * User Story: US3 - 执行控制
 * Task: T044 - Implement pause handling
 */
class PauseHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<PauseHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logInfo("Processing pause request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, PauseArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse pause arguments", e)
                throw DapErrors.invalidArguments("Invalid pause arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Pause arguments are required")
        }
        
        log.info("Pause request: threadId=${args.threadId}")
        
        // Pause via backend
        try {
            runBlocking {
                backend.pause(args.threadId)
            }
        } catch (e: Exception) {
            log.error("Failed to pause", e)
            throw DapErrors.internalError("Failed to pause: ${e.message}")
        }
        
        log.info("Pause successful")
        
        // No response body
        return null
    }
}
