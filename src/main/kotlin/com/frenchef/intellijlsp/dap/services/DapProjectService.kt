package com.frenchef.intellijlsp.dap.services

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapServerStarter
import com.frenchef.intellijlsp.dap.UdsDapServer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * DAP Project Service
 * 
 * Project-level service that manages the DAP server lifecycle for a project.
 * 
 * Phase 4: Integration
 * Task: T062 - Implement DAP project service
 */
@Service(Service.Level.PROJECT)
class DapProjectService(private val project: Project) : Disposable {
    
    private val log = logger<DapProjectService>()
    private val serverStarter = DapServerStarter(project)
    private var udsServer: UdsDapServer? = null
    
    companion object {
        fun getInstance(project: Project): DapProjectService {
            return project.getService(DapProjectService::class.java)
        }
    }
    
    /**
     * Start the DAP server on the specified port.
     */
    fun startServer(port: Int = 5005): Boolean {
        log.info("Starting DAP server for project: ${project.name}")
        DapErrors.logInfo("Starting DAP server for project: ${project.name}")
        
        return serverStarter.start(port)
    }

    /**
     * Start the DAP server using Unix Domain Socket transport.
     */
    fun startUdsServer(socketPath: String = defaultSocketPath()): Boolean {
        if (udsServer?.isRunning() == true) {
            log.warn("UDS DAP server already running for project: ${project.name}")
            return false
        }

        log.info("Starting UDS DAP server for project: ${project.name}")
        DapErrors.logInfo("Starting UDS DAP server for project: ${project.name}")

        val server = UdsDapServer(project, socketPath)
        val started = server.start()
        if (started) {
            udsServer = server
        }
        return started
    }
    
    /**
     * Stop the DAP server.
     */
    fun stopServer() {
        log.info("Stopping DAP server for project: ${project.name}")
        DapErrors.logInfo("Stopping DAP server for project: ${project.name}")
        
        serverStarter.stop()
    }

    /**
     * Stop the UDS DAP server.
     */
    fun stopUdsServer() {
        udsServer?.let { server ->
            log.info("Stopping UDS DAP server for project: ${project.name}")
            DapErrors.logInfo("Stopping UDS DAP server for project: ${project.name}")
            server.stop()
        }
        udsServer = null
    }
    
    /**
     * Check if the DAP server is running.
     */
    fun isServerRunning(): Boolean {
        return serverStarter.isRunning()
    }

    /**
     * Check if the UDS DAP server is running.
     */
    fun isUdsServerRunning(): Boolean {
        return udsServer?.isRunning() == true
    }
    
    /**
     * Get the current server port.
     */
    fun getServerPort(): Int? {
        return serverStarter.getPort()
    }

    /**
     * Get the UDS socket path if running.
     */
    fun getUdsSocketPath(): String? {
        return udsServer?.getSocketPath()
    }
    
    /**
     * Restart the DAP server.
     */
    fun restartServer(port: Int = 5005): Boolean {
        log.info("Restarting DAP server for project: ${project.name}")
        
        stopServer()
        Thread.sleep(500) // Give time for cleanup
        return startServer(port)
    }
    
    override fun dispose() {
        log.info("Disposing DAP project service for project: ${project.name}")
        stopServer()
        stopUdsServer()
    }

    private fun defaultSocketPath(): String {
        val socketDir = File(System.getProperty("user.home"), ".intellij-lsp")
        socketDir.mkdirs()

        val projectHash = project.basePath?.hashCode()?.toString(16) ?: "unknown"
        return File(socketDir, "dap-project-$projectHash.sock").absolutePath
    }
}
