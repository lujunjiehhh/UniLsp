# PROJECT KNOWLEDGE BASE

**Generated:** 2026-01-13
**Commit:** 9cc8a60
**Branch:** main

## OVERVIEW
IntelliJ IDEA plugin exposing LSP + DAP servers over TCP/UDS for external editors.
Primary stack: Kotlin (JVM 21), IntelliJ Platform SDK, JSON-RPC framing.

## STRUCTURE
```
./
├── src/main/kotlin/com/frenchef/intellijlsp/  # LSP + DAP implementation
├── src/main/resources/META-INF/              # plugin.xml + kotlin-features.xml
├── src/test/                                 # JUnit 5 tests (limited)
├── vscode-extension/                         # VSCode client + DAP bridge
├── tools/                                    # LSP/DAP CLI utilities
├── samples/                                  # example projects/test data
├── skills/                                   # opencode skills data
├── .specify/                                 # specs + templates
└── tmp/                                      # temporary/generated artifacts
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Plugin entrypoints | src/main/resources/META-INF/plugin.xml | Services, startup activities, UI widgets |
| LSP server lifecycle | src/main/kotlin/com/frenchef/intellijlsp/server/ | TCP/UDS servers and manager |
| LSP request handlers | src/main/kotlin/com/frenchef/intellijlsp/handlers/ | register() per LSP method |
| IntelliJ PSI integration | src/main/kotlin/com/frenchef/intellijlsp/intellij/ | Providers mapping PSI to LSP |
| JSON-RPC transport | src/main/kotlin/com/frenchef/intellijlsp/protocol/ | MessageReader/Writer + JsonRpcHandler |
| DAP server & session | src/main/kotlin/com/frenchef/intellijlsp/dap/ | Session state machine + servers |
| DAP handlers | src/main/kotlin/com/frenchef/intellijlsp/dap/handlers/ | One handler per DAP command |
| DAP models | src/main/kotlin/com/frenchef/intellijlsp/dap/model/ | DTOs + enums |
| DAP backend | src/main/kotlin/com/frenchef/intellijlsp/dap/backend/ | IntelliJ debugger bridge |
| VSCode client | vscode-extension/extension.ts | LSP client + inline DAP adapter |
| LSP test CLI | tools/lsp_tool/ | Python CLI for LSP endpoints |
| DAP test CLI | tools/dap-client-js/ | Node CLI + REPL |

## CODE MAP (selected)
| Symbol | Type | Location | Role |
|--------|------|----------|------|
| LspServerStartupActivity | class | src/main/kotlin/com/frenchef/intellijlsp/LspServerStartupActivity.kt | Auto-start LSP server |
| LspServerManager | class | src/main/kotlin/com/frenchef/intellijlsp/server/LspServerManager.kt | Manages TCP/UDS servers |
| JsonRpcHandler | class | src/main/kotlin/com/frenchef/intellijlsp/protocol/JsonRpcHandler.kt | LSP JSON-RPC routing |
| MessageReader/Writer | class | src/main/kotlin/com/frenchef/intellijlsp/protocol/ | Content-Length framing |
| DapServerStartupActivity | class | src/main/kotlin/com/frenchef/intellijlsp/dap/DapServerStartupActivity.kt | Auto-start DAP server |
| DapServerStarter | class | src/main/kotlin/com/frenchef/intellijlsp/dap/DapServerStarter.kt | TCP/UDS/stdio DAP start |
| DapSession | class | src/main/kotlin/com/frenchef/intellijlsp/dap/DapSession.kt | Session state machine |
| DapRequestRouter | class | src/main/kotlin/com/frenchef/intellijlsp/dap/handlers/DapRequestRouter.kt | DAP command dispatch |
| IntellijDebuggerBackend | class | src/main/kotlin/com/frenchef/intellijlsp/dap/backend/IntellijDebuggerBackend.kt | Debugger adapter bridge |

## CONVENTIONS
- Prefer JetBrains/Serena LSP tools when available.
- Kotlin/JVM 21; Gradle IntelliJ Platform plugin with instrumentCode disabled.
- Handlers register explicit JSON-RPC methods via register().
- PSI access stays inside ReadAction/WriteAction; long IO uses Dispatchers.IO.
- LSP uses LspGson; DAP uses DapGson.

## ANTI-PATTERNS (THIS PROJECT)
- Do not edit generated/vendor dirs: build/, .gradle-user-home3/, tmp/, vscode-extension/node_modules/.
- Treat reference-code/ as read-only if present.
- Avoid blocking the EDT in handler or provider code.

## COMMANDS
```bash
./gradlew buildPlugin
./gradlew runIde
```

## NOTES
- LSP default TCP port 2087; DAP default TCP port 5005.
- DAP discovery file written under ~/.intellij-lsp/dap-project-<hash>.json.
- VSCode extension uses intellijLsp.dapPort and injects projectFolder in DAP initialize.
