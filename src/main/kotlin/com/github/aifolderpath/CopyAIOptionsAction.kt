package com.github.aifolderpath

import com.github.aifolderpath.settings.AltPActionOptionStore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class CopyAIOptionsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        resolveDelegateAction(e)?.actionPerformed(e)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val delegate = resolveDelegateAction(e)
        if (delegate != null) {
            delegate.update(e)
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = e.project != null && (editor != null || !selectedFiles.isNullOrEmpty() || selectedFile != null)
    }

    private fun resolveDelegateAction(e: AnActionEvent): AnAction? {
        val actionManager = ActionManager.getInstance()
        val selectedActionId = AltPActionOptionStore.get().actionId
        val preferredActionId = if (e.getData(CommonDataKeys.EDITOR) == null && selectedActionId in editorOnlyActionIds) {
            COMPAT_PATH_ACTION_ID
        } else {
            selectedActionId
        }
        val preferredAction = actionManager.getAction(preferredActionId)
        if (preferredAction != null) {
            return preferredAction
        }
        return actionManager.getAction(COMPAT_PATH_ACTION_ID)
    }

    companion object {
        private const val COMPAT_PATH_ACTION_ID = "AIFolderPath.CopyAction"
        private val editorOnlyActionIds = setOf(
            "AIFolderPath.CopyAnchorAction",
            "AIFolderPath.CopyContextAction",
        )
    }
}
