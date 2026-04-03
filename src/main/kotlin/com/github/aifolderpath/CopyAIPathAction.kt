package com.github.aifolderpath

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.awt.datatransfer.StringSelection

class CopyAIPathAction : AnAction() {

    private val log = Logger.getInstance(CopyAIPathAction::class.java)

    /**
     * 入口动作：根据当前上下文决定复制什么格式的 AI 路径。
     *
     * 支持三类主要场景：
     * 1. 项目视图多选：逐个输出路径。
     * 2. 项目视图单目录：输出目录路径。
     * 3. 编辑器内文件：在基础路径上追加选区行信息。
     *
     * 逻辑保持非常直接，不做额外抽象，方便和 IntelliJ Action 上下文一一对应。
     */
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
            // 多选时按路径列表输出，不尝试混入编辑器语义，避免结果不稳定。
            selectedFiles.size > 1 -> formatSelectedPaths(project, selectedFiles)
            // 目录单选单独走目录格式，保留尾部分隔符。
            virtualFile.isDirectory -> PathResolver.resolveDirectory(project, virtualFile)
            // 编辑器场景在基础路径上追加选区对应的行信息。
            editor != null && psiFile != null -> {
                val basePath = PathResolver.resolve(project, virtualFile)
                buildFromEditor(project, editor, psiFile, basePath)
            }
            // 其余情况退回纯文件路径。
            else -> PathResolver.resolve(project, virtualFile)
        }
        log.info("AIFolderPath: result=$result")

        CopyPasteManager.getInstance().setContents(StringSelection(result))

        // 通知失败不影响主流程，剪贴板写入成功才是核心目标。
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Path Copied", result, NotificationType.INFORMATION)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath: notification failed", ex)
        }
    }

    /**
     * 统一读取当前 Action 上下文中的文件选择结果。
     *
     * IntelliJ 在不同入口下可能给数组，也可能只给单个文件，
     * 这里做一次收敛，避免后续逻辑到处判断两套数据源。
     */
    private fun getSelectedVirtualFiles(e: AnActionEvent): List<VirtualFile> {
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (!selectedFiles.isNullOrEmpty()) {
            return selectedFiles.toList()
        }
        return e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::listOf).orEmpty()
    }

    /**
     * 把多选文件/目录格式化为多行输出。
     *
     * 这里先按物理路径去重，避免项目视图某些选择状态下同一路径重复出现。
     */
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

    /**
     * 在编辑器场景下，把基础路径扩展为“路径 + 选区位置信息”。
     *
     * 规则很简单：
     * - 没选中内容：只返回基础路径。
     * - 精确选中方法名：输出整个方法的 `Lx-Ly + 方法名`。
     * - 普通单行选区：输出 `Lx + 选中文本`。
     * - 多行选区：输出 `Lx-Ly`，不直接拼接整段代码，避免剪贴板内容过长。
     */
    private fun buildFromEditor(
        project: Project,
        editor: com.intellij.openapi.editor.Editor,
        psiFile: PsiFile,
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
        val trimmedSelectedText = selectedText.trim()
        if (trimmedSelectedText.isEmpty()) {
            return basePath
        }

        val context = EditorSymbolContextResolver.resolve(project, editor, psiFile)
        val methodLineText = context?.takeIf { it.isExactMethodNameSelection }?.lineText()
        if (methodLineText != null) {
            return "$basePath $methodLineText $trimmedSelectedText"
        }

        val startLine = document.getLineNumber(startOffset) + 1
        val endLine = document.getLineNumber(if (endOffset > startOffset) endOffset - 1 else endOffset) + 1
        val lineText = if (startLine == endLine) "L$startLine" else "L$startLine-L$endLine"
        return if (startLine == endLine) {
            "$basePath $lineText $trimmedSelectedText"
        } else {
            "$basePath $lineText"
        }
    }

    /**
     * IntelliJ 建议把 update 放到后台线程执行，避免阻塞 UI。
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 只有当前上下文能拿到 PSI 文件或已选文件时，动作才显示。
     *
     * 这保证菜单不会在无意义场景下出现。
     */
    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val selectedFiles = getSelectedVirtualFiles(e)
        e.presentation.isEnabledAndVisible = psiFile != null || selectedFiles.isNotEmpty()
    }
}
