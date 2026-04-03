package com.github.aifolderpath

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

object PathResolver {

    /**
     * 为普通文件生成 AI 友好的路径。
     *
     * 输出目标格式是 `@模块名/模块内相对路径`。
     * 如果当前文件不在 IntelliJ 可识别的模块内，则会回退为
     * `@项目名/项目内相对路径`，保证任何文件至少都能得到一个稳定结果。
     */
    fun resolve(project: Project, file: VirtualFile): String {
        return buildPath(project, file, appendDirectorySeparator = false)
    }

    /**
     * 为目录生成 AI 友好的路径。
     *
     * 与 [resolve] 的区别只有一点：目录结果会额外补一个尾部分隔符，
     * 这样下游格式化时可以明确表达“这是目录，不是文件”。
     */
    fun resolveDirectory(project: Project, directory: VirtualFile): String {
        return buildPath(project, directory, appendDirectorySeparator = true)
    }

    /**
     * 统一构建文件或目录的 AI 路径。
     *
     * 处理顺序：
     * 1. 先尝试使用 IntelliJ 模块系统直接定位 content root。
     * 2. 如果模块存在但 content root 没命中，再手动向上查找构建文件作为模块根。
     * 3. 仍失败时，回退为项目根相对路径。
     *
     * 这样做的目的是尽量优先复用 IDE 已知的模块信息，只有在索引或模块边界
     * 不够理想时才做文件系统级兜底。
     */
    private fun buildPath(project: Project, file: VirtualFile, appendDirectorySeparator: Boolean): String {
        val module = ModuleUtilCore.findModuleForFile(file, project)
        val projectBasePath = project.basePath ?: return finalizePath(file.path, appendDirectorySeparator)

        if (module != null) {
            // IntelliJ 一个模块可能配置多个 content root。
            // 这里取“最深的那个命中根”，避免嵌套目录结构下路径截断过短。
            val moduleRoot = ModuleRootManager.getInstance(module).contentRoots
                .filter { VfsUtilCore.isAncestor(it, file, false) || it == file }
                .maxByOrNull { it.path.length }

            if (moduleRoot != null) {
                val relPath = VfsUtilCore.getRelativePath(file, moduleRoot, '/')
                val path = if (relPath.isNullOrEmpty()) "@${module.name}" else "@${module.name}/$relPath"
                return finalizePath(path, appendDirectorySeparator)
            }

            // 某些情况下 IDE 模块存在，但 content root 无法给出理想结果。
            // 此时退回到“向上查找 pom/build 文件”的方式确定模块边界。
            val modulePath = findModuleRoot(file, projectBasePath)
            if (modulePath != null) {
                val relPath = file.path.removePrefix(modulePath).trimStart('/', '\\').replace('\\', '/')
                val path = if (relPath.isEmpty()) "@${module.name}" else "@${module.name}/$relPath"
                return finalizePath(path, appendDirectorySeparator)
            }
        }

        // 最后兜底到项目根，适用于单模块项目或无法识别模块边界的场景。
        val projectName = project.name
        val relPath = file.path.removePrefix(projectBasePath).trimStart('/', '\\').replace('\\', '/')
        val path = if (relPath.isEmpty()) "@$projectName" else "@$projectName/$relPath"
        return finalizePath(path, appendDirectorySeparator)
    }

    /**
     * 从当前文件所在目录开始向上查找最近的模块根。
     *
     * 模块根的判定非常保守：只认 `pom.xml`、`build.gradle`、`build.gradle.kts`。
     * 同时严格限制在项目根目录之内，避免跨项目向上爬导致路径错误。
     */
    private fun findModuleRoot(file: VirtualFile, projectBasePath: String): String? {
        var dir = if (file.isDirectory) file else file.parent
        val normalizedBase = projectBasePath.replace('\\', '/')
        while (dir != null) {
            val dirPath = dir.path.replace('\\', '/')
            if (dirPath.length < normalizedBase.length) break

            if (hasModuleMarker(dir)) {
                return dir.path
            }
            if (dirPath == normalizedBase) break
            dir = dir.parent
        }
        return null
    }

    /**
     * 对最终输出做统一收口。
     *
     * 这里统一把路径标准化为 `/`，避免 Windows 环境下输出不一致。
     * 目录场景则额外保留一个尾部分隔符，供调用方区分文件与目录。
     */
    private fun finalizePath(path: String, appendDirectorySeparator: Boolean): String {
        val normalizedPath = path.replace('\\', '/')
        return if (appendDirectorySeparator) {
            "${normalizedPath.trimEnd('/', '\\')}\\"
        } else {
            normalizedPath
        }
    }

    /**
     * 判断当前目录是否可视为模块根。
     *
     * 这里只识别最常见的 Maven / Gradle 标记文件，不做更多约定猜测。
     */
    private fun hasModuleMarker(dir: VirtualFile): Boolean {
        return dir.findChild("pom.xml") != null
                || dir.findChild("build.gradle") != null
                || dir.findChild("build.gradle.kts") != null
    }
}
