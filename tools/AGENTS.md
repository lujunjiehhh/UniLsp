# TOOLS

## OVERVIEW
CLI utilities for manual LSP/DAP testing.

## WHERE TO LOOK
- LSP test runner: tools/lsp_tool/main.py
- LSP client: tools/lsp_tool/client.py
- Call/type hierarchy test: tools/test_hierarchy.py
- DAP CLI: tools/dap-client-js/bin/dap-cli.js

## CONVENTIONS
- LSP tools assume TCP connection (default 2087).
- DAP CLI supports tcp/uds/stdio and discovery in ~/.intellij-lsp.
- Node >= 18 for dap-client-js.

## ANTI-PATTERNS
- Do not edit temp scripts under tools/dap-client-js/*.txt.
