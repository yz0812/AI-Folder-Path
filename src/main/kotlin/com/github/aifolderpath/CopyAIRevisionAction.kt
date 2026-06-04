package com.github.aifolderpath

import com.github.aifolderpath.settings.NotificationSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsLogDataKeys
import java.awt.datatransfer.StringSelection

class CopyAIRevisionAction : AnAction() {
    private val log = Logger.getInstance(CopyAIRevisionAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
        val commits = selection.commits
        if (commits.isEmpty()) return

        val selectedFiles = selectedFiles(e)
        val result = if (selectedFiles.isEmpty()) {
            commits.joinToString("\n") { it.hash.asString() }
        } else {
            val revision = commits.first().hash.asString()
            selectedFiles.joinToString("\n") { file ->
                "revision=$revision file=${PathResolver.resolvePath(project, file.path)} status=${file.status}"
            }
        }

        log.info("AIFolderPath(Revision): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    private fun selectedFiles(e: AnActionEvent): List<RevisionFile> {
        val changes = e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS)
            ?.takeIf { it.isNotEmpty() }
            ?: e.getData(VcsDataKeys.CURRENT_CHANGE)?.let { arrayOf(it) }
            ?: return emptyList()

        return changes.mapNotNull(::fileFromChange).distinct()
    }

    private fun fileFromChange(change: Change): RevisionFile? {
        val path = change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path ?: return null
        return RevisionFile(statusFromChange(change), path)
    }

    private fun statusFromChange(change: Change): String {
        return when (change.type) {
            Change.Type.NEW -> "新增"
            Change.Type.MODIFICATION -> "修改"
            Change.Type.DELETED -> "删除"
            Change.Type.MOVED -> "移动/重命名"
        }
    }

    private data class RevisionFile(
        val status: String,
        val path: String,
    )

    private fun notify(project: Project, content: String, type: NotificationType) {
        if (type == NotificationType.INFORMATION && !NotificationSettings.getInstance().copyNotificationEnabled) return
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Revision Copied", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Revision): notification failed", ex)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
        e.presentation.isEnabledAndVisible = e.project != null && selection?.commits?.isNotEmpty() == true
    }
}
