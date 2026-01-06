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
 * Handler for 'threads' request.
 * 
 * Returns all threads in the debuggee.
 * 
 * User Story: US4 - 堆栈和变量检查
 * Task: T050 - Implement threads request handling
 */
class ThreadsHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<ThreadsHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logDebug("Processing threads request")
        
        // Get threads from backend
        val threads = try {
            runBlocking {
                backend.getThreads()
            }
        } catch (e: Exception) {
            log.error("Failed to get threads", e)
            throw DapErrors.internalError("Failed to get threads: ${e.message}")
        }
        
        log.debug("Retrieved ${threads.size} threads")
        
        // Build response
        val responseBody = ThreadsResponseBody(threads = threads)
        
        return gson.toJsonTree(responseBody)
    }
}

/**
 * Handler for 'stackTrace' request.
 * 
 * Returns the stack trace for a thread.
 * 
 * User Story: US4 - 堆栈和变量检查
 * Task: T051 - Implement stackTrace request handling
 */
class StackTraceHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<StackTraceHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logDebug("Processing stackTrace request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, StackTraceArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse stackTrace arguments", e)
                throw DapErrors.invalidArguments("Invalid stackTrace arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("StackTrace arguments are required")
        }
        
        log.debug("StackTrace request: threadId=${args.threadId}, startFrame=${args.startFrame}, levels=${args.levels}")
        
        // Get stack trace from backend
        val (stackFrames, totalFrames) = try {
            runBlocking {
                backend.getStackTrace(args.threadId, args.startFrame, args.levels, args.format)
            }
        } catch (e: Exception) {
            log.error("Failed to get stack trace", e)
            throw DapErrors.internalError("Failed to get stack trace: ${e.message}")
        }
        
        log.debug("Retrieved ${stackFrames.size} stack frames (total: $totalFrames)")
        
        // Build response
        val responseBody = StackTraceResponseBody(
            stackFrames = stackFrames,
            totalFrames = totalFrames
        )
        
        return gson.toJsonTree(responseBody)
    }
}
