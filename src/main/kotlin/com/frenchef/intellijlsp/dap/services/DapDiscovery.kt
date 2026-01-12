package com.frenchef.intellijlsp.dap.services

import com.frenchef.intellijlsp.config.TransportMode
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Writes a small discovery file for clients to find the active DAP endpoint.
 */
object DapDiscovery {
    private const val DIRECTORY_NAME = ".intellij-lsp"
    private const val FILE_PREFIX = "dap-project-"
    private const val FILE_SUFFIX = ".json"

    fun write(project: Project, transport: TransportMode, port: Int?, socketPath: String?) {
        val path = discoveryPath(project)
        try {
            Files.createDirectories(path.parent)
            val json = buildJson(transport, port, socketPath)
            Files.writeString(
                path,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
        } catch (_: Exception) {
            // Ignore discovery write failures to avoid impacting server startup.
        }
    }

    fun clear(project: Project) {
        val path = discoveryPath(project)
        try {
            Files.deleteIfExists(path)
        } catch (_: Exception) {
            // Best effort cleanup only.
        }
    }

    private fun discoveryPath(project: Project): Path {
        val dir = Paths.get(System.getProperty("user.home"), DIRECTORY_NAME)
        val projectHash = project.basePath?.hashCode()?.toString(16)
            ?: project.locationHash.takeIf { it.isNotBlank() }
            ?: System.identityHashCode(project).toString(16)
        return dir.resolve("$FILE_PREFIX$projectHash$FILE_SUFFIX")
    }

    private fun buildJson(transport: TransportMode, port: Int?, socketPath: String?): String {
        val transportValue = transport.name.lowercase()
        val portValue = port?.toString() ?: "null"
        val socketValue = socketPath?.replace("\\", "\\\\")?.let { "\"$it\"" } ?: "null"
        return """{"transport":"$transportValue","port":$portValue,"socketPath":$socketValue}"""
    }
}
