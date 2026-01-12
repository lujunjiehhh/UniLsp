package com.frenchef.intellijlsp

import com.frenchef.intellijlsp.config.LspSettings
import com.frenchef.intellijlsp.handlers.LifecycleHandler
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.server.LspServerManager
import com.frenchef.intellijlsp.services.LspProjectService
import com.frenchef.intellijlsp.util.LspLogger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Startup activity that automatically starts the LSP server when a project opens. */
class LspServerStartupActivity : ProjectActivity {
    private val log = logger<LspServerStartupActivity>()

    override suspend fun execute(project: Project) {
        val settings = LspSettings.getInstance()

        if (!settings.autoStart) {
            log.info("Auto-start is disabled for project: ${project.name}")
            return
        }

        log.info("Starting LSP server for project: ${project.name}")

        // Initialize LSP Logger for debugging
        LspLogger.init(project)
        LspLogger.info("Startup", "Starting LSP server for project: ${project.name}")

        try {
            // Create JSON-RPC handler for this project
            val jsonRpcHandler = JsonRpcHandler(project)

            // Create and start the server
            val server = LspServerManager.createAndStartServer(project, jsonRpcHandler)

            if (server == null) {
                log.error("Failed to create LSP server for project: ${project.name}")
                return
            }

            // Register all handlers via registry
            com.frenchef.intellijlsp.handlers.LspHandlerRegistry.registerAll(project, jsonRpcHandler, server)

            // Log server info
            val serverInfo = when {
                server.getPort() != null -> "TCP port ${server.getPort()}"
                server.getSocketPath() != null -> "Unix socket ${server.getSocketPath()}"
                else -> "unknown transport"
            }
            log.info("LSP server started for project ${project.name} on $serverInfo")
        } catch (e: Exception) {
            log.error("Error starting LSP server for project: ${project.name}", e)
        }
    }
}
