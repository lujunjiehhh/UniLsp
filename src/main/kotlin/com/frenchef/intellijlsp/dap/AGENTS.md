# DAP SUBSYSTEM

## OVERVIEW
Debug Adapter Protocol server implementation, session state, and transport wrappers.

## WHERE TO LOOK
- Transport entrypoints: DapServerStarter.kt, UdsDapServer.kt
- Session state machine: DapSession.kt
- Error mapping: DapErrors.kt + dap/model/DapError.kt
- Backend bridge: backend/IntellijDebuggerBackend.kt
- Discovery file: services/DapDiscovery.kt
- Port allocation: services/DapPortAllocator.kt

## CONVENTIONS
- DAP framing uses Content-Length with DapServer input/output streams.
- State transitions must go through DapSession (initialize → configuring → running).
- Errors returned to clients should use DapErrors helpers.
- Transport mode uses config/DapSettings (default TCP 5005).

## ANTI-PATTERNS
- Do not send events before initialize completes.
- Avoid long-running backend work on the UI thread.
