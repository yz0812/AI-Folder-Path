package com.github.aifolderpath.settings

import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JComponent

class ShortcutSettingsConfigurable(
    private val keymapProvider: () -> Keymap = {
        requireNotNull(KeymapManager.getInstance().activeKeymap) { "Active keymap is unavailable" }
    },
    private val service: ShortcutKeymapService = ShortcutKeymapService(),
) : SearchableConfigurable {
    private var panel: ShortcutSettingsPanel? = null
    private var loadedState: EditableShortcutPage? = null

    override fun getId(): String = "com.github.aifolderpath.settings.shortcuts"

    override fun getDisplayName(): String = "AI Folder Path"

    override fun createComponent(): JComponent {
        val state = service.buildEditableState(keymapProvider())
        loadedState = state
        return ShortcutSettingsPanel(state).also { panel = it }.root()
    }

    override fun isModified(): Boolean = panel?.snapshot()?.let { it != loadedState } ?: false

    @Throws(ConfigurationException::class)
    override fun apply() {
        val currentPanel = panel ?: return
        if (!isModified) {
            return
        }

        val activeKeymap = keymapProvider()
        val snapshot = currentPanel.snapshot()

        try {
            service.validateActiveKeymap(activeKeymap, snapshot)
            val validationErrors = snapshot.cards.mapNotNull { it.validationMessage }.distinct()
            if (validationErrors.isNotEmpty()) {
                throw ConfigurationException(validationErrors.joinToString("\n"))
            }

            val conflicts = try {
                service.detectConflicts(snapshot)
            } catch (e: IllegalArgumentException) {
                throw ConfigurationException(e.message ?: "快捷键校验失败")
            }
            if (conflicts.isNotEmpty()) {
                throw ConfigurationException(buildConflictMessage(conflicts))
            }

            val savedKeymap = service.applyChanges(snapshot)
            loadedState = service.buildEditableState(savedKeymap)
            currentPanel.setState(requireNotNull(loadedState))
        } catch (e: ConfigurationException) {
            throw e
        } catch (e: Exception) {
            throw ConfigurationException("快捷键设置保存失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun reset() {
        val state = service.buildEditableState(keymapProvider())
        loadedState = state
        panel?.setState(state)
    }

    override fun disposeUIResources() {
        panel?.dispose()
        panel = null
        loadedState = null
    }

    fun panel(): ShortcutSettingsPanel {
        return requireNotNull(panel)
    }

    companion object {
        internal fun buildConflictMessage(conflicts: List<ShortcutConflict>): String {
            val lines = conflicts.joinToString("\n") { conflict ->
                "- ${conflict.ownerDisplayName} (${conflict.ownerActionId}) <- ${conflict.requestedByDisplayName} / ${KeymapUtil.getShortcutText(conflict.requestedShortcut)}"
            }
            return "以下快捷键已被其他 Action 占用，无法保存：\n$lines"
        }
    }
}
