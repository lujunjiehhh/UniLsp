package com.frenchef.intellijlsp.dap.services

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapServerStarter
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

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
     * Stop the DAP server.
     */
    fun stopServer() {
        log.info("Stopping DAP server for project: ${project.name}")
        DapErrors.logInfo("Stopping DAP server for project: ${project.name}")
        
        serverStarter.stop()
    }
    
    /**
     * Check if the DAP server is running.
     */
    fun isServerRunning(): Boolean {
        return serverStarter.isRunning()
    }
    
    /**
     * Get the current server port.
     */
    fun getServerPort(): Int? {
        return serverStarter.getPort()
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
    }
}
