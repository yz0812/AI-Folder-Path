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
import com.github.aifolderpath.settings.NotificationSettings
import java.awt.datatransfer.StringSelection

class CopyAITreeAction : AnAction() {
    private val log = Logger.getInstance(CopyAITreeAction::class.java)

    /**
     * 复制目录树或多选树形摘要。
     *
     * - 单个目录：输出该目录的摘要树。
     * - 多选文件/目录：输出合并后的选择树。
     *
     * 目标是给 AI 提供比单纯路径列表更容易理解的层级结构。
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = getSelectedVirtualFiles(e)
        if (selectedFiles.isEmpty()) {
            return
        }

        val result = when {
            selectedFiles.size == 1 && selectedFiles.first().isDirectory -> {
                PathTreeFormatter.formatDirectorySummary(project, selectedFiles.first())
            }
            else -> PathTreeFormatter.formatSelection(project, selectedFiles)
        }

        log.info("AIFolderPath(Tree): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    /**
     * 统一获取当前选择的 VirtualFile 列表。
     *
     * IntelliJ 在不同入口下可能只给单个文件，也可能给数组，
     * 这里统一收敛成 List。
     */
    private fun getSelectedVirtualFiles(e: AnActionEvent): List<VirtualFile> {
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (!selectedFiles.isNullOrEmpty()) {
            return selectedFiles.toList()
        }
        return e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::listOf).orEmpty()
    }

    /**
     * 统一发送复制完成通知。
     */
    private fun notify(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        if (type == NotificationType.INFORMATION && !NotificationSettings.getInstance().copyNotificationEnabled) return
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Tree Copied", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Tree): notification failed", ex)
        }
    }

    /**
     * update 放后台线程执行。
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 只要当前有选中文件或目录，就允许显示该动作。
     */
    override fun update(e: AnActionEvent) {
        val selectedFiles = getSelectedVirtualFiles(e)
        e.presentation.isEnabledAndVisible = selectedFiles.isNotEmpty()
    }
}
