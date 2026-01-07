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
     * Route a raw DAP JSON message to the appropriate handler based on its `"type"` field.
     *
     * @param json The DAP message as a JsonObject; expected to contain a `"type"` property (e.g., "request", "response", "event").
     * @return A DapResponse for handled requests, or `null` for responses, events, unknown types, or when no response should be sent.
     */
    suspend fun handleMessage(json: JsonObject): DapResponse? {
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
     * Routes a single DAP request JSON to the registered handler after validating session state.
     *
     * Validates the session state for the request's command and, if valid, invokes the registered handler
     * with the request arguments. Produces a success response containing the handler result, or an error
     * response describing validation failures, unknown commands, DAP-level errors, or internal errors.
     *
     * @param json The raw DAP request JSON object containing at minimum `seq`, `command`, and optional `arguments`.
     * @return A DapResponse representing either the handler's successful response body or an error response with a DAP error id.
     */
    private suspend fun handleRequest(json: JsonObject): DapResponse {
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
     * Check whether the session's current state permits executing the given DAP command.
     *
     * Special cases:
     * - `initialize` is only permitted when the session is UNINITIALIZED.
     * - All other commands require the session to be initialized (not UNINITIALIZED or INITIALIZING).
     * - Commands are rejected when the session is TERMINATED.
     * - Certain commands (stackTrace, scopes, variables, evaluate, setExpression, source, exceptionInfo)
     *   require the session to be in the STOPPED state.
     *
     * @param command The DAP request command name to validate.
     * @return A `Pair<String, Int>` with a human-readable error message and DAP error id if validation fails, or `null` if the command is allowed.
     */
    private fun validateState(command: String): Pair<String, Int>? {
        val state = session.getState()
        
        // Initialize is always allowed in UNINITIALIZED state
        if (command == DapCommands.INITIALIZE) {
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
    /**
 * Handles a DAP request and produces the response body for that request.
 *
 * @param arguments The request arguments as JSON, or `null` if the request has no arguments.
 * @return A `JsonElement` to use as the response body, or `null` if no body should be returned.
 * @throws DapException To signal protocol-level errors or terminal failures.
 */
suspend fun handle(arguments: JsonElement?): JsonElement?
}