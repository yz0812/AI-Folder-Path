package com.github.aifolderpath

import com.github.aifolderpath.EditorSymbolContextResolver.EditorSymbolContext
import com.github.aifolderpath.settings.OutputFormatPreset
import com.github.aifolderpath.settings.OutputFormatPresetStore

object OutputFormatter {
    /**
     * 按当前输出预设格式化纯路径。
     *
     * Compact / Anchor 模式下，纯路径本身已经够用；
     * Context 模式则显式补上 `path:` 前缀，让多行块结构更清楚。
     */
    fun formatPath(path: String, preset: OutputFormatPreset = OutputFormatPresetStore.get()): String {
        return when (preset) {
            OutputFormatPreset.Compact, OutputFormatPreset.Anchor -> path
            OutputFormatPreset.Context -> "path: $path"
        }
    }

    /**
     * 根据当前预设，把同一个符号上下文格式化为不同输出形态。
     */
    fun formatContext(
        context: EditorSymbolContext,
        preset: OutputFormatPreset = OutputFormatPresetStore.get(),
    ): String {
        return when (preset) {
            OutputFormatPreset.Compact -> context.path
            OutputFormatPreset.Anchor -> formatAnchor(context)
            OutputFormatPreset.Context -> formatContextBlock(context)
        }
    }

    /**
     * 生成单行锚点格式。
     *
     * 结构是：路径 + 可选符号文本 + 行号范围。
     * 这是最适合直接贴到聊天窗口里的紧凑格式。
     */
    fun formatAnchor(context: EditorSymbolContext): String {
        return buildString {
            append(context.path)
            context.symbolText()?.let {
                append(' ')
                append(it)
            }
            append(' ')
            append(context.lineText())
        }
    }

    /**
     * 生成 usage 列表中的单行锚点。
     *
     * 与 definition 锚点不同，这里固定使用“当前命中行”，
     * 因为 usage 关注的是调用点，而不是整个方法体范围。
     */
    fun formatUsageAnchor(context: EditorSymbolContext): String {
        return buildString {
            append(context.path)
            context.symbolText()?.let {
                append(' ')
                append(it)
            }
            append(' ')
            append(context.currentLineText())
        }
    }

    /**
     * 生成更适合结构化展示的上下文块。
     *
     * 这里按多行 key-value 输出，便于 AI 或用户快速分辨路径、类、方法和行范围。
     */
    fun formatContextBlock(context: EditorSymbolContext): String {
        return buildList {
            add("path: ${context.path}")
            context.className?.let { add("class: $it") }
            context.methodSignature?.let { add("method: $it") }
            add("lines: ${context.lineText()}")
        }.joinToString("\n")
    }

    /**
     * 把“定义 + 调用点列表”拼成统一文本。
     *
     * 当调用点过多时，只展示前几条，并在末尾追加省略提示。
     */
    fun formatDefinitionAndUsages(
        definition: String,
        usages: List<String>,
        omittedCount: Int = 0,
    ): String {
        return buildList {
            add("definition: $definition")
            if (usages.isEmpty()) {
                add("usages: (none)")
            } else {
                usages.forEachIndexed { index, usage ->
                    add("usage[${index + 1}]: $usage")
                }
            }
            if (omittedCount > 0) {
                add("... +$omittedCount more call sites")
            }
        }.joinToString("\n")
    }
}
