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
        val symbol = findNamedSymbol(element)
        val container = findContainingNamedSymbol(symbol)
        val isExactNameSelection = symbol?.let {
            useExplicitRange && isNameSelection(it, rangeStartOffset, rangeEndOffset)
        } == true
        val lineTarget = if (useExplicitRange) {
            symbol?.takeIf { isExactNameSelection }
        } else {
            symbol
        }
        val path = PathResolver.resolve(project, virtualFile)

        val startLine = lineTarget?.textRange?.startOffset?.let { toLineNumber(document, it) }
            ?: if (useExplicitRange) toLineNumber(document, rangeStartOffset) else currentLine
        val endLine = lineTarget?.textRange?.let { range ->
            val endOffset = normalizeEndOffset(range.startOffset, range.endOffset)
            toLineNumber(document, endOffset)
        } ?: if (useExplicitRange) toLineNumber(document, rangeEndOffset) else currentLine

        val symbolName = symbol?.name?.takeIf { it.isNotBlank() }
        val containerName = container?.name?.takeIf { it.isNotBlank() }
        return EditorSymbolContext(
            path = path,
            currentLine = currentLine,
            startLine = startLine,
            endLine = endLine,
            className = containerName ?: symbolName,
            methodSignature = if (containerName != null) symbolName else null,
            isExactMethodNameSelection = isExactNameSelection,
        )
    }

    private fun findNamedSymbol(element: PsiElement?): PsiNameIdentifierOwner? {
        var current = element
        while (current != null) {
            val owner = current as? PsiNameIdentifierOwner
            if (owner != null && owner.nameIdentifier != null && !owner.name.isNullOrBlank()) {
                return owner
            }
            current = current.parent
        }
        return null
    }

    private fun findContainingNamedSymbol(symbol: PsiNameIdentifierOwner?): PsiNameIdentifierOwner? {
        var current = symbol?.parent
        while (current != null) {
            val owner = current as? PsiNameIdentifierOwner
            if (owner != null && owner.nameIdentifier != null && !owner.name.isNullOrBlank()) {
                return owner
            }
            current = current.parent
        }
        return null
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
