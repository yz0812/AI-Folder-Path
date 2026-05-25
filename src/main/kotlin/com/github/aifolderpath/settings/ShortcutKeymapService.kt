package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.ConfigurationException

open class ShortcutKeymapService(
    private val activeKeymapProvider: () -> Keymap = {
        requireNotNull(KeymapManager.getInstance().activeKeymap) { "Active keymap is unavailable" }
    },
    private val keymapRegistry: ShortcutKeymapRegistry = IdeShortcutKeymapRegistry(),
) {
    private fun availableDefinitions(): List<ShortcutActionDefinition> {
        val actionManager = ActionManager.getInstance()
        return PluginShortcutDefinitions.all.filter { definition ->
            actionManager.getAction(definition.actionId) != null
        }
    }

    fun readPageState(): ShortcutPageState = readPageState(activeKeymapProvider())

    fun readPageState(keymap: Keymap): ShortcutPageState {
        return ShortcutPageState(
            keymapName = keymap.presentableName,
            isReadOnly = !keymap.canModify(),
            actions = availableDefinitions().map { definition ->
                buildActionState(definition, keymap)
            },
        )
    }

    fun buildEditableState(): EditableShortcutPage = buildEditableState(activeKeymapProvider())

    fun buildEditableState(keymap: Keymap): EditableShortcutPage {
        val editableKeymap = keymap.toEditableKeymap()
        return EditableShortcutPage(
            originalKeymapName = keymap.presentableName,
            originalKeymap = keymap,
            editableKeymapName = editableKeymap.presentableName,
            editableKeymap = editableKeymap,
            readOnly = !keymap.canModify(),
            cards = availableDefinitions().map { definition ->
                val keyboardShortcuts = editableKeymap.getShortcuts(definition.actionId)
                    .filterIsInstance<KeyboardShortcut>()
                    .sortedBy { KeymapUtil.getShortcutText(it) }
                val firstShortcut = keyboardShortcuts.firstOrNull()
                val hasUnsupportedShortcut = keyboardShortcuts.any { it.secondKeyStroke != null }
                EditableShortcutCard(
                    definition = definition,
                    originalShortcut = firstShortcut,
                    editedShortcut = firstShortcut,
                    hasMultipleBindings = keyboardShortcuts.size > 1,
                    statusMessage = when {
                        hasUnsupportedShortcut -> "只支持单击组合键"
                        keyboardShortcuts.size > 1 -> "当前为多绑定状态，保存后会替换为单个快捷键"
                        else -> null
                    },
                    validationMessage = if (hasUnsupportedShortcut) "只支持单击组合键" else null,
                )
            },
        )
    }

    fun validateActiveKeymap(activeKeymap: Keymap, editablePage: EditableShortcutPage) {
        if (activeKeymap !== editablePage.originalKeymap) {
            throw ConfigurationException("当前 Keymap 已变化，请刷新页面后重试")
        }
    }

    fun detectConflicts(editablePage: EditableShortcutPage): List<ShortcutConflict> {
        return detectConflicts(editablePage.editableKeymap, editablePage)
    }

    fun detectConflicts(keymap: Keymap, editablePage: EditableShortcutPage): List<ShortcutConflict> {
        val managedActionIds = editablePage.cards.map { it.definition.actionId }.toSet()
        val duplicateManagedShortcuts = editablePage.cards
            .mapNotNull { it.editedShortcut }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
        require(duplicateManagedShortcuts.isEmpty()) { "两个插件动作不能共用同一个快捷键" }

        val releasingActionIdsByShortcut = editablePage.cards
            .flatMap { card ->
                keymap.getShortcuts(card.definition.actionId)
                    .filterIsInstance<KeyboardShortcut>()
                    .filterNot { it == card.editedShortcut }
                    .map { it to card.definition.actionId }
            }
            .groupBy({ it.first }, { it.second })

        return editablePage.cards.flatMap { card ->
            val targetShortcut = card.editedShortcut ?: return@flatMap emptyList()
            val releasingOwners = releasingActionIdsByShortcut[targetShortcut].orEmpty().toSet()
            val ignoredOwners = ignoredConflictOwners(card.definition.actionId)

            keymap.getActionIdList(targetShortcut)
                .filterNot { it == card.definition.actionId }
                .filterNot { it in managedActionIds && it in releasingOwners }
                .filterNot { it in ignoredOwners }
                .distinct()
                .map { ownerActionId ->
                    val ownerDisplayName = ActionManager.getInstance().getAction(ownerActionId)?.templateText ?: ownerActionId
                    ShortcutConflict(
                        ownerActionId = ownerActionId,
                        requestedByActionId = card.definition.actionId,
                        requestedShortcut = targetShortcut,
                        ownerDisplayName = ownerDisplayName,
                        requestedByDisplayName = card.definition.title,
                    )
                }
        }
    }

    fun applyChanges(editablePage: EditableShortcutPage): Keymap {
        val editableKeymap = editablePage.editableKeymap
        applyChanges(editableKeymap, editablePage)
        if (editableKeymap !== editablePage.originalKeymap) {
            if (keymapRegistry.allKeymaps().none { it.name == editableKeymap.name }) {
                keymapRegistry.addKeymap(editableKeymap)
            }
            keymapRegistry.activateKeymap(editableKeymap)
        }
        return editableKeymap
    }

    open fun applyChanges(
        targetKeymap: Keymap,
        editablePage: EditableShortcutPage,
    ) {
        val actionIdsToRewrite = editablePage.cards
            .flatMap { card -> setOf(card.definition.actionId) + ignoredConflictOwners(card.definition.actionId) }
            .toSet()
        val originalShortcuts = actionIdsToRewrite.associateWith { actionId ->
            targetKeymap.getShortcuts(actionId).filterIsInstance<KeyboardShortcut>()
        }

        try {
            actionIdsToRewrite.forEach { actionId ->
                targetKeymap.getShortcuts(actionId)
                    .filterIsInstance<KeyboardShortcut>()
                    .forEach { shortcut -> targetKeymap.removeShortcut(actionId, shortcut) }
            }

            editablePage.cards.forEach { card ->
                card.editedShortcut?.let { shortcut ->
                    targetKeymap.addShortcut(card.definition.actionId, shortcut)
                }
            }
        } catch (e: Exception) {
            restoreShortcuts(targetKeymap, originalShortcuts)
            throw e
        }
    }

    private fun restoreShortcuts(
        targetKeymap: Keymap,
        originalShortcuts: Map<String, List<KeyboardShortcut>>,
    ) {
        originalShortcuts.forEach { (actionId, shortcuts) ->
            targetKeymap.getShortcuts(actionId)
                .filterIsInstance<KeyboardShortcut>()
                .forEach { shortcut -> targetKeymap.removeShortcut(actionId, shortcut) }
            shortcuts.forEach { shortcut -> targetKeymap.addShortcut(actionId, shortcut) }
        }
    }

    private fun ignoredConflictOwners(actionId: String): Set<String> {
        return when (actionId) {
            "AIFolderPath.CopyOptionsAction" -> setOf("AIFolderPath.CopyAction")
            else -> emptySet()
        }
    }

    private fun buildActionState(
        definition: ShortcutActionDefinition,
        keymap: Keymap,
    ): ShortcutActionState {
        val shortcutTexts = keymap.getShortcuts(definition.actionId)
            .filterIsInstance<KeyboardShortcut>()
            .mapNotNull(::toShortcutText)
            .sorted()

        return ShortcutActionState(
            actionId = definition.actionId,
            label = definition.label,
            defaultShortcut = definition.defaultShortcut,
            shortcutText = shortcutTexts.firstOrNull().orEmpty(),
            hasMultipleBindings = shortcutTexts.size > 1,
        )
    }

    private fun toShortcutText(shortcut: Shortcut): String? {
        return runCatching { KeymapUtil.getShortcutText(shortcut) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun Keymap.toEditableKeymap(): Keymap {
        if (canModify()) {
            return this
        }
        return deriveKeymap(nextEditableKeymapName(this))
    }

    private fun nextEditableKeymapName(keymap: Keymap): String {
        val existingNames = keymapRegistry.allKeymaps().mapTo(mutableSetOf()) { it.name }
        val baseName = "${keymap.presentableName} (AI Folder Path)"
        if (baseName !in existingNames) {
            return baseName
        }

        var index = 1
        while (true) {
            val candidate = "$baseName $index"
            if (candidate !in existingNames) {
                return candidate
            }
            index++
        }
    }
}

interface ShortcutKeymapRegistry {
    fun allKeymaps(): List<Keymap>

    fun addKeymap(keymap: Keymap)

    fun activateKeymap(keymap: Keymap)
}

private class IdeShortcutKeymapRegistry : ShortcutKeymapRegistry {
    private val manager: KeymapManagerEx
        get() = KeymapManagerEx.getInstanceEx()

    override fun allKeymaps(): List<Keymap> = manager.allKeymaps.toList()

    override fun addKeymap(keymap: Keymap) {
        manager.schemeManager.addScheme(keymap)
    }

    override fun activateKeymap(keymap: Keymap) {
        manager.setActiveKeymap(keymap)
    }
}
