package com.frenchef.intellijlsp.dap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unix Domain Socket-based DAP server implementation.
 */
class UdsDapServer(
    private val project: Project,
    private val socketPath: String
) {

    private val log = logger<UdsDapServer>()

    @Volatile
    private var running = false

    private var serverChannel: ServerSocketChannel? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val clients = ConcurrentHashMap<Int, ClientConnection>()
    private val clientIdCounter = AtomicInteger(0)

    fun start(): Boolean {
        if (running) {
            log.warn("UDS DAP server already running on socket $socketPath")
            return false
        }

        try {
            val socketFile = File(socketPath)
            socketFile.parentFile?.mkdirs()

            if (socketFile.exists()) {
                socketFile.delete()
            }

            val address = UnixDomainSocketAddress.of(socketPath)
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            serverChannel?.bind(address)

            try {
                val permissions = setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
                )
                Files.setPosixFilePermissions(socketFile.toPath(), permissions)
            } catch (e: Exception) {
                log.warn("Could not set socket file permissions", e)
            }

            running = true
            log.info("UDS DAP server started on socket $socketPath for project: ${project.name}")

            acceptJob = scope.launch {
                acceptConnections()
            }

            return true
        } catch (e: Exception) {
            log.error("Failed to start UDS DAP server on socket $socketPath", e)
            return false
        }
    }

    fun stop() {
        if (!running) {
            return
        }

        log.info("Stopping UDS DAP server on socket $socketPath")
        running = false

        val snapshot = clients.values.toList()
        clients.clear()
        snapshot.forEach { client ->
            client.server.stop()
            client.close()
        }

        try {
            serverChannel?.close()
        } catch (e: Exception) {
            log.warn("Error closing server channel", e)
        }

        try {
            File(socketPath).delete()
        } catch (e: Exception) {
            log.warn("Error deleting socket file", e)
        }

        acceptJob?.cancel()
        scope.cancel()

        log.info("UDS DAP server stopped")
    }

    fun isRunning(): Boolean = running

    fun getSocketPath(): String = socketPath

    fun getClientCount(): Int = clients.size

    private suspend fun acceptConnections() {
        val channel = serverChannel ?: return

        while (running && channel.isOpen) {
            try {
                val clientChannel = withContext(Dispatchers.IO) {
                    channel.accept()
                }

                val clientId = clientIdCounter.incrementAndGet()
                val server = DapServer(
                    project = project,
                    input = clientChannel.socket().getInputStream(),
                    output = clientChannel.socket().getOutputStream(),
                    closeStreamsOnShutdown = true,
                    onExit = {
                        clients.remove(clientId)?.close()
                    }
                )

                val client = ClientConnection(clientId, clientChannel, server)
                clients[clientId] = client
                server.start()

                log.info("UDS DAP client $clientId connected")
            } catch (e: CancellationException) {
                log.debug("UDS accept loop cancelled")
                break
            } catch (e: Exception) {
                if (running) {
                    log.error("Error accepting UDS DAP connection", e)
                }
            }
        }
    }

    private inner class ClientConnection(
        val id: Int,
        private val channel: SocketChannel,
        val server: DapServer
    ) {
        fun close() {
            try {
                channel.close()
            } catch (e: Exception) {
                log.debug("Error closing UDS client $id channel", e)
            }
        }
    }
}
