package com.github.aifolderpath

import com.github.aifolderpath.settings.ProjectPathSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object PathResolver {

    /**
     * 为普通文件生成 AI 友好的路径。
     *
     * 输出目标格式是 `@项目根相对路径`。
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
     */
    private fun buildPath(project: Project, file: VirtualFile, appendDirectorySeparator: Boolean): String {
        val projectBasePath = project.basePath ?: return finalizePath(project, file.path, appendDirectorySeparator)
        val normalizedBasePath = projectBasePath.replace('\\', '/').trimEnd('/')
        val normalizedFilePath = file.path.replace('\\', '/')
        val relPath = if (normalizedFilePath == normalizedBasePath) {
            ""
        } else {
            normalizedFilePath.removePrefix("$normalizedBasePath/")
        }
        val path = if (relPath.isEmpty()) "@${project.name}" else "@$relPath"
        return finalizePath(project, path, appendDirectorySeparator)
    }

    /**
     * 对最终输出做统一收口。
     *
     * 这里统一把路径标准化为 `/`，避免 Windows 环境下输出不一致。
     * 目录场景则额外保留一个尾部分隔符，供调用方区分文件与目录。
     */
    private fun finalizePath(project: Project, path: String, appendDirectorySeparator: Boolean): String {
        val normalizedPath = applyPrefix(project, path.replace('\\', '/'))
        return if (appendDirectorySeparator) {
            "${normalizedPath.trimEnd('/', '\\')}\\"
        } else {
            normalizedPath
        }
    }

    /**
     * 按当前项目设置给 AI 路径补前缀目录。
     */
    private fun applyPrefix(project: Project, path: String): String {
        val prefixDirectory = ProjectPathSettings.getInstance(project).prefixDirectory
        if (prefixDirectory.isEmpty()) {
            return path
        }
        return "@$prefixDirectory/${path.removePrefix("@")}"
    }
}
