package com.github.aifolderpath

import com.github.aifolderpath.settings.NotificationSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsRef
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CopyAIGitInfoAction : AnAction() {
    private val log = Logger.getInstance(CopyAIGitInfoAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
        val refs = e.getData(VcsLogDataKeys.VCS_LOG_REFS).orEmpty()
        val cachedDetails = selection.cachedFullDetails

        if (cachedDetails.size == selection.commits.size) {
            copy(project, cachedDetails, refs)
            return
        }

        selection.requestFullDetails { details ->
            ApplicationManager.getApplication().invokeLater {
                if (details.isEmpty()) {
                    notify(project, "未找到选中提交详情", NotificationType.WARNING)
                } else {
                    copy(project, details.toList(), refs)
                }
            }
        }
    }

    private fun copy(project: Project, details: List<VcsFullCommitDetails>, refs: List<VcsRef>) {
        val result = details.joinToString("\n\n") { detail -> formatCommit(project, detail, refs) }
        log.info("AIFolderPath(Git): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    private fun formatCommit(project: Project, detail: VcsFullCommitDetails, refs: List<VcsRef>): String {
        val commitRefs = refs
            .filter { it.commitHash == detail.id && it.root == detail.root }
            .joinToString(", ") { it.name }
        val changes = detail.changes.toList()
        return buildList {
            add("commit: ${detail.id.asString()}")
            add("short: ${detail.id.toShortString()}")
            add("author: ${detail.author.name} <${detail.author.email}>")
            add("time: ${formatTime(detail.authorTime)}")
            add("subject: ${detail.subject}")
            if (commitRefs.isNotBlank()) add("refs: $commitRefs")
            add("root: ${detail.root.path.replace('\\', '/')}")
            add("files:")
            if (changes.isEmpty()) {
                add("- (none)")
            } else {
                changes.take(MAX_CHANGED_FILES).forEach { change ->
                    add("- ${formatChange(project, change)}")
                }
                if (changes.size > MAX_CHANGED_FILES) {
                    add("- ... +${changes.size - MAX_CHANGED_FILES} more files")
                }
            }
        }.joinToString("\n")
    }

    private fun formatChange(project: Project, change: Change): String {
        val beforePath = change.beforeRevision?.file?.path?.let { normalizePath(project, it) }
        val afterPath = change.afterRevision?.file?.path?.let { normalizePath(project, it) }
        return when (change.type) {
            Change.Type.NEW -> "A ${afterPath ?: beforePath.orEmpty()}"
            Change.Type.DELETED -> "D ${beforePath ?: afterPath.orEmpty()}"
            Change.Type.MOVED -> "R ${beforePath.orEmpty()} -> ${afterPath ?: beforePath.orEmpty()}"
            Change.Type.MODIFICATION -> "M ${afterPath ?: beforePath.orEmpty()}"
        }
    }

    private fun normalizePath(project: Project, path: String): String {
        return PathResolver.resolvePath(project, path)
    }

    private fun formatTime(timestamp: Long): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        if (type == NotificationType.INFORMATION && !NotificationSettings.getInstance().copyNotificationEnabled) return
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Git Info Copied", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Git): notification failed", ex)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
        e.presentation.isEnabledAndVisible = e.project != null && selection?.commits?.isNotEmpty() == true
    }

    companion object {
        private const val MAX_CHANGED_FILES = 50
    }
}
