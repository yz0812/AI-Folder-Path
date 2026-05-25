package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke

class ShortcutSettingsPanel(initialState: EditableShortcutPage) {
    private data class CardWidgets(
        val field: ShortcutCaptureField,
        val recordButton: JButton,
        val clearButton: JButton,
        val restoreDefaultButton: JButton,
        val statusLabel: JBLabel,
        val cardPanel: JPanel,
    )

    private val keymapLabel = JLabel()
    private val pageMessageLabel = JBLabel().apply {
        border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
        isAllowAutoWrapping = true
    }
    private val rootPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
    }
    private val cardWidgets = LinkedHashMap<String, CardWidgets>()
    private var currentState: EditableShortcutPage = initialState

    init {
        val headerPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            alignmentX = JComponent.LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(0, 0, 12, 0)
            add(JLabel("当前 Keymap"), constraints(0, 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(keymapLabel, constraints(0, 1, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = Insets(4, 0, 0, 0)))
            add(pageMessageLabel, constraints(0, 2, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
        }
        rootPanel.add(headerPanel)

        currentState.cards.forEach { card ->
            val statusLabel = JBLabel().apply {
                isAllowAutoWrapping = true
            }
            val field = ShortcutCaptureField { shortcut, error ->
                syncCard(card.definition.actionId, shortcut, error)
            }.apply {
                columns = 18
            }
            val recordButton = JButton("录制")
            val clearButton = JButton("清空")
            val restoreDefaultButton = JButton("恢复默认")

            recordButton.addActionListener {
                field.requestFocusInWindow()
                field.requestFocus()
            }
            clearButton.addActionListener { field.clearShortcut() }
            restoreDefaultButton.addActionListener { field.setShortcut(card.definition.defaultShortcut) }

            val titleLabel = JLabel(card.definition.title).apply {
                font = font.deriveFont(font.style or Font.BOLD)
            }
            val descriptionLabel = JLabel(card.definition.description).apply {
                border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
            }
            val textPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(titleLabel)
                add(descriptionLabel)
            }
            val controlsPanel = JPanel(GridBagLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
                add(field, constraints(0, 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = Insets(0, 0, 0, 8)))
                add(recordButton, constraints(1, 0, insets = Insets(0, 0, 0, 8)))
                add(clearButton, constraints(2, 0, insets = Insets(0, 0, 0, 8)))
                add(restoreDefaultButton, constraints(3, 0))
                add(statusLabel, constraints(0, 1, gridwidth = 4, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = Insets(6, 0, 0, 0)))
            }
            val cardPanel = JPanel(BorderLayout()).apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createEtchedBorder(),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12),
                )
                add(textPanel, BorderLayout.NORTH)
                add(controlsPanel, BorderLayout.CENTER)
            }
            cardWidgets[card.definition.actionId] = CardWidgets(
                field = field,
                recordButton = recordButton,
                clearButton = clearButton,
                restoreDefaultButton = restoreDefaultButton,
                statusLabel = statusLabel,
                cardPanel = cardPanel,
            )
            rootPanel.add(cardPanel)
            rootPanel.add(JPanel().apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(0, 0, 12, 0)
            })
        }

        setState(initialState)
    }

    fun root(): JComponent = rootPanel

    fun snapshot(): EditableShortcutPage {
        return currentState.copy(
            cards = currentState.cards.map { card ->
                val widgets = cardWidgets.getValue(card.definition.actionId)
                card.copy(
                    editedShortcut = widgets.field.currentShortcut(),
                    validationMessage = widgets.field.errorText(),
                )
            },
        )
    }

    fun setShortcut(actionId: String, shortcut: KeyboardShortcut?) {
        cardWidgets.getValue(actionId).field.setShortcut(shortcut)
    }

    fun captureShortcut(actionId: String, first: KeyStroke?, second: KeyStroke?) {
        val field = cardWidgets.getValue(actionId).field
        field.acceptStroke(first)
        second?.let(field::acceptStroke)
    }

    fun setState(state: EditableShortcutPage) {
        currentState = state
        keymapLabel.text = state.originalKeymapName
        pageMessageLabel.text = state.pageMessage
        state.cards.forEach { card ->
            val widgets = cardWidgets.getValue(card.definition.actionId)
            widgets.field.loadShortcut(card.editedShortcut, card.validationMessage)
            widgets.statusLabel.text = card.validationMessage ?: card.statusMessage.orEmpty()
        }
        applyEditableState()
    }

    fun displayText(actionId: String): String = cardWidgets.getValue(actionId).field.displayText()

    fun statusMessage(actionId: String): String? = cardWidgets.getValue(actionId).statusLabel.text

    fun pageMessage(): String = pageMessageLabel.text

    fun dispose() {
        cardWidgets.values.forEach { it.field.dispose() }
    }

    fun isCardControlsEnabled(actionId: String): Boolean {
        val widgets = cardWidgets.getValue(actionId)
        return widgets.field.isEnabled && widgets.recordButton.isEnabled && widgets.clearButton.isEnabled && widgets.restoreDefaultButton.isEnabled
    }

    private fun syncCard(actionId: String, shortcut: KeyboardShortcut?, validationMessage: String?) {
        currentState = currentState.replaceCard(actionId, shortcut, validationMessage)
        val card = currentState.cards.first { it.definition.actionId == actionId }
        val widgets = cardWidgets.getValue(actionId)
        widgets.statusLabel.text = card.validationMessage ?: card.statusMessage.orEmpty()
        widgets.cardPanel.revalidate()
        widgets.cardPanel.repaint()
    }

    private fun applyEditableState() {
        cardWidgets.values.forEach { widgets ->
            widgets.field.isEnabled = true
            widgets.recordButton.isEnabled = true
            widgets.clearButton.isEnabled = true
            widgets.restoreDefaultButton.isEnabled = true
        }
    }

    private fun constraints(
        gridx: Int,
        gridy: Int,
        gridwidth: Int = 1,
        weightx: Double = 0.0,
        fill: Int = GridBagConstraints.NONE,
        anchor: Int = GridBagConstraints.WEST,
        insets: Insets = Insets(0, 0, 0, 0),
    ): GridBagConstraints {
        return GridBagConstraints().apply {
            this.gridx = gridx
            this.gridy = gridy
            this.gridwidth = gridwidth
            this.weightx = weightx
            this.fill = fill
            this.anchor = anchor
            this.insets = insets
        }
    }
}
