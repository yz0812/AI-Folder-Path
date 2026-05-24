package com.github.aifolderpath

import com.github.aifolderpath.EditorSymbolContextResolver.EditorSymbolContext
import com.github.aifolderpath.settings.NotificationSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import java.awt.datatransfer.StringSelection

class CopyAIRefAction : AnAction() {

    private val log = Logger.getInstance(CopyAIRefAction::class.java)

    /**
     * 复制“定义 + 调用点列表”。
     *
     * 核心流程：
     * 1. 找到当前光标/选区对应的目标元素。
     * 2. 先解析到引用目标，再尽量落到具体实现。
     * 3. 格式化定义锚点与 usage 锚点。
     * 4. 汇总后写入剪贴板。
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val element = findTargetElement(editor, psiFile)
        if (element == null) {
            notify(project, "未找到可解析的符号", NotificationType.WARNING)
            return
        }

        val referenceTarget = resolveReferenceTarget(element)
        if (referenceTarget == null) {
            notify(project, "无法解析到定义或实现", NotificationType.WARNING)
            return
        }

        val definitionTarget = resolveToImplementation(referenceTarget)
        if (definitionTarget == null) {
            notify(project, "无法解析到定义或实现", NotificationType.WARNING)
            return
        }

        val targetFile = definitionTarget.containingFile?.virtualFile
        if (targetFile == null) {
            notify(project, "无法获取目标文件", NotificationType.WARNING)
            return
        }

        val definition = formatDefinitionAnchor(project, definitionTarget, targetFile)
        val usages = collectUsageAnchors(project, referenceTarget, definitionTarget, DEFAULT_USAGE_LIMIT)
        val result = OutputFormatter.formatDefinitionAndUsages(
            definition = definition,
            usages = usages.visibleUsages,
            omittedCount = usages.omittedCount,
        )

        log.info("AIFolderPath(Usages): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    /**
     * 定位当前操作针对的 PSI 元素。
     *
     * 如果有选区，默认取选区起点；否则取光标位置。
     */
    private fun findTargetElement(
        editor: com.intellij.openapi.editor.Editor,
        psiFile: PsiFile,
    ): PsiElement? {
        val selectionModel = editor.selectionModel
        val offset = if (selectionModel.hasSelection()) selectionModel.selectionStart else editor.caretModel.offset
        return psiFile.findElementAt(offset)
    }

    /**
     * 把当前位置解析成真正的“引用目标”。
     *
     * 优先按引用解析；如果当前位置不是引用表达式，
     * 再退回到“是否正落在方法名/类名上”的判定。
     */
    private fun resolveReferenceTarget(element: PsiElement): PsiElement? {
        val reference = (element.parent as? PsiReference) ?: element.reference
        val resolved = reference?.resolve()
        if (resolved is PsiMethod || resolved is PsiClass) {
            return resolved
        }

        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
        if (method != null && isOnMethodName(element, method)) {
            return method
        }

        val clazz = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false)
        if (clazz != null && isOnClassName(element, clazz)) {
            return clazz
        }

        return null
    }

    /**
     * 尝试把目标进一步收敛到“最适合展示的定义”。
     *
     * 类直接返回；方法则优先找具体实现，避免接口/抽象方法输出过虚。
     */
    private fun resolveToImplementation(target: PsiElement): PsiElement? {
        return when (target) {
            is PsiMethod -> findConcreteMethod(target)
            is PsiClass -> target
            else -> null
        }
    }

    /**
     * 如果当前方法来自接口或抽象类，尽量找一个具体实现方法。
     *
     * 这里只取第一个命中的实现，追求的是可用锚点，不做复杂排序。
     */
    private fun findConcreteMethod(method: PsiMethod): PsiMethod {
        val containingClass = method.containingClass ?: return method
        if (!containingClass.isInterface && !containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return method
        }

        val implementations = DefinitionsScopedSearch.search(method).findAll()
        val implMethods = implementations.filterIsInstance<PsiMethod>()
        return implMethods.firstOrNull() ?: method
    }

    /**
     * 收集调用点锚点列表。
     *
     * 这里同时搜索引用目标与最终定义目标，避免接口方法与实现方法之间遗漏引用。
     * 收集后会去重、排序，并按上限截断。
     */
    private fun collectUsageAnchors(
        project: com.intellij.openapi.project.Project,
        referenceTarget: PsiElement,
        definitionTarget: PsiElement,
        limit: Int,
    ): UsageList {
        val allUsageAnchors = buildList {
            add(referenceTarget)
            if (definitionTarget != referenceTarget) {
                add(definitionTarget)
            }
        }
            .asSequence()
            .flatMap { ReferencesSearch.search(it).findAll().asSequence() }
            .mapNotNull { reference -> toUsageAnchor(project, reference) }
            .distinctBy { Triple(it.sortPath, it.lineNumber, it.displayText) }
            .sortedWith(compareBy(UsageAnchor::sortPath, UsageAnchor::lineNumber, UsageAnchor::sortOffset))
            .toList()

        val visibleUsages = allUsageAnchors.take(limit).map { it.displayText }
        return UsageList(
            visibleUsages = visibleUsages,
            omittedCount = (allUsageAnchors.size - visibleUsages.size).coerceAtLeast(0),
        )
    }

    /**
     * 生成定义位置的锚点文本。
     *
     * 优先输出带符号信息的锚点；实在拿不到上下文时，再退回纯路径。
     */
    private fun formatDefinitionAnchor(
        project: com.intellij.openapi.project.Project,
        target: PsiElement,
        targetFile: com.intellij.openapi.vfs.VirtualFile,
    ): String {
        return EditorSymbolContextResolver.resolve(project, target)?.let(OutputFormatter::formatAnchor)
            ?: PathResolver.resolve(project, targetFile)
    }

    /**
     * 把单个引用转换成可展示、可排序的 usage 锚点。
     */
    private fun toUsageAnchor(
        project: com.intellij.openapi.project.Project,
        reference: PsiReference,
    ): UsageAnchor? {
        val element = reference.element
        val targetFile = element.containingFile?.virtualFile ?: return null
        val context = EditorSymbolContextResolver.resolve(project, element)
        val displayText = context?.let(OutputFormatter::formatUsageAnchor)
            ?: PathResolver.resolve(project, targetFile)
        return UsageAnchor(
            displayText = displayText,
            sortPath = targetFile.path,
            lineNumber = context?.currentLine ?: 1,
            sortOffset = element.textRange?.startOffset ?: 0,
        )
    }

    /**
     * 判断当前位置是否正好落在方法名标识符上。
     */
    private fun isOnMethodName(element: PsiElement, method: PsiMethod): Boolean {
        val nameId = method.nameIdentifier ?: return false
        return element.textRange.intersects(nameId.textRange)
    }

    /**
     * 判断当前位置是否正好落在类名标识符上。
     */
    private fun isOnClassName(element: PsiElement, clazz: PsiClass): Boolean {
        val nameId = clazz.nameIdentifier ?: return false
        return element.textRange.intersects(nameId.textRange)
    }

    /**
     * 统一发送通知。
     *
     * 通知属于辅助反馈，失败时只记日志，不影响复制结果。
     */
    private fun notify(
        project: com.intellij.openapi.project.Project,
        content: String,
        type: NotificationType,
    ) {
        if (type == NotificationType.INFORMATION && !NotificationSettings.getInstance().copyNotificationEnabled) return
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Usages Copied", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Usages): notification failed", ex)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 只有编辑器和 PSI 文件同时存在时，才允许显示该动作。
     */
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }

    /**
     * 可展示、可排序的单个 usage 项。
     */
    private data class UsageAnchor(
        val displayText: String,
        val sortPath: String,
        val lineNumber: Int,
        val sortOffset: Int,
    )

    /**
     * usage 列表和省略数量的封装结果。
     */
    private data class UsageList(
        val visibleUsages: List<String>,
        val omittedCount: Int,
    )

    companion object {
        private const val DEFAULT_USAGE_LIMIT = 10
    }
}
