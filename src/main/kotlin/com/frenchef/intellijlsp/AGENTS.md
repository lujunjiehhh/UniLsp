# CORE KOTLIN MODULE

## OVERVIEW
Primary Kotlin implementation for LSP + DAP servers and IntelliJ integration.

## STRUCTURE
```
com/frenchef/intellijlsp/
├── handlers/      # LSP request handlers
├── intellij/      # PSI/IDE integration providers
├── protocol/      # JSON-RPC framing + message types
├── server/        # TCP/UDS LSP servers
├── dap/           # DAP server + session + backend
├── language/      # LanguageHandler implementations
├── services/      # Project services
├── config/        # Settings UI + persistence
└── util/          # Logging/URI helpers
```

## WHERE TO LOOK
- LSP startup: LspServerStartupActivity.kt
- DAP startup: dap/DapServerStartupActivity.kt
- URI handling: util/LspUriUtil.kt and intellij/LspDecompiledUriResolver.kt
- Diagnostics flow: handlers/DiagnosticsHandler.kt + intellij/DiagnosticsProvider.kt

## CONVENTIONS
- PSI access must be wrapped in ReadAction/WriteAction.
- Heavy IO runs on Dispatchers.IO; avoid blocking EDT.
- Use LspGson for LSP DTOs and DapGson for DAP DTOs.
- Normalize URIs via LspUriUtil.normalize before VirtualFile lookups.

## ANTI-PATTERNS
- Do not bypass DapSession state transitions for DAP requests.
- Avoid direct file system edits inside handlers/providers.
