package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.backend.DebuggerBackend
import com.frenchef.intellijlsp.dap.backend.IntellijDebuggerBackend
import com.frenchef.intellijlsp.dap.handlers.DapRequestRouter
import com.frenchef.intellijlsp.dap.handlers.InitializeHandler
import com.frenchef.intellijlsp.dap.model.DapEvent
import com.frenchef.intellijlsp.dap.model.DapResponse
import com.frenchef.intellijlsp.dap.model.InitializedEventBody
import com.frenchef.intellijlsp.protocol.MessageReader
import com.frenchef.intellijlsp.protocol.MessageWriter
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DAP Server
 *
 * Manages the DAP session lifecycle and routes requests over a framed stream.
 */
class DapServer(
    private val project: Project,
    private val input: InputStream,
    private val output: OutputStream,
    private val onExit: (() -> Unit)? = null,
    private val closeStreamsOnShutdown: Boolean = true
) {
    private val log = logger<DapServer>()
    private val gson = DapGson.instance

    private val session = DapSession()
    private val backend: DebuggerBackend = IntellijDebuggerBackend(project)
    private val router = DapRequestRouter(session)

    private val messageReader = MessageReader(input)
    private val messageWriter = MessageWriter(output)

    private val running = AtomicBoolean(false)
    private val shutdownStarted = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        registerHandlers()
    }

    /**
     * Start the DAP server loop.
     */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("DAP server already running")
            return
        }

        scope.launch {
            try {
                messageLoop()
            } catch (e: Exception) {
                DapErrors.logTransportError("DAP server message loop failed", e)
            } finally {
                shutdownOnce()
            }
        }
    }

    /**
     * Stop the DAP server loop.
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        scope.cancel()
        shutdownOnce()
    }

    private suspend fun messageLoop() {
        while (running.get()) {
            try {
                val json = withContext(Dispatchers.IO) {
                    messageReader.readMessage()
                } ?: break

                processMessage(json)
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                DapErrors.logTransportError("Error reading DAP message", e)
            }
        }
    }

    private fun processMessage(json: JsonObject) {
        try {
            val response = router.handleMessage(json)
            if (response != null) {
                sendResponse(response)
                if (response.command == "initialize" && response.success) {
                    sendInitializedEvent()
                }
            }
        } catch (e: Exception) {
            DapErrors.logInternalError("Error processing DAP message", e)
        }
    }

    private fun sendResponse(response: DapResponse) {
        try {
            val json = gson.toJsonTree(response).asJsonObject
            messageWriter.writeMessage(json)
        } catch (e: Exception) {
            DapErrors.logTransportError("Failed to send DAP response", e)
        }
    }

    private fun sendEvent(event: DapEvent) {
        try {
            val json = gson.toJsonTree(event).asJsonObject
            messageWriter.writeMessage(json)
        } catch (e: Exception) {
            DapErrors.logTransportError("Failed to send DAP event", e)
        }
    }

    private fun sendInitializedEvent() {
        val event = DapEvent(
            seq = session.nextSeq(),
            event = "initialized",
            body = gson.toJsonTree(InitializedEventBody())
        )
        sendEvent(event)
    }

    private fun registerHandlers() {
        router.registerHandler("initialize", InitializeHandler(session))
    }

    private fun shutdownOnce() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return
        }

        shutdownInternal()
    }

    private fun shutdownInternal() {
        try {
            if (!session.isTerminated()) {
                runBlocking {
                    backend.disconnect(false)
                }
            }
            session.reset()
        } catch (e: Exception) {
            DapErrors.logInternalError("Error during DAP shutdown", e)
        } finally {
            if (closeStreamsOnShutdown) {
                try {
                    input.close()
                } catch (_: Exception) {
                }
                try {
                    output.close()
                } catch (_: Exception) {
                }
            }

            onExit?.invoke()
        }
    }
}
