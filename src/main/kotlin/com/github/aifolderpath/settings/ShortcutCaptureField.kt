package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapUtil
import java.awt.event.KeyEvent
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.Timer

class ShortcutCaptureField(
    private val onStateChanged: (KeyboardShortcut?, String?) -> Unit = { _, _ -> },
) : JTextField() {
    private var shortcut: KeyboardShortcut? = null
    private var error: String? = null
    private var pendingFirstStroke: KeyStroke? = null
    private val finalizeTimer = Timer(500) { commitPendingStroke() }.apply {
        isRepeats = false
    }

    init {
        isEditable = false
        focusTraversalKeysEnabled = false
    }

    fun acceptStroke(stroke: KeyStroke?) {
        if (stroke == null) {
            return
        }
        if (isPureModifier(stroke)) {
            finalizeTimer.stop()
            pendingFirstStroke = null
            shortcut = null
            error = "不支持纯修饰键"
            text = ""
            publishState()
            return
        }

        if (pendingFirstStroke == null) {
            pendingFirstStroke = stroke
            shortcut = KeyboardShortcut(stroke, null)
            error = null
            text = KeymapUtil.getShortcutText(requireNotNull(shortcut))
            finalizeTimer.restart()
            publishState()
            return
        }

        finalizeTimer.stop()
        pendingFirstStroke = null
        shortcut = null
        error = "只支持单击组合键"
        text = ""
        publishState()
    }

    internal fun commitPendingStroke() {
        finalizeTimer.stop()
        pendingFirstStroke = null
        publishState()
    }

    fun currentShortcut(): KeyboardShortcut? = shortcut

    fun clearShortcut() {
        finalizeTimer.stop()
        pendingFirstStroke = null
        shortcut = null
        error = null
        text = ""
        publishState()
    }

    fun setShortcut(value: KeyboardShortcut?) {
        finalizeTimer.stop()
        pendingFirstStroke = null
        shortcut = value
        error = null
        text = value?.let(KeymapUtil::getShortcutText).orEmpty()
        publishState()
    }

    fun loadShortcut(value: KeyboardShortcut?, errorMessage: String?) {
        finalizeTimer.stop()
        pendingFirstStroke = null
        shortcut = value
        error = errorMessage
        text = value?.let(KeymapUtil::getShortcutText).orEmpty()
    }

    fun errorText(): String? = error

    fun displayText(): String = text

    fun dispose() {
        finalizeTimer.stop()
    }

    override fun processKeyEvent(e: KeyEvent) {
        if (e.id != KeyEvent.KEY_PRESSED || !isEnabled) {
            return
        }
        acceptStroke(KeyStroke.getKeyStrokeForEvent(e))
        e.consume()
    }

    private fun isPureModifier(stroke: KeyStroke): Boolean {
        return stroke.keyCode in setOf(
            KeyEvent.VK_CONTROL,
            KeyEvent.VK_SHIFT,
            KeyEvent.VK_ALT,
            KeyEvent.VK_META,
        )
    }

    private fun publishState() {
        onStateChanged(shortcut, error)
    }
}
