# LSP HANDLERS

## OVERVIEW
JSON-RPC handlers for LSP requests/notifications.

## WHERE TO LOOK
- Document sync: DocumentSyncHandler.kt
- Diagnostics: DiagnosticsHandler.kt
- Code actions: CodeActionHandler.kt + CodeActionResolveHandler.kt
- Symbols: DocumentSymbolHandler.kt, WorkspaceSymbolHandler.kt
- Rename: RenameHandler.kt
- Hierarchies: CallHierarchyHandler.kt, TypeHierarchyHandler.kt
- Formatting: FormattingHandler.kt

## CONVENTIONS
- register() wires JSON-RPC methods on JsonRpcHandler.
- Use LspGson for DTO parsing and responses.
- PSI access runs inside ReadAction.
- Normalize URIs with LspUriUtil.normalize when needed.

## ANTI-PATTERNS
- Do not block EDT in handlers.
- Avoid direct file system edits; use services/providers.
