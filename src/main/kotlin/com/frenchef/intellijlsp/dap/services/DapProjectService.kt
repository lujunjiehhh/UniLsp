package com.frenchef.intellijlsp.dap.services

import com.frenchef.intellijlsp.dap.DapServerStarter
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Project-level service that manages DAP server lifecycle for a project.
 */
@Service(Service.Level.PROJECT)
class DapProjectService(private val project: Project) : Disposable {
    private val log = logger<DapProjectService>()
    private val serverStarter = DapServerStarter(project)

    companion object {
        fun getInstance(project: Project): DapProjectService {
            return project.getService(DapProjectService::class.java)
        }
    }

    fun startTcp(port: Int = 5005): Boolean {
        log.info("Starting DAP TCP server for project: ${project.name}")
        val started = serverStarter.startTcp(port)
        if (started) {
            DapDiscovery.write(
                project,
                transport = com.frenchef.intellijlsp.config.TransportMode.TCP,
                port = serverStarter.getPort(),
                socketPath = null
            )
        }
        return started
    }

    fun startUds(socketPath: String = defaultSocketPath()): Boolean {
        log.info("Starting DAP UDS server for project: ${project.name}")
        val started = serverStarter.startUds(socketPath)
        if (started) {
            DapDiscovery.write(
                project,
                transport = com.frenchef.intellijlsp.config.TransportMode.UDS,
                port = null,
                socketPath = serverStarter.getSocketPath()
            )
        }
        return started
    }

    fun stopServer() {
        log.info("Stopping DAP server for project: ${project.name}")
        serverStarter.stop()
        DapDiscovery.clear(project)
    }

    fun isServerRunning(): Boolean = serverStarter.isRunning()

    fun getServerPort(): Int? = serverStarter.getPort()

    fun getSocketPath(): String? = serverStarter.getSocketPath()

    fun getClientCount(): Int = serverStarter.getClientCount()

    override fun dispose() {
        stopServer()
    }

    private fun defaultSocketPath(): String {
        val socketDir = File(System.getProperty("user.home"), ".intellij-lsp")
        socketDir.mkdirs()

        val projectHash = project.basePath?.hashCode()?.toString(16)
            ?: project.locationHash.takeIf { it.isNotBlank() }
            ?: System.identityHashCode(project).toString(16)
        return File(socketDir, "dap-project-$projectHash.sock").absolutePath
    }
}
