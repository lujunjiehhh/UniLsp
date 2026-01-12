package com.frenchef.intellijlsp.dap.services

import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-level allocator for DAP TCP ports across projects.
 * Prevents conflicts when multiple projects are open.
 */
object DapPortAllocator {
    private val allocatedPorts = ConcurrentHashMap<Int, String>()

    /**
     * Allocate an available port starting from the given port.
     *
     * @param projectName The name of the project requesting the port
     * @param startingPort The port to try first
     * @return The allocated port number, or null if no port is available
     */
    fun allocatePort(projectName: String, startingPort: Int): Int? {
        var port = startingPort.coerceAtLeast(1)

        repeat(100) {
            if (port > 65535) {
                return null
            }

            if (isPortAvailable(port) && allocatedPorts.putIfAbsent(port, projectName) == null) {
                return port
            }
            port++
        }

        return null
    }

    /**
     * Release a previously allocated port.
     *
     * @param port The port to release
     */
    fun releasePort(port: Int) {
        allocatedPorts.remove(port)
    }

    /**
     * Check if a port is currently allocated.
     *
     * @param port The port to check
     * @return true if the port is allocated, false otherwise
     */
    fun isAllocated(port: Int): Boolean = allocatedPorts.containsKey(port)

    /**
     * Get the project name that has allocated a port.
     *
     * @param port The port to query
     * @return The project name, or null if the port is not allocated
     */
    fun getProjectForPort(port: Int): String? = allocatedPorts[port]

    /**
     * Get all allocated ports.
     *
     * @return A map of port to project name
     */
    fun getAllocatedPorts(): Map<Int, String> = allocatedPorts.toMap()

    /**
     * Check if a port is available (not in use by any process).
     */
    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: Exception) {
            false
        }
    }
}
