package com.frenchef.intellijlsp.dap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
    private val acceptScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clients = ConcurrentHashMap<Int, ClientConnection>()
    private val clientIdCounter = AtomicInteger(0)
    private val stdioServer = AtomicReference<DapServer?>()
    
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
        
        // Stop all client connections
        val snapshot = clients.values.toList()
        clients.clear()
        snapshot.forEach { client ->
            client.server.stop()
            client.close()
        }

        stdioServer.getAndSet(null)?.stop()
        
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
     * Get the current connected client count.
     */
    fun getClientCount(): Int {
        return clients.size
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
                
                val clientId = clientIdCounter.incrementAndGet()

                val server = DapServer(
                    project = project,
                    input = clientSocket.getInputStream(),
                    output = clientSocket.getOutputStream(),
                    closeStreamsOnShutdown = true,
                    onExit = {
                        clients.remove(clientId)?.close()
                    }
                )

                clients[clientId] = ClientConnection(clientId, clientSocket, server)
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
        if (stdioServer.get() != null) {
            log.warn("DAP stdio server already running")
            return
        }

        log.info("Starting DAP server with stdio transport")
        
        val server = DapServer(
            project = project,
            input = input,
            output = output,
            closeStreamsOnShutdown = false,
            onExit = { stdioServer.compareAndSet(server, null) }
        )

        stdioServer.set(server)
        server.start()
        
        DapErrors.logInfo("DAP server started with stdio transport")
    }

    private inner class ClientConnection(
        val id: Int,
        private val socket: java.net.Socket,
        val server: DapServer
    ) {
        fun close() {
            try {
                socket.close()
            } catch (e: Exception) {
                log.debug("Error closing DAP client $id socket", e)
            }
        }
    }
}
