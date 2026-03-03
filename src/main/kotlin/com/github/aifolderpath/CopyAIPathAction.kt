package com.github.aifolderpath

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import java.awt.datatransfer.StringSelection

class CopyAIPathAction : AnAction() {

    private val log = Logger.getInstance(CopyAIPathAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        log.info("AIFolderPath: actionPerformed triggered")
        val project = e.project ?: run { log.warn("AIFolderPath: project is null"); return }
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: run { log.warn("AIFolderPath: psiFile is null"); return }
        val virtualFile = psiFile.virtualFile ?: run { log.warn("AIFolderPath: virtualFile is null"); return }

        val basePath = PathResolver.resolve(project, virtualFile)
        log.info("AIFolderPath: basePath=$basePath")

        val result: String = if (editor != null) {
            buildFromEditor(editor, psiFile, basePath)
        } else {
            basePath
        }

        log.info("AIFolderPath: copying result=$result")
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

    private fun buildFromEditor(
        editor: com.intellij.openapi.editor.Editor,
        psiFile: PsiFile,
        basePath: String
    ): String {
        val selectionModel = editor.selectionModel
        val document = editor.document

        // 有选区：判断是选中了标识符还是代码片段
        if (selectionModel.hasSelection()) {
            val startOffset = selectionModel.selectionStart
            val endOffset = selectionModel.selectionEnd
            val selectedText = selectionModel.selectedText ?: return basePath

            val elementAtStart = psiFile.findElementAt(startOffset)

            // 检查选中的是否是一个标识符（类名或方法名）
            if (isIdentifierSelection(selectedText, elementAtStart)) {
                val resolvedElement = resolveIdentifier(elementAtStart)
                return when (resolvedElement) {
                    is PsiMethod -> "$basePath ${buildMethodSignature(resolvedElement)}"
                    is PsiClass -> basePath
                    else -> basePath
                }
            }

            // 选中的是代码片段
            val containingMethod = findContainingMethod(elementAtStart)
            val methodSuffix = if (containingMethod != null) " ${containingMethod.name}" else ""

            val startLine = document.getLineNumber(startOffset)
            val endLine = document.getLineNumber(endOffset)

            val sb = StringBuilder()
            sb.append(basePath).append(methodSuffix)
            for (line in startLine..endLine) {
                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
                val lineNum = line + 1  // 转为1-based行号
                sb.append("\n${lineNum}line    $lineText")
            }
            return sb.toString()
        }

        // 无选区：基于光标位置
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
        val resolved = resolveIdentifier(element)

        return when (resolved) {
            is PsiMethod -> "$basePath ${buildMethodSignature(resolved)}"
            is PsiClass -> basePath
            else -> basePath
        }
    }

    /**
     * 判断选中内容是否为单个标识符（类名/方法名）
     */
    private fun isIdentifierSelection(text: String, element: PsiElement?): Boolean {
        if (text.contains('\n') || text.contains(';') || text.contains('{')) return false
        val trimmed = text.trim()
        return trimmed.matches(Regex("[a-zA-Z_$][a-zA-Z0-9_$]*"))
    }

    /**
     * 从标识符元素解析到其声明（PsiClass / PsiMethod）
     */
    private fun resolveIdentifier(element: PsiElement?): PsiElement? {
        if (element == null) return null

        // 向上查找方法或类声明
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
        if (method != null && isOnMethodName(element, method)) {
            return method
        }

        val clazz = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false)
        if (clazz != null && isOnClassName(element, clazz)) {
            return clazz
        }

        // 尝试引用解析
        val parent = element.parent
        if (parent is PsiReference) {
            val resolved = parent.resolve()
            if (resolved is PsiMethod || resolved is PsiClass) return resolved
        }

        return method ?: clazz
    }

    private fun isOnMethodName(element: PsiElement, method: PsiMethod): Boolean {
        val nameId = method.nameIdentifier ?: return false
        return element.textRange.intersects(nameId.textRange)
    }

    private fun isOnClassName(element: PsiElement, clazz: PsiClass): Boolean {
        val nameId = clazz.nameIdentifier ?: return false
        return element.textRange.intersects(nameId.textRange)
    }

    private fun findContainingMethod(element: PsiElement?): PsiMethod? {
        return PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, true)
    }

    /**
     * 构建方法签名：methodName(Type1 param1, Type2 param2): ReturnType
     */
    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { param ->
            "${param.type.presentableText} ${param.name}"
        }
        val returnType = method.returnType?.presentableText ?: "void"
        return "${method.name}($params): $returnType"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = psiFile != null
    }
}
