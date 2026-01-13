# LSP PROTOCOL LAYER

## OVERVIEW
JSON-RPC framing, message dispatch, and pending request tracking.

## WHERE TO LOOK
- Framing: MessageReader.kt, MessageWriter.kt
- Routing: JsonRpcHandler.kt
- Pending requests: PendingRequestManager.kt
- LSP DTOs: protocol/models/

## CONVENTIONS
- Content-Length framing only; UTF-8 payloads.
- Use LspGson.instance for JSON parsing/serialization.
- sendRequest uses PendingRequestManager; set messageSender first.

## ANTI-PATTERNS
- Do not write to output streams without synchronization.
- Avoid bypassing JsonRpcHandler for request dispatch.
