package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.KeyboardShortcut
import javax.swing.KeyStroke

data class ShortcutActionDefinition(
    val actionId: String,
    val label: String,
    val defaultShortcut: KeyboardShortcut,
    val description: String,
) {
    val title: String
        get() = label
}

object PluginShortcutDefinitions {
    val copyAi = ShortcutActionDefinition(
        actionId = "AIFolderPath.CopyOptionsAction",
        label = "Copy AI",
        defaultShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("alt P"), null),
        description = "按当前 Alt+P 默认动作复制 AI 路径、增强引用或 Git 修订号",
    )

    val copyAiUsages = ShortcutActionDefinition(
        actionId = "AIFolderPath.CopyRefAction",
        label = "Copy AI Usages",
        defaultShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("ctrl alt 0"), null),
        description = "复制定义和前 10 个 usages（仅在增强动作可用时显示）",
    )

    val all = listOf(copyAi, copyAiUsages)
}
