package com.github.aifolderpath

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.github.aifolderpath.settings.NotificationSettings
import java.awt.datatransfer.StringSelection

class CopyAISymbolAnchorAction : AnAction() {
    private val log = Logger.getInstance(CopyAISymbolAnchorAction::class.java)

    /**
     * 复制当前符号锚点。
     *
     * 这是最紧凑的输出形式：优先给出 `路径 + 符号 + 行号范围`。
     * 如果当前位置无法解析出符号上下文，则退回为纯路径，避免动作失效。
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val virtualFile = psiFile.virtualFile ?: return
        val context = EditorSymbolContextResolver.resolve(project, editor, psiFile)
        val result = context?.let(OutputFormatter::formatAnchor)
            ?: OutputFormatter.formatPath(PathResolver.resolve(project, virtualFile))

        log.info("AIFolderPath(Anchor): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    /**
     * 统一发送复制完成通知。
     */
    private fun notify(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        if (type == NotificationType.INFORMATION && !NotificationSettings.getInstance().copyNotificationEnabled) return
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Anchor Copied", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Anchor): notification failed", ex)
        }
    }

    /**
     * update 放后台线程执行。
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 只有编辑器和 PSI 文件可用时才展示动作。
     */
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }
}
