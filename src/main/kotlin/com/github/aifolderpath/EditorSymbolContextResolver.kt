package com.github.aifolderpath

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner

object EditorSymbolContextResolver {
    data class EditorSymbolContext(
        val path: String,
        val currentLine: Int,
        val startLine: Int,
        val endLine: Int,
        val className: String?,
        val methodSignature: String?,
        val isExactMethodNameSelection: Boolean,
    ) {
        fun lineText(): String {
            return if (startLine == endLine) "L$startLine" else "L$startLine-L$endLine"
        }

        fun currentLineText(): String = "L$currentLine"

        fun symbolText(): String? {
            return when {
                methodSignature != null && className != null -> "$className.$methodSignature"
                methodSignature != null -> methodSignature
                className != null -> className
                else -> null
            }
        }
    }

    private data class NamedContext(
        val function: PsiNameIdentifierOwner?,
        val type: PsiNameIdentifierOwner?,
        val fallback: PsiNameIdentifierOwner?,
    ) {
        val lineTarget: PsiNameIdentifierOwner?
            get() = function ?: type ?: fallback
    }

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
        val namedContext = findNamedContext(element)
        val isExactFunctionNameSelection = namedContext.function?.let {
            useExplicitRange && isNameSelection(it, rangeStartOffset, rangeEndOffset)
        } == true
        val lineTarget = if (useExplicitRange) {
            namedContext.function?.takeIf { isExactFunctionNameSelection }
        } else {
            namedContext.lineTarget
        }
        val path = PathResolver.resolve(project, virtualFile)

        val startLine = lineTarget?.textRange?.startOffset?.let { toLineNumber(document, it) }
            ?: if (useExplicitRange) toLineNumber(document, rangeStartOffset) else currentLine
        val endLine = lineTarget?.textRange?.let { range ->
            val endOffset = normalizeEndOffset(range.startOffset, range.endOffset)
            toLineNumber(document, endOffset)
        } ?: if (useExplicitRange) toLineNumber(document, rangeEndOffset) else currentLine

        val functionName = namedContext.function?.name?.takeIf { it.isNotBlank() }
        val typeName = namedContext.type?.name?.takeIf { it.isNotBlank() }
        val fallbackName = namedContext.fallback?.name?.takeIf { it.isNotBlank() }
        return EditorSymbolContext(
            path = path,
            currentLine = currentLine,
            startLine = startLine,
            endLine = endLine,
            className = typeName ?: if (functionName == null) fallbackName else null,
            methodSignature = functionName,
            isExactMethodNameSelection = isExactFunctionNameSelection,
        )
    }

    private fun findNamedContext(element: PsiElement?): NamedContext {
        var current = element
        var function: PsiNameIdentifierOwner? = null
        var type: PsiNameIdentifierOwner? = null
        var fallback: PsiNameIdentifierOwner? = null

        while (current != null) {
            val owner = current as? PsiNameIdentifierOwner
            if (owner != null && owner.nameIdentifier != null && !owner.name.isNullOrBlank()) {
                when {
                    function == null && isFunctionLike(owner) -> function = owner
                    type == null && isTypeLike(owner) -> type = owner
                    fallback == null -> fallback = owner
                }
            }
            current = current.parent
        }
        return NamedContext(function = function, type = type, fallback = fallback)
    }

    private fun isFunctionLike(owner: PsiNameIdentifierOwner): Boolean {
        val className = owner.javaClass.name.lowercase()
        return "method" in className || "function" in className || "fun" in className
    }

    private fun isTypeLike(owner: PsiNameIdentifierOwner): Boolean {
        val className = owner.javaClass.name.lowercase()
        return "class" in className || "interface" in className || "enum" in className || "objectdeclaration" in className
    }

    private fun normalizeEndOffset(startOffset: Int, endOffset: Int): Int {
        return if (endOffset > startOffset) endOffset - 1 else endOffset
    }

    private fun isNameSelection(symbol: PsiNameIdentifierOwner, selectionStartOffset: Int, selectionEndOffset: Int): Boolean {
        val nameId = symbol.nameIdentifier ?: return false
        return nameId.textRange.startOffset == selectionStartOffset &&
            normalizeEndOffset(nameId.textRange.startOffset, nameId.textRange.endOffset) == selectionEndOffset
    }

    private fun toLineNumber(document: Document?, offset: Int): Int {
        if (document == null || document.textLength == 0) {
            return 1
        }
        val boundedOffset = offset.coerceIn(0, document.textLength - 1)
        return document.getLineNumber(boundedOffset) + 1
    }
}
