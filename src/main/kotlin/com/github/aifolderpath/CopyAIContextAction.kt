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

class CopyAIContextAction : AnAction() {
    private val log = Logger.getInstance(CopyAIContextAction::class.java)

    /**
     * 复制当前符号或选区的上下文块。
     *
     * 优先从编辑器当前位置解析出结构化上下文，输出 `path/class/method/lines` 这类多行信息；
     * 如果解析失败，则至少回退为纯路径，保证动作始终可用。
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val virtualFile = psiFile.virtualFile ?: return
        val context = EditorSymbolContextResolver.resolve(project, editor, psiFile)
        val result = context?.let(OutputFormatter::formatContextBlock)
            ?: OutputFormatter.formatPath(PathResolver.resolve(project, virtualFile))

        log.info("AIFolderPath(Context): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    /**
     * 统一发送复制完成通知。
     *
     * 通知属于辅助反馈，失败时只打日志，不影响主流程。
     */
    private fun notify(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        if (type == NotificationType.INFORMATION && !NotificationSettings.getInstance().copyNotificationEnabled) return
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Context Copied", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Context): notification failed", ex)
        }
    }

    /**
     * update 放后台线程，避免 UI 线程做无谓工作。
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 只有编辑器与 PSI 文件同时存在时，动作才显示。
     */
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }
}
