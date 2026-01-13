# INTELLIJ INTEGRATION LAYER

## OVERVIEW
Providers that translate IntelliJ PSI/IDE data into LSP responses.

## WHERE TO LOOK
- Completions: CompletionProvider.kt
- Signature help: SignatureHelpProvider.kt
- Symbols: DocumentSymbolProvider.kt, WorkspaceSymbolProvider.kt
- Diagnostics: DiagnosticsProvider.kt, InspectionDiagnosticsProvider.kt
- Semantic tokens: SemanticTokensProvider.kt
- Hierarchy: TypeHierarchyProvider.kt

## CONVENTIONS
- PSI operations run in ReadAction and use PsiDocumentManager.
- Use LanguageHandlerRegistry for per-language logic.
- Keep result limits (MAX_RESULTS) to avoid large payloads.

## ANTI-PATTERNS
- Do not access PSI outside ReadAction.
- Avoid long-running operations on EDT.
