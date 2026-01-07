package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.model.Capabilities
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.InitializeRequestArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "initialize" request.
 */
class InitializeHandler(private val session: DapSession) : DapRequestHandler {

    /**
     * Handles a DAP "initialize" request by processing the incoming arguments, performing session initialization,
     * and returning the server's capabilities as a JSON element.
     *
     * Parses the initialize request, invokes the session's initialization hooks, and converts the resulting
     * capabilities to JSON. Throws if the session is already initialized or if completing initialization fails.
     *
     * @param arguments Raw JSON arguments for the initialize request, or `null` if none were provided.
     * @return A JSON element representing the server capabilities.
     * @throws DapError If the session is already initialized.
     * @throws DapError If completing the initialization state transition fails.
     */
    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<InitializeRequestArguments>(arguments, DapCommands.INITIALIZE)
        if (!session.onInitializeRequest(args)) {
            throw DapErrors.alreadyInitialized()
        }

        val capabilities = buildServerCapabilities()
        if (!session.onInitializeComplete(capabilities)) {
            throw DapErrors.internalError("Initialize state transition failed")
        }

        return DapGson.instance.toJsonTree(capabilities)
    }

    /**
     * Create the server capabilities exposed in response to the DAP initialize request.
     *
     * @return A [Capabilities] instance with `supportsConfigurationDoneRequest` and
     * `supportsTerminateRequest` set to `true`.
     */
    private fun buildServerCapabilities(): Capabilities {
        return Capabilities(
            supportsConfigurationDoneRequest = true,
            supportsTerminateRequest = true
        )
    }
}