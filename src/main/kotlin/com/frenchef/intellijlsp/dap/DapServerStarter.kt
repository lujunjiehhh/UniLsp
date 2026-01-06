package com.frenchef.intellijlsp.dap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*

/**
 * DAP Server Starter
 * 
 * Manages the lifecycle of the DAP server, including:
 * - Starting the server on a TCP port
 * - Accepting client connections
 * - Managing server instances
 * 
 * Phase 4: Integration
 * Task: T061 - Implement DAP server startup and connection management
 */
class DapServerStarter(private val project: Project) {
    
    private val log = logger<DapServerStarter>()
    
    private val serverSocket = AtomicReference<ServerSocket?>()
    private val currentServer = AtomicReference<DapServer?>()
    private val acceptScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start the DAP server on the specified port.
     * 
     * @param port The port to listen on (default: 5005)
     * @return true if the server started successfully
     */
    fun start(port: Int = 5005): Boolean {
        if (serverSocket.get() != null) {
            log.warn("DAP server already running")
            return false
        }
        
        try {
            val socket = ServerSocket(port)
            serverSocket.set(socket)
            
            log.info("DAP server listening on port $port")
            DapErrors.logInfo("DAP server started on port $port")
            
            // Start accepting connections
            acceptScope.launch {
                acceptConnections(socket)
            }
            
            return true
        } catch (e: Exception) {
            DapErrors.logTransportError("Failed to start DAP server", e)
            return false
        }
    }
    
    /**
     * Stop the DAP server.
     */
    fun stop() {
        // Stop accepting new connections
        serverSocket.getAndSet(null)?.let { socket ->
            try {
                socket.close()
                log.info("DAP server socket closed")
            } catch (e: Exception) {
                log.warn("Error closing server socket", e)
            }
        }
        
        // Stop current server instance
        currentServer.getAndSet(null)?.stop()
        
        // Cancel accept coroutine
        acceptScope.cancel()
        
        DapErrors.logInfo("DAP server stopped")
    }
    
    /**
     * Check if the server is running.
     */
    fun isRunning(): Boolean {
        return serverSocket.get() != null
    }
    
    /**
     * Get the current server port.
     */
    fun getPort(): Int? {
        return serverSocket.get()?.localPort
    }
    
    /**
     * Accept incoming client connections.
     */
    private suspend fun acceptConnections(socket: ServerSocket) {
        DapErrors.logInfo("DAP server accepting connections")
        
        while (!socket.isClosed) {
            try {
                // Accept next connection
                val clientSocket = withContext(Dispatchers.IO) {
                    socket.accept()
                }
                
                log.info("DAP client connected from ${clientSocket.remoteSocketAddress}")
                DapErrors.logInfo("DAP client connected")
                
                // Stop previous server instance if any
                currentServer.getAndSet(null)?.stop()
                
                // Create new server instance
                val server = DapServer(
                    project = project,
                    input = clientSocket.getInputStream(),
                    output = clientSocket.getOutputStream()
                )
                
                currentServer.set(server)
                server.start()
                
            } catch (e: CancellationException) {
                log.debug("Accept loop cancelled")
                break
            } catch (e: Exception) {
                if (!socket.isClosed) {
                    DapErrors.logTransportError("Error accepting connection", e)
                }
            }
        }
        
        DapErrors.logInfo("DAP server stopped accepting connections")
    }
    
    /**
     * Start a DAP server with stdio transport (for testing or direct invocation).
     */
    fun startStdio(input: InputStream = System.`in`, output: OutputStream = System.out) {
        log.info("Starting DAP server with stdio transport")
        
        val server = DapServer(
            project = project,
            input = input,
            output = output
        )
        
        currentServer.set(server)
        server.start()
        
        DapErrors.logInfo("DAP server started with stdio transport")
    }
}
