package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.frenchef.intellijlsp.dap.DapSession
import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.model.Capabilities
import com.frenchef.intellijlsp.dap.model.DapCommands
import com.frenchef.intellijlsp.dap.model.ExceptionBreakpointsFilter
import com.frenchef.intellijlsp.dap.model.InitializeRequestArguments
import com.google.gson.JsonElement

/**
 * Handler for the DAP "initialize" request.
 */
class InitializeHandler(
    private val session: DapSession,
    private val backend: DebuggerBackend
) : DapRequestHandler {

    override suspend fun handle(arguments: JsonElement?): JsonElement? {
        val args = parseArguments<InitializeRequestArguments>(arguments, DapCommands.INITIALIZE)
        if (!session.onInitializeRequest(args)) {
            throw DapErrors.alreadyInitialized()
        }

        // Project activation
        args.projectFolder?.let { folder ->
            val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.find { p ->
                p.basePath?.let { java.nio.file.Paths.get(it).toAbsolutePath().normalize() == java.nio.file.Paths.get(folder).toAbsolutePath().normalize() } == true
            }
            if (project != null) {
                session.setActiveProject(project)
                backend.setActiveProject(project)
            } else {
                com.intellij.openapi.diagnostic.logger<InitializeHandler>().warn("Requested project folder not found among open projects: $folder")
            }
        }

        val capabilities = buildServerCapabilities()
        if (!session.onInitializeComplete(capabilities)) {
            throw DapErrors.internalError("Initialize state transition failed")
        }

        return DapGson.instance.toJsonTree(capabilities)
    }

    private fun buildServerCapabilities(): Capabilities {
        return Capabilities(
            supportsConfigurationDoneRequest = true,
            supportsEvaluateForHovers = true,
            supportsConditionalBreakpoints = true,
            supportsLogPoints = true,
            supportsFunctionBreakpoints = true,
            supportsSetVariable = true,
            exceptionBreakpointFilters = listOf(
                ExceptionBreakpointsFilter(filter = "all", label = "All Exceptions"),
                ExceptionBreakpointsFilter(filter = "uncaught", label = "Uncaught Exceptions"),
                ExceptionBreakpointsFilter(filter = "caught", label = "Caught Exceptions")
            ),
            supportsTerminateRequest = true
        )
    }
}
