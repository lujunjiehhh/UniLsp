# DAP HANDLERS

## OVERVIEW
One class per DAP command, registered in DapRequestRouter.

## WHERE TO LOOK
- Initialize/Launch/Attach: InitializeHandler.kt, LaunchHandler.kt, AttachHandler.kt
- Breakpoints: SetBreakpointsHandler.kt, SetFunctionBreakpointsHandler.kt, SetExceptionBreakpointsHandler.kt
- Threads/Stack: ThreadsHandler.kt, StackTraceHandler.kt
- Scopes/Variables/Evaluate: ScopesHandler.kt, VariablesHandler.kt, EvaluateHandler.kt
- Execution: ContinueHandler.kt, NextHandler.kt, StepInHandler.kt, StepOutHandler.kt, PauseHandler.kt
- Lifecycle: ConfigurationDoneHandler.kt, DisconnectHandler.kt, TerminateHandler.kt, RestartHandler.kt

## CONVENTIONS
- Parse args via parseArguments/parseOptionalArguments in HandlerUtils.
- Return JSON using DapGson and response bodies.
- Enforce session state with DapSession for config/running/stopped requests.
- Delegate heavy work to DebuggerBackend.

## ANTI-PATTERNS
- Do not throw raw exceptions; wrap with DapErrors where possible.
- Avoid accessing PSI directly in handlers.
