package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.model.AttachRequestArguments
import com.frenchef.intellijlsp.dap.model.ConfigurationDoneArguments
import com.frenchef.intellijlsp.dap.model.ContinueArguments
import com.frenchef.intellijlsp.dap.model.DapMessage
import com.frenchef.intellijlsp.dap.model.DapRequest
import com.frenchef.intellijlsp.dap.model.DisconnectArguments
import com.frenchef.intellijlsp.dap.model.EvaluateArguments
import com.frenchef.intellijlsp.dap.model.InitializeRequestArguments
import com.frenchef.intellijlsp.dap.model.LaunchRequestArguments
import com.frenchef.intellijlsp.dap.model.NextArguments
import com.frenchef.intellijlsp.dap.model.PauseArguments
import com.frenchef.intellijlsp.dap.model.ScopesArguments
import com.frenchef.intellijlsp.dap.model.SetBreakpointsArguments
import com.frenchef.intellijlsp.dap.model.StackTraceArguments
import com.frenchef.intellijlsp.dap.model.StepInArguments
import com.frenchef.intellijlsp.dap.model.StepOutArguments
import com.frenchef.intellijlsp.dap.model.TerminateArguments
import com.frenchef.intellijlsp.dap.model.ThreadsArguments
import com.frenchef.intellijlsp.dap.model.VariablesArguments

/**
 * SDK-side Debug Adapter Protocol client contract.
 *
 * The client maintains an internal outbound queue of DAP requests and encodes them
 * into bytes via [send]. Incoming bytes are fed into [recv], which buffers partial
 * frames and returns fully decoded DAP messages in order.
 */
interface DapClient {
    /**
     * Encode the next queued outbound message into a full DAP frame.
     *
     * Returns null if no outbound messages are pending. Implementations should
     * return exactly one frame per call (Content-Length header + JSON payload).
     */
    fun send(): ByteArray?

    /**
     * Feed raw bytes from the transport layer into the client.
     *
     * Implementations must handle partial frames and return any fully decoded
     * [DapMessage] objects in the order they were received.
     */
    fun recv(bytes: ByteArray): List<DapMessage>

    fun initialize(args: InitializeRequestArguments): DapRequest
    fun configurationDone(args: ConfigurationDoneArguments = ConfigurationDoneArguments()): DapRequest
    fun launch(args: LaunchRequestArguments): DapRequest
    fun attach(args: AttachRequestArguments): DapRequest
    fun disconnect(args: DisconnectArguments? = null): DapRequest
    fun terminate(args: TerminateArguments? = null): DapRequest

    fun setBreakpoints(args: SetBreakpointsArguments): DapRequest
    fun threads(args: ThreadsArguments = ThreadsArguments()): DapRequest
    fun stackTrace(args: StackTraceArguments): DapRequest
    fun scopes(args: ScopesArguments): DapRequest
    fun variables(args: VariablesArguments): DapRequest
    fun evaluate(args: EvaluateArguments): DapRequest

    fun continueExecution(args: ContinueArguments): DapRequest
    fun next(args: NextArguments): DapRequest
    fun stepIn(args: StepInArguments): DapRequest
    fun stepOut(args: StepOutArguments): DapRequest
    fun pause(args: PauseArguments): DapRequest
}
