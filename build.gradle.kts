plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.github.aifolderpath"
version = "1.3.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.github.aifolderpath"
        name = "AI Folder Path"
        version = project.version.toString()
        description = """
            <p>Copy AI-ready paths, symbol anchors, context blocks, usages, and project trees from IntelliJ IDEA.</p>
            <p><b>中文</b></p>
            <ul>
              <li>支持 Copy AI / Path / Anchor / Context / Usages / Tree 多种 AI 友好输出</li>
              <li>Alt+P 在编辑器默认复制符号锚点，在项目视图自动回退为路径复制</li>
              <li>支持文件、目录、多选路径、目录树、类、方法、代码选区与调用点列表</li>
              <li>支持 Maven / Gradle 多模块路径解析，适合 Claude、Cursor、Copilot Chat 等 AI 编程工具</li>
              <li>内置快捷键设置页，可配置 Copy AI 与 Copy AI Usages，并自动处理只读 Keymap 副本</li>
            </ul>
            <p><b>English</b></p>
            <ul>
              <li>Supports Copy AI / Path / Anchor / Context / Usages / Tree for AI-ready code references</li>
              <li>Alt+P copies a symbol anchor in the editor and falls back to path copy in the Project view</li>
              <li>Handles files, directories, multi-selection paths, directory trees, classes, methods, selections, and call-site lists</li>
              <li>Resolves Maven / Gradle multi-module paths for Claude, Cursor, Copilot Chat, and other AI coding tools</li>
              <li>Includes a built-in shortcut settings page for Copy AI and Copy AI Usages, with read-only keymap duplication support</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "261.*"
        }
        vendor {
            name = "AIFolderPath"
        }
    }
}
