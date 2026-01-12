package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.config.TransportMode
import com.frenchef.intellijlsp.dap.config.DapSettings
import com.frenchef.intellijlsp.dap.services.DapProjectService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity that automatically starts the DAP server when a project opens.
 */
class DapServerStartupActivity : ProjectActivity {
    private val log = logger<DapServerStartupActivity>()

    override suspend fun execute(project: Project) {
        val settings = DapSettings.getInstance()
        if (!settings.autoStart) {
            log.info("DAP auto-start is disabled for project: ${project.name}")
            return
        }

        val service = DapProjectService.getInstance(project)
        if (service.isServerRunning()) {
            log.info("DAP server already running for project: ${project.name}")
            return
        }

        log.info("Starting DAP server for project: ${project.name}")

        val started = when (settings.transportMode) {
            TransportMode.TCP -> service.startTcp(settings.startingPort)
            TransportMode.UDS -> service.startUds()
        }
        if (!started) {
            log.warn("Failed to start DAP server for project: ${project.name}")
            return
        }

        val info = service.getServerPort()?.let { "TCP port $it" }
            ?: service.getSocketPath()?.let { "Unix socket $it" }
            ?: "unknown transport"
        log.info("DAP server started for project ${project.name} on $info")
    }
}
