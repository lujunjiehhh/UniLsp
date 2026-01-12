package com.frenchef.intellijlsp.handlers

import com.frenchef.intellijlsp.intellij.*
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.server.LspServer
import com.frenchef.intellijlsp.services.LspProjectService
import com.intellij.openapi.project.Project

/**
 * Registry to manage and register all LSP handlers for a project.
 */
object LspHandlerRegistry {
    
    fun registerAll(project: Project, jsonRpcHandler: JsonRpcHandler, server: LspServer? = null) {
        // Create common providers
        val documentManager = DocumentManager(project)
        val completionProvider = CompletionProvider(project)

        // Register lifecycle handler
        val lifecycleHandler = LifecycleHandler(project, jsonRpcHandler)
        server?.let { lifecycleHandler.setServer(it) }
        lifecycleHandler.register()

        // Register standard handlers
        DocumentSyncHandler(project, jsonRpcHandler, documentManager).register()
        HoverHandler(project, jsonRpcHandler, documentManager).register()
        DefinitionHandler(project, jsonRpcHandler, documentManager).register()
        CompletionHandler(project, jsonRpcHandler, documentManager, completionProvider).register()
        ReferencesHandler(project, jsonRpcHandler, documentManager).register()
        DocumentHighlightHandler(project, jsonRpcHandler, documentManager).register()
        TypeDefinitionHandler(project, jsonRpcHandler, documentManager).register()
        DocumentSymbolHandler(project, jsonRpcHandler, documentManager).register()
        SemanticTokensHandler(project, jsonRpcHandler, documentManager).register()
        SignatureHelpHandler(project, jsonRpcHandler, documentManager).register()
        WorkspaceSymbolHandler(project, jsonRpcHandler).register()
        FormattingHandler(project, jsonRpcHandler, documentManager).register()
        CodeActionHandler(project, jsonRpcHandler, documentManager).register()
        CodeActionResolveHandler(project, jsonRpcHandler, documentManager).register()
        ImplementationHandler(project, jsonRpcHandler, documentManager).register()
        InlayHintsHandler(project, jsonRpcHandler, documentManager).register()
        RenameHandler(project, jsonRpcHandler, documentManager).register()
        CallHierarchyHandler(project, jsonRpcHandler, documentManager).register()
        TypeHierarchyHandler(project, jsonRpcHandler, documentManager).register()
        WorkspaceFoldersHandler(project, jsonRpcHandler).register()
        FileWatchingHandler(project, jsonRpcHandler).register()

        // Handle Diagnostics (if server is provided)
        server?.let { s ->
            val projectService = project.getService(LspProjectService::class.java)
            projectService.setServer(s)

            val diagnosticsProvider = DiagnosticsProvider(project)
            val diagnosticsHandler = DiagnosticsHandler(project, documentManager, diagnosticsProvider, s)
            diagnosticsHandler.start()
            projectService.setDiagnosticsHandler(diagnosticsHandler)
        }
    }
}
