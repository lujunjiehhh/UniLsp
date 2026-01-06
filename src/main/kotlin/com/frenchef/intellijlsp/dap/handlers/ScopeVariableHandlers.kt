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
 * Handler for 'scopes' request.
 * 
 * Returns the scopes for a stack frame.
 * 
 * User Story: US4 - 堆栈和变量检查
 * Task: T052 - Implement scopes request handling
 */
class ScopesHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<ScopesHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logDebug("Processing scopes request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, ScopesArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse scopes arguments", e)
                throw DapErrors.invalidArguments("Invalid scopes arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Scopes arguments are required")
        }
        
        log.debug("Scopes request: frameId=${args.frameId}")
        
        // Get scopes from backend
        val scopes = try {
            runBlocking {
                backend.getScopes(args.frameId)
            }
        } catch (e: Exception) {
            log.error("Failed to get scopes", e)
            throw DapErrors.internalError("Failed to get scopes: ${e.message}")
        }
        
        log.debug("Retrieved ${scopes.size} scopes")
        
        // Build response
        val responseBody = ScopesResponseBody(scopes = scopes)
        
        return gson.toJsonTree(responseBody)
    }
}

/**
 * Handler for 'variables' request.
 * 
 * Returns the variables for a scope or structured variable.
 * 
 * User Story: US4 - 堆栈和变量检查
 * Task: T053 - Implement variables request handling
 */
class VariablesHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<VariablesHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logDebug("Processing variables request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, VariablesArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse variables arguments", e)
                throw DapErrors.invalidArguments("Invalid variables arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Variables arguments are required")
        }
        
        log.debug("Variables request: variablesReference=${args.variablesReference}, filter=${args.filter}")
        
        // Get variables from backend
        val variables = try {
            runBlocking {
                backend.getVariables(
                    args.variablesReference,
                    args.filter,
                    args.start,
                    args.count,
                    args.format
                )
            }
        } catch (e: Exception) {
            log.error("Failed to get variables", e)
            throw DapErrors.internalError("Failed to get variables: ${e.message}")
        }
        
        log.debug("Retrieved ${variables.size} variables")
        
        // Build response
        val responseBody = VariablesResponseBody(variables = variables)
        
        return gson.toJsonTree(responseBody)
    }
}

/**
 * Handler for 'evaluate' request.
 * 
 * Evaluates an expression in the context of a stack frame.
 * 
 * User Story: US4 - 堆栈和变量检查
 * Task: T054 - Implement evaluate request handling
 */
class EvaluateHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {
    
    private val log = logger<EvaluateHandler>()
    private val gson = DapGson.instance
    
    override fun handle(arguments: JsonElement?): JsonElement? {
        DapErrors.logDebug("Processing evaluate request")
        
        // Parse arguments
        val args = if (arguments != null && !arguments.isJsonNull) {
            try {
                gson.fromJson(arguments, EvaluateArguments::class.java)
            } catch (e: Exception) {
                log.error("Failed to parse evaluate arguments", e)
                throw DapErrors.invalidArguments("Invalid evaluate arguments: ${e.message}")
            }
        } else {
            throw DapErrors.invalidArguments("Evaluate arguments are required")
        }
        
        log.debug("Evaluate request: expression='${args.expression}', frameId=${args.frameId}, context=${args.context}")
        
        // Evaluate expression via backend
        val result = try {
            runBlocking {
                backend.evaluate(args.expression, args.frameId, args.context, args.format)
            }
        } catch (e: Exception) {
            log.error("Failed to evaluate expression", e)
            throw DapErrors.evaluateError(args.expression, e.message ?: "Unknown error")
        }
        
        log.debug("Evaluate result: ${result.result}")
        
        return gson.toJsonTree(result)
    }
}
