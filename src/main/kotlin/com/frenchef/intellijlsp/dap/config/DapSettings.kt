package com.frenchef.intellijlsp.dap.config

import com.frenchef.intellijlsp.config.TransportMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for the DAP server.
 */
@State(
    name = "DapSettings",
    storages = [Storage("IntellijDapSettings.xml")]
)
class DapSettings : PersistentStateComponent<DapSettings> {
    /**
     * Transport mode: TCP or Unix Domain Socket.
     */
    var transportMode: TransportMode = TransportMode.TCP

    /**
     * Starting port for TCP mode. Default is 5005.
     */
    var startingPort: Int = 5005

    /**
     * Whether to auto-start the DAP server when a project opens.
     */
    var autoStart: Boolean = true

    override fun getState(): DapSettings = this

    override fun loadState(state: DapSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): DapSettings {
            return ApplicationManager.getApplication().getService(DapSettings::class.java)
        }
    }
}
