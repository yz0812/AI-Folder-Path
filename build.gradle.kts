import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.github.aifolderpath"
version = "1.3.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugin("com.intellij.java")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.github.aifolderpath"
        name = "AI Folder Path"
        version = project.version.toString()
        description = """
            <p>Copy AI-ready paths, symbol anchors, context blocks, usages, project trees, and Git commit context from JetBrains IDEs.</p>
            <p><b>中文</b></p>
            <ul>
              <li>支持 Copy AI / Path / Anchor / Context / Usages / Tree / Git Info 多种 AI 友好输出</li>
              <li>Alt+P 按当前默认动作复制；在 Git Log 中复制修订号，增强动作不可用时自动回退为路径复制</li>
              <li>支持文件、目录、多选路径、目录树、类、方法、代码选区、调用点列表与 Git Log 提交信息</li>
              <li>支持 Maven / Gradle 多模块路径解析，适合 Claude、Cursor、Copilot Chat 等 AI 编程工具</li>
              <li>内置快捷键设置页，可配置当前可用动作，并自动处理只读 Keymap 副本</li>
            </ul>
            <p><b>English</b></p>
            <ul>
              <li>Supports Copy AI / Path / Anchor / Context / Usages / Tree / Git Info for AI-ready code references</li>
              <li>Alt+P uses the current default action, copies revisions in Git Log, and falls back to path copy when enhanced actions are unavailable</li>
              <li>Handles files, directories, multi-selection paths, directory trees, classes, methods, selections, call-site lists, and Git Log commit info</li>
              <li>Resolves Maven / Gradle multi-module paths for Claude, Cursor, Copilot Chat, and other AI coding tools</li>
              <li>Includes a built-in shortcut settings page for currently available actions, with read-only keymap duplication support</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "241"
        }
        vendor {
            name = "AIFolderPath"
        }
    }
}
