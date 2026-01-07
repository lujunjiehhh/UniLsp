package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.model.*
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * DAP Session State Machine
 * 
 * Manages the lifecycle of a debug session according to DAP specification:
 * 
 * State transitions:
 * - UNINITIALIZED -> INITIALIZING (on initialize request)
 * - INITIALIZING -> INITIALIZED (after initialize response, before initialized event)
 * - INITIALIZED -> CONFIGURING (after initialized event, during configuration)
 * - CONFIGURING -> RUNNING (after configurationDone)
 * - RUNNING -> STOPPED (on breakpoint hit, step complete, etc.)
 * - STOPPED -> RUNNING (on continue, step, etc.)
 * - Any -> TERMINATED (on disconnect/terminate)
 */
class DapSession {
    
    private val log = logger<DapSession>()
    
    /**
     * Session states
     */
    enum class State {
        /** Initial state before initialize request */
        UNINITIALIZED,
        /** Processing initialize request */
        INITIALIZING,
        /** Initialize complete, waiting for configuration */
        INITIALIZED,
        /** Processing configuration (breakpoints, etc.) */
        CONFIGURING,
        /** Debug session is running */
        RUNNING,
        /** Debug session is stopped (breakpoint, step, etc.) */
        STOPPED,
        /** Session is terminated */
        TERMINATED
    }
    
    private val state = AtomicReference(State.UNINITIALIZED)
    private val sequenceNumber = AtomicInteger(0)
    
    // Client capabilities from initialize request
    private var clientCapabilities: InitializeRequestArguments? = null
    
    // Server capabilities to advertise
    private var serverCapabilities: Capabilities? = null
    
    // Session configuration
    private var linesStartAt1: Boolean = true
    private var columnsStartAt1: Boolean = true
    
    // Current stopped thread (when in STOPPED state)
    private var stoppedThreadId: Int? = null
    
    /**
     * Get the current session state.
     */
    fun getState(): State = state.get()
    
    /**
     * Get the next sequence number for outgoing messages.
     */
    fun nextSeq(): Int = sequenceNumber.incrementAndGet()
    
    /**
     * Check if the session is initialized (has completed initialize handshake).
     */
    fun isInitialized(): Boolean {
        val currentState = state.get()
        return currentState != State.UNINITIALIZED && currentState != State.INITIALIZING
    }
    
    /**
     * Check if the session is in a state that can accept debug requests.
     */
    fun canAcceptDebugRequests(): Boolean {
        val currentState = state.get()
        return currentState == State.RUNNING || currentState == State.STOPPED
    }
    
    /**
     * Check if the session is stopped (can inspect variables, etc.).
     */
    fun isStopped(): Boolean = state.get() == State.STOPPED
    
    /**
     * Check if the session is terminated.
     */
    fun isTerminated(): Boolean = state.get() == State.TERMINATED
    
    /**
     * Get the client capabilities.
     */
    fun getClientCapabilities(): InitializeRequestArguments? = clientCapabilities
    
    /**
     * Get the server capabilities.
     */
    fun getServerCapabilities(): Capabilities? = serverCapabilities
    
    /**
     * Check if lines start at 1 (client preference).
     */
    fun linesStartAt1(): Boolean = linesStartAt1
    
    /**
     * Check if columns start at 1 (client preference).
     */
    fun columnsStartAt1(): Boolean = columnsStartAt1
    
    /**
     * Get the currently stopped thread ID.
     */
    fun getStoppedThreadId(): Int? = stoppedThreadId
    
    // ========================================================================
    // State Transitions
    // ========================================================================
    
    /**
     * Transition to INITIALIZING state on initialize request.
     * 
     * @param args The initialize request arguments
     * @return true if transition was successful
     */
    fun onInitializeRequest(args: InitializeRequestArguments): Boolean {
        if (!state.compareAndSet(State.UNINITIALIZED, State.INITIALIZING)) {
            log.warn("Cannot initialize: current state is ${state.get()}")
            return false
        }
        
        clientCapabilities = args
        linesStartAt1 = args.linesStartAt1 ?: true
        columnsStartAt1 = args.columnsStartAt1 ?: true
        
        log.info("Session transitioning to INITIALIZING")
        return true
    }
    
    /**
     * Complete initialization and set server capabilities.
     * 
     * @param capabilities The server capabilities to advertise
     * @return true if transition was successful
     */
    fun onInitializeComplete(capabilities: Capabilities): Boolean {
        if (!state.compareAndSet(State.INITIALIZING, State.INITIALIZED)) {
            log.warn("Cannot complete initialize: current state is ${state.get()}")
            return false
        }
        
        serverCapabilities = capabilities
        log.info("Session transitioning to INITIALIZED")
        return true
    }
    
    /**
     * Transition to CONFIGURING state after initialized event is sent.
     * 
     * @return true if transition was successful
     */
    fun onInitializedEventSent(): Boolean {
        if (!state.compareAndSet(State.INITIALIZED, State.CONFIGURING)) {
            log.warn("Cannot start configuring: current state is ${state.get()}")
            return false
        }
        
        log.info("Session transitioning to CONFIGURING")
        return true
    }
    
    /**
     * Transition to RUNNING state after configurationDone.
     * 
     * @return true if transition was successful
     */
    fun onConfigurationDone(): Boolean {
        if (!state.compareAndSet(State.CONFIGURING, State.RUNNING)) {
            log.warn("Cannot complete configuration: current state is ${state.get()}")
            return false
        }
        
        log.info("Session transitioning to RUNNING")
        return true
    }
    
    /**
     * Transition to STOPPED state.
     * 
     * @param threadId The thread that stopped
     * @return true if transition was successful
     */
    fun onStopped(threadId: Int): Boolean {
        while (true) {
            val currentState = state.get()
            if (currentState != State.RUNNING && currentState != State.STOPPED) {
                log.warn("Cannot stop: current state is $currentState")
                return false
            }

            if (state.compareAndSet(currentState, State.STOPPED)) {
                stoppedThreadId = threadId
                log.info("Session transitioning to STOPPED (thread=$threadId)")
                return true
            }
        }
    }
    
    /**
     * Transition to RUNNING state from STOPPED.
     * 
     * @return true if transition was successful
     */
    fun onContinued(): Boolean {
        if (!state.compareAndSet(State.STOPPED, State.RUNNING)) {
            log.warn("Cannot continue: current state is ${state.get()}")
            return false
        }
        
        stoppedThreadId = null
        log.info("Session transitioning to RUNNING")
        return true
    }
    
    /**
     * Transition to TERMINATED state.
     * 
     * @return true if transition was successful
     */
    fun onTerminated(): Boolean {
        val previousState = state.getAndSet(State.TERMINATED)
        if (previousState == State.TERMINATED) {
            log.debug("Session already terminated")
            return false
        }
        
        stoppedThreadId = null
        log.info("Session transitioning to TERMINATED from $previousState")
        return true
    }
    
    /**
     * Reset the session to initial state.
     */
    fun reset() {
        state.set(State.UNINITIALIZED)
        sequenceNumber.set(0)
        clientCapabilities = null
        serverCapabilities = null
        linesStartAt1 = true
        columnsStartAt1 = true
        stoppedThreadId = null
        log.info("Session reset to UNINITIALIZED")
    }
}
