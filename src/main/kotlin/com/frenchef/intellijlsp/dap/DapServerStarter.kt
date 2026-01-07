package com.frenchef.intellijlsp.dap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages DAP server lifecycle for TCP/UDS transports.
 */
class DapServerStarter(private val project: Project) {
    private val log = logger<DapServerStarter>()

    private val tcpServerSocket = AtomicReference<ServerSocket?>()
    private var acceptScope: CoroutineScope? = null
    private val tcpClients = ConcurrentHashMap<Int, ClientConnection>()
    private val clientIdCounter = AtomicInteger(0)

    private var udsServer: UdsDapServer? = null
    private var stdioServer: DapServer? = null

    /**
     * Start TCP DAP server on the given port.
     */
    fun startTcp(port: Int = 5005): Boolean {
        if (tcpServerSocket.get() != null) {
            log.warn("DAP TCP server already running")
            return false
        }

        return try {
            val socket = ServerSocket(port, 50, InetAddress.getLoopbackAddress())
            tcpServerSocket.set(socket)
            ensureAcceptScope().launch { acceptConnections(socket) }
            true
        } catch (e: Exception) {
            DapErrors.logTransportError("Failed to start DAP TCP server", e)
            false
        }
    }

    /**
     * Start UDS DAP server on the given socket path.
     */
    fun startUds(socketPath: String): Boolean {
        if (udsServer != null) {
            log.warn("DAP UDS server already running")
            return false
        }

        val server = UdsDapServer(project, socketPath)
        if (!server.start()) {
            return false
        }

        udsServer = server
        return true
    }

    /**
     * Start DAP server over stdio (mainly for VSCode and testing).
     */
    fun startStdio(input: InputStream = System.`in`, output: OutputStream = System.out) {
        if (stdioServer != null) {
            log.warn("DAP stdio server already running")
            return
        }

        val server = DapServer(
            project = project,
            input = input,
            output = output,
            closeStreamsOnShutdown = false
        )
        stdioServer = server
        server.start()
    }

    /**
     * Stop all DAP servers.
     */
    fun stop() {
        tcpServerSocket.getAndSet(null)?.let { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                log.warn("Error closing DAP TCP server socket", e)
            }
        }

        tcpClients.values.forEach { it.close() }
        tcpClients.clear()

        udsServer?.stop()
        udsServer = null

        stdioServer?.stop()
        stdioServer = null

        acceptScope?.cancel()
        acceptScope = null
    }

    fun isRunning(): Boolean {
        return tcpServerSocket.get() != null ||
            udsServer?.isRunning() == true ||
            stdioServer != null
    }

    fun getPort(): Int? = tcpServerSocket.get()?.localPort

    fun getSocketPath(): String? = udsServer?.getSocketPath()

    fun getClientCount(): Int {
        val udsCount = udsServer?.getClientCount() ?: 0
        return tcpClients.size + udsCount
    }

    private fun ensureAcceptScope(): CoroutineScope {
        val current = acceptScope
        val active = current?.coroutineContext?.get(Job)?.isActive == true
        if (active && current != null) {
            return current
        }

        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        acceptScope = newScope
        return newScope
    }

    private suspend fun acceptConnections(socket: ServerSocket) {
        while (!socket.isClosed) {
            try {
                val clientSocket = withContext(Dispatchers.IO) {
                    socket.accept()
                }

                try {
                    val clientId = clientIdCounter.incrementAndGet()
                    val client = ClientConnection(clientId, clientSocket)
                    tcpClients[clientId] = client
                    client.start()
                } catch (e: Exception) {
                    try {
                        clientSocket.close()
                    } catch (closeError: Exception) {
                        log.warn("Error closing DAP client socket after failure", closeError)
                    }
                    throw e
                }
            } catch (e: Exception) {
                if (!socket.isClosed) {
                    DapErrors.logTransportError("Error accepting DAP TCP connection", e)
                }
            }
        }
    }

    private inner class ClientConnection(
        val id: Int,
        private val socket: Socket
    ) {
        private val server = DapServer(
            project = project,
            input = socket.getInputStream(),
            output = socket.getOutputStream(),
            onExit = {
                tcpClients.remove(id)
                closeSocket()
            },
            closeStreamsOnShutdown = false
        )

        fun start() {
            log.info("DAP client $id connected from ${socket.remoteSocketAddress}")
            server.start()
        }

        fun close() {
            server.stop()
            closeSocket()
        }

        private fun closeSocket() {
            try {
                socket.close()
            } catch (e: Exception) {
                log.warn("Error closing DAP client $id socket", e)
            }
        }
    }
}
