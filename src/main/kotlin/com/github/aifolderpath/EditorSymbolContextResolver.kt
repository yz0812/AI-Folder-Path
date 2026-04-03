package com.github.aifolderpath

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

object EditorSymbolContextResolver {
    /**
     * 统一承载“编辑器当前位置/选区”解析后的上下文信息。
     *
     * 这里不直接绑定某一种输出格式，而是只提供路径、行号、类名、方法签名等
     * 原始语义，方便不同复制动作按自己的格式做二次拼装。
     */
    data class EditorSymbolContext(
        val path: String,
        val currentLine: Int,
        val startLine: Int,
        val endLine: Int,
        val className: String?,
        val methodSignature: String?,
        val isExactMethodNameSelection: Boolean,
    ) {
        /**
         * 返回当前上下文覆盖的行范围文本。
         * 单行返回 `Lx`，多行返回 `Lx-Ly`。
         */
        fun lineText(): String {
            return if (startLine == endLine) "L$startLine" else "L$startLine-L$endLine"
        }

        /**
         * 返回当前光标所在行，主要用于 usages 输出。
         */
        fun currentLineText(): String = "L$currentLine"

        /**
         * 组合类名与方法签名，生成更完整的符号文本。
         *
         * 优先级：类+方法 > 仅方法 > 仅类。
         */
        fun symbolText(): String? {
            return when {
                methodSignature != null && className != null -> "$className.$methodSignature"
                methodSignature != null -> methodSignature
                className != null -> className
                else -> null
            }
        }
    }

    /**
     * 从编辑器当前状态解析上下文。
     *
     * 如果用户有选区，就以选区为准；否则退回到光标所在位置。
     * 这样 Anchor / Context 这类动作就能同时支持“点一下符号”和“框选一段代码”。
     */
    fun resolve(project: Project, editor: Editor, psiFile: PsiFile): EditorSymbolContext? {
        val selectionModel = editor.selectionModel
        val startOffset = if (selectionModel.hasSelection()) {
            selectionModel.selectionStart
        } else {
            editor.caretModel.offset
        }
        val endOffset = if (selectionModel.hasSelection()) {
            normalizeEndOffset(selectionModel.selectionStart, selectionModel.selectionEnd)
        } else {
            startOffset
        }
        return resolve(
            project = project,
            psiFile = psiFile,
            element = psiFile.findElementAt(startOffset),
            currentOffset = startOffset,
            rangeStartOffset = startOffset,
            rangeEndOffset = endOffset,
            useExplicitRange = selectionModel.hasSelection(),
        )
    }

    /**
     * 从任意 PSI 元素解析上下文。
     *
     * 主要给 usages、definition 这类“从符号节点反推显示文本”的场景复用。
     */
    fun resolve(project: Project, element: PsiElement): EditorSymbolContext? {
        val psiFile = element.containingFile ?: return null
        val offset = element.textRange?.startOffset ?: 0
        return resolve(
            project = project,
            psiFile = psiFile,
            element = element,
            currentOffset = offset,
            rangeStartOffset = offset,
            rangeEndOffset = offset,
            useExplicitRange = false,
        )
    }

    /**
     * 核心解析实现。
     *
     * 解析结果由三部分组成：
     * 1. 路径：由 [PathResolver] 统一生成。
     * 2. 符号：优先方法，其次类。
     * 3. 行号范围：显式选区优先；没有选区时退回符号自身范围。
     */
    private fun resolve(
        project: Project,
        psiFile: PsiFile,
        element: PsiElement?,
        currentOffset: Int,
        rangeStartOffset: Int,
        rangeEndOffset: Int,
        useExplicitRange: Boolean,
    ): EditorSymbolContext? {
        val virtualFile = psiFile.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: FileDocumentManager.getInstance().getDocument(virtualFile)
        val currentLine = toLineNumber(document, currentOffset)
        val method = element?.let { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java, false) }
        val clazz = element?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java, false) }
        val target = method ?: clazz
        val isExactMethodNameSelection = method?.let {
            useExplicitRange && isMethodNameSelection(it, rangeStartOffset, rangeEndOffset)
        } == true
        val lineTarget = if (useExplicitRange) {
            method?.takeIf { isExactMethodNameSelection }
        } else {
            target
        }
        val path = PathResolver.resolve(project, virtualFile)

        val startLine = lineTarget?.textRange?.startOffset?.let { toLineNumber(document, it) }
            ?: if (useExplicitRange) toLineNumber(document, rangeStartOffset) else currentLine
        val endLine = lineTarget?.textRange?.let { range ->
            val endOffset = normalizeEndOffset(range.startOffset, range.endOffset)
            toLineNumber(document, endOffset)
        } ?: if (useExplicitRange) toLineNumber(document, rangeEndOffset) else currentLine

        return EditorSymbolContext(
            path = path,
            currentLine = currentLine,
            startLine = startLine,
            endLine = endLine,
            className = method?.containingClass?.name ?: clazz?.name,
            methodSignature = method?.let(::buildMethodSignature),
            isExactMethodNameSelection = isExactMethodNameSelection,
        )
    }

    /**
     * IntelliJ 的 endOffset 常常是“右开区间”，这里把它修正成真实落点。
     * 否则结束行可能被误算到下一行。
     */
    private fun normalizeEndOffset(startOffset: Int, endOffset: Int): Int {
        return if (endOffset > startOffset) endOffset - 1 else endOffset
    }

    /**
     * 只有“精确选中方法名”时，才把行号范围提升为整个方法。
     */
    private fun isMethodNameSelection(method: PsiMethod, selectionStartOffset: Int, selectionEndOffset: Int): Boolean {
        val nameId = method.nameIdentifier ?: return false
        return nameId.textRange.startOffset == selectionStartOffset &&
            normalizeEndOffset(nameId.textRange.startOffset, nameId.textRange.endOffset) == selectionEndOffset
    }

    /**
     * 把文档偏移量安全转换为 1-based 行号。
     *
     * 即使文档为空、offset 越界，也会返回一个稳定结果，避免上层继续做防御判断。
     */
    private fun toLineNumber(document: Document?, offset: Int): Int {
        if (document == null || document.textLength == 0) {
            return 1
        }
        val boundedOffset = offset.coerceIn(0, document.textLength - 1)
        return document.getLineNumber(boundedOffset) + 1
    }

    /**
     * 生成简洁的方法签名，只保留方法名和参数类型。
     *
     * 不包含返回值、修饰符、泛型边界，目的是让复制结果更短、更适合贴给 AI。
     */
    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { param ->
            param.type.presentableText
        }
        return "${method.name}($params)"
    }
}
