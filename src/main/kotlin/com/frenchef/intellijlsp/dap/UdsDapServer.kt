package com.frenchef.intellijlsp.dap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unix Domain Socket DAP server implementation.
 */
class UdsDapServer(
    private val project: Project,
    private val socketPath: String
) {
    private val log = logger<UdsDapServer>()

    @Volatile
    private var running = false

    private var serverChannel: ServerSocketChannel? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val clients = ConcurrentHashMap<Int, ClientConnection>()
    private val clientIdCounter = AtomicInteger(0)

    fun start(): Boolean {
        if (running) {
            log.warn("DAP UDS server already running on $socketPath")
            return false
        }

        return try {
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
                log.warn("Could not set DAP socket file permissions", e)
            }

            running = true
            scope.launch { acceptConnections() }
            true
        } catch (e: Exception) {
            DapErrors.logTransportError("Failed to start DAP UDS server", e)
            false
        }
    }

    fun stop() {
        if (!running) {
            return
        }

        running = false

        clients.values.forEach { it.close() }
        clients.clear()

        try {
            serverChannel?.close()
        } catch (e: Exception) {
            log.warn("Error closing DAP UDS server channel", e)
        }

        try {
            File(socketPath).delete()
        } catch (e: Exception) {
            log.warn("Error deleting DAP UDS socket file", e)
        }

        scope.cancel()
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
                val client = ClientConnection(clientId, clientChannel)
                clients[clientId] = client
                client.start()
            } catch (e: Exception) {
                if (running) {
                    DapErrors.logTransportError("Error accepting DAP UDS connection", e)
                }
            }
        }
    }

    private inner class ClientConnection(
        val id: Int,
        private val channel: SocketChannel
    ) {
        private val server = DapServer(
            project = project,
            input = channel.socket().getInputStream(),
            output = channel.socket().getOutputStream(),
            onExit = {
                clients.remove(id)
                closeChannel()
            },
            closeStreamsOnShutdown = false
        )

        fun start() {
            log.info("DAP UDS client $id connected")
            server.start()
        }

        fun close() {
            server.stop()
            closeChannel()
        }

        private fun closeChannel() {
            try {
                channel.close()
            } catch (e: Exception) {
                log.warn("Error closing DAP UDS client $id channel", e)
            }
        }
    }
}
