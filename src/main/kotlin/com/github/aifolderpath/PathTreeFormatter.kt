package com.github.aifolderpath

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object PathTreeFormatter {
    /**
     * 默认最多展开 2 层目录，避免目录树复制过长。
     */
    private const val DEFAULT_MAX_DEPTH = 2

    /**
     * 默认最多渲染 50 个节点，避免一次复制过多内容。
     */
    private const val DEFAULT_MAX_NODES = 50

    /**
     * 规范化后的路径项。
     *
     * - normalizedPath：完整标准化路径，用于排序和去重
     * - segments：按 `/` 拆开的路径片段，用于构树
     * - directory：当前项是否为目录
     */
    private data class PathEntry(
        val normalizedPath: String,
        val segments: List<String>,
        val directory: Boolean,
    )

    /**
     * 内存中的树节点结构。
     */
    private data class TreeNode(
        var directory: Boolean = true,
        val children: LinkedHashMap<String, TreeNode> = linkedMapOf(),
    )

    /**
     * 渲染时的状态对象，用来控制节点上限和是否发生截断。
     */
    private class RenderState(
        private val maxNodes: Int,
    ) {
        var renderedNodes: Int = 0
        var truncated: Boolean = false

        fun tryConsumeNode(): Boolean {
            if (renderedNodes >= maxNodes) {
                truncated = true
                return false
            }
            renderedNodes++
            return true
        }
    }

    /**
     * 把项目视图中的多选结果格式化成树形文本。
     *
     * 关键处理点：
     * 1. 先按原始路径去重。
     * 2. 再转换成统一的 AI 路径片段。
     * 3. 如果某个目录已经被选中，就忽略它下面重复出现的子项，避免树重复展开。
     */
    fun formatSelection(project: Project, selectedFiles: List<VirtualFile>): String {
        val entries = selectedFiles
            .distinctBy { it.path }
            .map { file ->
                val aiPath = if (file.isDirectory) {
                    PathResolver.resolveDirectory(project, file)
                } else {
                    PathResolver.resolve(project, file)
                }
                toPathEntry(aiPath, file.isDirectory)
            }
            .sortedWith(compareBy<PathEntry>({ it.segments.firstOrNull().orEmpty() }, { it.segments.size }, { it.normalizedPath }))
            .fold(mutableListOf<PathEntry>()) { kept, entry ->
                if (kept.none { it.directory && isAncestor(it.segments, entry.segments) }) {
                    kept += entry
                }
                kept
            }

        if (entries.isEmpty()) {
            return ""
        }

        return entries
            .groupBy { it.segments.firstOrNull().orEmpty() }
            .values
            .joinToString("\n\n") { renderSelectionGroup(it) }
    }

    /**
     * 为单个目录生成摘要树。
     *
     * 目录本身作为头部，下面按层级列出子节点；
     * 超过深度或节点数限制时，会在末尾给出省略提示。
     */
    fun formatDirectorySummary(
        project: Project,
        directory: VirtualFile,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): String {
        val header = PathResolver.resolveDirectory(project, directory).replace('\\', '/').trimEnd('/') + "/"
        val lines = mutableListOf(header)
        val state = RenderState(maxNodes)
        renderDirectoryChildren(directory, 1, maxDepth, "", lines, state)
        if (state.truncated) {
            lines += "... (+more omitted)"
        }
        return lines.joinToString("\n")
    }

    /**
     * 渲染同一顶层分组下的树。
     *
     * 先提取所有项的公共目录前缀作为头部，再把剩余相对路径插入到内存树中。
     */
    private fun renderSelectionGroup(entries: List<PathEntry>): String {
        val commonPrefix = commonDirectoryPrefix(entries)
        val header = commonPrefix.joinToString("/").ifEmpty { entries.first().segments.first() } + "/"
        val root = TreeNode(directory = true)

        entries.forEach { entry ->
            val relativeSegments = entry.segments.drop(commonPrefix.size)
            if (relativeSegments.isNotEmpty()) {
                insert(root, relativeSegments, entry.directory)
            }
        }

        val lines = mutableListOf(header)
        renderTree(root, "", lines)
        return lines.joinToString("\n")
    }

    /**
     * 递归渲染目录摘要。
     *
     * 排序规则固定为“目录在前、名称按字母序”，这样复制结果更稳定。
     */
    private fun renderDirectoryChildren(
        directory: VirtualFile,
        depth: Int,
        maxDepth: Int,
        prefix: String,
        lines: MutableList<String>,
        state: RenderState,
    ) {
        if (depth > maxDepth) {
            if (directory.children.isNotEmpty()) {
                state.truncated = true
            }
            return
        }

        val children = directory.children
            .sortedWith(compareBy<VirtualFile>({ !it.isDirectory }, { it.name.lowercase() }))

        children.forEachIndexed { index, child ->
            if (!state.tryConsumeNode()) {
                return
            }
            val isLast = index == children.lastIndex
            val connector = if (isLast) "└─ " else "├─ "
            lines += prefix + connector + child.name + if (child.isDirectory) "/" else ""
            if (child.isDirectory) {
                renderDirectoryChildren(
                    directory = child,
                    depth = depth + 1,
                    maxDepth = maxDepth,
                    prefix = prefix + if (isLast) "   " else "│  ",
                    lines = lines,
                    state = state,
                )
            }
        }
    }

    /**
     * 渲染内存树节点。
     */
    private fun renderTree(node: TreeNode, prefix: String, lines: MutableList<String>) {
        val entries = node.children.entries
            .sortedWith(compareBy<Map.Entry<String, TreeNode>>({ !it.value.directory }, { it.key.lowercase() }))

        entries.forEachIndexed { index, entry ->
            val isLast = index == entries.lastIndex
            val connector = if (isLast) "└─ " else "├─ "
            lines += prefix + connector + entry.key + if (entry.value.directory) "/" else ""
            renderTree(entry.value, prefix + if (isLast) "   " else "│  ", lines)
        }
    }

    /**
     * 把相对路径片段插入树结构。
     */
    private fun insert(root: TreeNode, segments: List<String>, directory: Boolean) {
        var current = root
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            current = current.children.getOrPut(segment) { TreeNode(directory = !isLast || directory) }
            if (isLast) {
                current.directory = directory
            }
        }
    }

    /**
     * 计算一组选中项的公共目录前缀。
     *
     * 文件只参与其父目录前缀计算，目录本身则完整参与。
     */
    private fun commonDirectoryPrefix(entries: List<PathEntry>): List<String> {
        val candidates = entries.map { entry ->
            if (entry.directory) entry.segments else entry.segments.dropLast(1)
        }
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val first = candidates.first()
        var index = 0
        while (index < first.size && candidates.all { index < it.size && it[index] == first[index] }) {
            index++
        }
        return first.take(index)
    }

    /**
     * 把 AI 路径转换为便于构树的数据结构。
     */
    private fun toPathEntry(aiPath: String, directory: Boolean): PathEntry {
        val normalized = aiPath.replace('\\', '/').trimEnd('/')
        return PathEntry(
            normalizedPath = normalized,
            segments = normalized.split('/').filter { it.isNotBlank() },
            directory = directory,
        )
    }

    /**
     * 判断 ancestor 是否是 descendant 的严格祖先路径。
     */
    private fun isAncestor(ancestor: List<String>, descendant: List<String>): Boolean {
        if (ancestor.size >= descendant.size) {
            return false
        }
        return ancestor.indices.all { ancestor[it] == descendant[it] }
    }
}
