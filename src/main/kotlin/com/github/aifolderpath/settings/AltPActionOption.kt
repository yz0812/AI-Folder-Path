package com.github.aifolderpath.settings

import com.intellij.ide.util.PropertiesComponent

enum class AltPActionOption(
    val id: String,
    val label: String,
    val actionId: String,
) {
    CopyAnchor(
        id = "copy-anchor",
        label = "Copy AI Anchor",
        actionId = "AIFolderPath.CopyAnchorAction",
    ),
    CopyContext(
        id = "copy-context",
        label = "Copy AI Context",
        actionId = "AIFolderPath.CopyContextAction",
    ),
    CopyPath(
        id = "copy-path",
        label = "Copy AI Path",
        actionId = "AIFolderPath.CopyAction",
    ),
    CopyTree(
        id = "copy-tree",
        label = "Copy AI Tree",
        actionId = "AIFolderPath.CopyTreeAction",
    );

    override fun toString(): String = label

    companion object {
        fun fromId(value: String?): AltPActionOption {
            return entries.firstOrNull { it.id == value } ?: CopyPath
        }
    }
}

object AltPActionOptionStore {
    private const val KEY = "com.github.aifolderpath.altPActionOption"

    fun get(): AltPActionOption {
        return AltPActionOption.fromId(PropertiesComponent.getInstance().getValue(KEY))
    }

    fun set(value: AltPActionOption) {
        PropertiesComponent.getInstance().setValue(KEY, value.id, AltPActionOption.CopyPath.id)
    }
}
