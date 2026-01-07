package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.model.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger

/**
 * DAP Request Router
 * 
 * Routes incoming DAP requests to appropriate handlers and manages response generation.
 * Also handles state validation before dispatching requests.
 */
class DapRequestRouter(private val session: DapSession) {
    
    private val log = logger<DapRequestRouter>()
    private val gson = DapGson.instance
    
    // Handler registry
    private val requestHandlers = mutableMapOf<String, DapRequestHandler>()
    
    /**
     * Register a request handler for a specific command.
     */
    fun registerHandler(command: String, handler: DapRequestHandler) {
        requestHandlers[command] = handler
        log.debug("Registered handler for command: $command")
    }
    
    /**
     * Handle an incoming DAP message.
     * 
     * @param json The raw JSON message
     * @return A response message, or null for events/notifications
     */
    fun handleMessage(json: JsonObject): DapResponse? {
        val type = json.get("type")?.asString
        
        return when (type) {
            "request" -> handleRequest(json)
            "response" -> {
                // Handle responses to requests we sent (reverse requests)
                handleResponse(json)
                null
            }
            "event" -> {
                // Events from client (rare, but possible)
                log.debug("Received event from client: ${json.get("event")?.asString}")
                null
            }
            else -> {
                log.warn("Unknown message type: $type")
                null
            }
        }
    }
    
    /**
     * Handle a DAP request and return a response.
     */
    private fun handleRequest(json: JsonObject): DapResponse {
        val seq = json.get("seq")?.asInt ?: 0
        val command = json.get("command")?.asString ?: "unknown"
        val arguments = json.get("arguments")
        
        log.info("Handling request: command=$command, seq=$seq")
        
        // Validate session state
        val stateError = validateState(command)
        if (stateError != null) {
            return createErrorResponse(seq, command, stateError.first, stateError.second)
        }
        
        // Find handler
        val handler = requestHandlers[command]
        if (handler == null) {
            log.warn("No handler registered for command: $command")
            return createErrorResponse(
                seq, command,
                "Unknown command: $command",
                DapErrorId.UNKNOWN_COMMAND
            )
        }
        
        // Execute handler
        return try {
            val result = handler.handle(arguments)
            createSuccessResponse(seq, command, result)
        } catch (e: DapException) {
            log.warn("DAP exception handling $command: ${e.message}")
            createErrorResponse(seq, command, e.message ?: "Unknown error", e.errorId)
        } catch (e: Exception) {
            log.error("Error handling $command", e)
            createErrorResponse(
                seq, command,
                "Internal error: ${e.message}",
                DapErrorId.INTERNAL_ERROR
            )
        }
    }
    
    /**
     * Handle a response to a request we sent.
     */
    private fun handleResponse(json: JsonObject) {
        val requestSeq = json.get("request_seq")?.asInt
        val success = json.get("success")?.asBoolean ?: false
        val command = json.get("command")?.asString
        
        log.debug("Received response for request $requestSeq ($command): success=$success")
        // TODO: Implement pending request management for reverse requests
    }
    
    /**
     * Validate session state for the given command.
     * 
     * @return Pair of (error message, error id) if validation fails, null if OK
     */
    private fun validateState(command: String): Pair<String, Int>? {
        val state = session.getState()
        
        // Initialize is always allowed in UNINITIALIZED state
        if (command == "initialize") {
            if (state != DapSession.State.UNINITIALIZED) {
                return "Session already initialized" to DapErrorId.ALREADY_INITIALIZED
            }
            return null
        }
        
        // All other commands require initialization
        if (state == DapSession.State.UNINITIALIZED || state == DapSession.State.INITIALIZING) {
            return "Session not initialized" to DapErrorId.NOT_INITIALIZED
        }
        
        // Check for terminated state
        if (state == DapSession.State.TERMINATED) {
            return "Session terminated" to DapErrorId.SESSION_TERMINATED
        }
        
        // Commands that require stopped state
        val stoppedOnlyCommands = setOf(
            "stackTrace", "scopes", "variables", "evaluate",
            "setExpression", "source", "exceptionInfo"
        )
        
        if (command in stoppedOnlyCommands && state != DapSession.State.STOPPED) {
            return "notStopped" to DapErrorId.NOT_STOPPED
        }
        
        return null
    }
    
    /**
     * Create a success response.
     */
    private fun createSuccessResponse(requestSeq: Int, command: String, body: JsonElement?): DapResponse {
        return DapResponse(
            seq = session.nextSeq(),
            requestSeq = requestSeq,
            success = true,
            command = command,
            body = body
        )
    }
    
    /**
     * Create an error response.
     */
    private fun createErrorResponse(
        requestSeq: Int,
        command: String,
        message: String,
        errorId: Int
    ): DapResponse {
        val errorBody = DapErrorBody(
            error = DapErrorMessage(
                id = errorId,
                format = message,
                showUser = true
            )
        )
        
        return DapResponse(
            seq = session.nextSeq(),
            requestSeq = requestSeq,
            success = false,
            command = command,
            message = message,
            body = gson.toJsonTree(errorBody)
        )
    }
}

/**
 * Interface for DAP request handlers.
 */
fun interface DapRequestHandler {
    /**
     * Handle a DAP request.
     * 
     * @param arguments The request arguments (may be null)
     * @return The response body as JsonElement
     * @throws DapException if there's a protocol error
     */
    fun handle(arguments: JsonElement?): JsonElement?
}
