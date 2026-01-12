package com.frenchef.intellijlsp.dap.config

import com.frenchef.intellijlsp.config.TransportMode
import com.frenchef.intellijlsp.dap.services.DapProjectService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Settings UI for the DAP server.
 */
class DapConfigurable : Configurable {
    private var tcpRadioButton: JBRadioButton? = null
    private var udsRadioButton: JBRadioButton? = null
    private var portField: JBTextField? = null
    private var autoStartCheckBox: JCheckBox? = null
    private var statusLabel: JLabel? = null

    override fun getDisplayName(): String {
        return "IntelliJ DAP Server"
    }

    override fun createComponent(): JComponent {
        val settings = DapSettings.getInstance()

        tcpRadioButton = JBRadioButton("Tcp socket", settings.transportMode == TransportMode.TCP)
        udsRadioButton = JBRadioButton("Unix domain socket", settings.transportMode == TransportMode.UDS)

        val transportGroup = ButtonGroup()
        transportGroup.add(tcpRadioButton)
        transportGroup.add(udsRadioButton)

        val transportPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(tcpRadioButton)
            add(Box.createHorizontalStrut(10))
            add(udsRadioButton)
        }

        portField = JBTextField(settings.startingPort.toString(), 10)
        portField?.isEnabled = settings.transportMode == TransportMode.TCP

        tcpRadioButton?.addActionListener {
            portField?.isEnabled = tcpRadioButton?.isSelected == true
        }
        udsRadioButton?.addActionListener {
            portField?.isEnabled = tcpRadioButton?.isSelected == true
        }

        autoStartCheckBox = JCheckBox("Auto-start server when project opens", settings.autoStart)

        statusLabel = JLabel(getServerStatusText())
        statusLabel?.alignmentX = Component.LEFT_ALIGNMENT

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Transport Mode:", transportPanel)
            .addSeparator()
            .addLabeledComponent("Starting Port (TCP):", portField!!)
            .addTooltip("The server will try this port first, then increment if unavailable.")
            .addSeparator()
            .addComponent(autoStartCheckBox!!)
            .addSeparator()
            .addLabeledComponent("Server Status:", statusLabel!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = DapSettings.getInstance()
        val selectedMode = getSelectedTransportMode()
        val portValue = portField?.text?.toIntOrNull() ?: settings.startingPort
        val autoStart = autoStartCheckBox?.isSelected ?: settings.autoStart

        return selectedMode != settings.transportMode ||
            portValue != settings.startingPort ||
            autoStart != settings.autoStart
    }

    override fun apply() {
        val settings = DapSettings.getInstance()

        settings.transportMode = getSelectedTransportMode()
        settings.startingPort = portField?.text?.toIntOrNull() ?: 5005
        settings.autoStart = autoStartCheckBox?.isSelected ?: true

        statusLabel?.text = getServerStatusText()
    }

    override fun reset() {
        val settings = DapSettings.getInstance()

        tcpRadioButton?.isSelected = settings.transportMode == TransportMode.TCP
        udsRadioButton?.isSelected = settings.transportMode == TransportMode.UDS
        portField?.text = settings.startingPort.toString()
        portField?.isEnabled = settings.transportMode == TransportMode.TCP
        autoStartCheckBox?.isSelected = settings.autoStart
        statusLabel?.text = getServerStatusText()
    }

    private fun getServerStatusText(): String {
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isEmpty()) {
            return "No projects open"
        }

        val statusLines = mutableListOf<String>()
        for (project in openProjects) {
            val service = DapProjectService.getInstance(project)
            val status = if (service.isServerRunning()) {
                when {
                    service.getServerPort() != null -> "TCP port ${service.getServerPort()}"
                    service.getSocketPath() != null -> "UDS ${service.getSocketPath()}"
                    else -> "Running"
                }
            } else {
                "Not started"
            }
            statusLines.add("${project.name}: $status")
        }

        return "<html>${statusLines.joinToString("<br>")}</html>"
    }

    private fun getSelectedTransportMode(): TransportMode =
        if (tcpRadioButton?.isSelected == true) TransportMode.TCP else TransportMode.UDS
}
