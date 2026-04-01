package com.github.aifolderpath

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

class CopyAIPathAction : AnAction() {

    private val log = Logger.getInstance(CopyAIPathAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        log.info("AIFolderPath: actionPerformed triggered")
        val project = e.project ?: run { log.warn("AIFolderPath: project is null"); return }
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectedFiles = getSelectedVirtualFiles(e)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val virtualFile = selectedFiles.firstOrNull() ?: psiFile?.virtualFile ?: run {
            log.warn("AIFolderPath: virtualFile is null")
            return
        }

        val result = when {
            selectedFiles.size > 1 -> formatSelectedPaths(project, selectedFiles)
            virtualFile.isDirectory -> PathResolver.resolveDirectory(project, virtualFile)
            editor != null && psiFile != null -> {
                val basePath = PathResolver.resolve(project, virtualFile)
                buildFromEditor(editor, basePath)
            }
            else -> PathResolver.resolve(project, virtualFile)
        }
        log.info("AIFolderPath: result=$result")

        CopyPasteManager.getInstance().setContents(StringSelection(result))

        // 气泡通知
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Path Copied", result, NotificationType.INFORMATION)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath: notification failed", ex)
        }
    }

    private fun getSelectedVirtualFiles(e: AnActionEvent): List<VirtualFile> {
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (!selectedFiles.isNullOrEmpty()) {
            return selectedFiles.toList()
        }
        return e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::listOf).orEmpty()
    }

    private fun formatSelectedPaths(
        project: com.intellij.openapi.project.Project,
        selectedFiles: List<VirtualFile>,
    ): String {
        return selectedFiles
            .distinctBy { it.path }
            .joinToString("\n") { file ->
                if (file.isDirectory) {
                    PathResolver.resolveDirectory(project, file)
                } else {
                    PathResolver.resolve(project, file)
                }
            }
    }

    private fun buildFromEditor(
        editor: com.intellij.openapi.editor.Editor,
        basePath: String,
    ): String {
        val selectionModel = editor.selectionModel
        val document = editor.document

        if (!selectionModel.hasSelection()) {
            return basePath
        }

        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd
        val selectedText = selectionModel.selectedText ?: return basePath
        val startLine = document.getLineNumber(startOffset) + 1
        val endLine = document.getLineNumber(if (endOffset > startOffset) endOffset - 1 else endOffset) + 1
        val trimmedSelectedText = selectedText.trim()
        val selectedLineCount = endLine - startLine + 1

        if (trimmedSelectedText.isEmpty()) {
            return basePath
        }

        return if (selectedLineCount <= 2) {
            "$basePath $trimmedSelectedText"
        } else {
            "$basePath lines $startLine-$endLine"
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val selectedFiles = getSelectedVirtualFiles(e)
        e.presentation.isEnabledAndVisible = psiFile != null || selectedFiles.isNotEmpty()
    }
}
