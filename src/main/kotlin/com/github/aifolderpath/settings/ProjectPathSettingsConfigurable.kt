package com.github.aifolderpath.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ProjectPathSettingsConfigurable(
    private val project: Project,
) : SearchableConfigurable {
    private var prefixField: JBTextField? = null
    private var loadedPrefix: String = ""

    override fun getId(): String = "com.github.aifolderpath.settings.projectPath"

    override fun getDisplayName(): String = "AI Folder Path Project"

    override fun createComponent(): JComponent {
        loadedPrefix = ProjectPathSettings.getInstance(project).prefixDirectory
        val field = JBTextField(32).apply {
            text = loadedPrefix
            toolTipText = "例如：repo/backend"
        }
        prefixField = field

        val titleLabel = JLabel("项目路径前缀").apply {
            font = font.deriveFont(font.style or Font.BOLD)
        }
        val descriptionLabel = JBLabel("配置复制结果中的前缀目录，只保存到当前项目。留空时保持原输出。").apply {
            isAllowAutoWrapping = true
        }
        val hintLabel = JBLabel("示例：repo/backend → @repo/backend/module/path").apply {
            isAllowAutoWrapping = true
        }

        return JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            add(titleLabel, constraints(0, 0, gridwidth = 2, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(
                descriptionLabel,
                constraints(0, 1, gridwidth = 2, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = Insets(6, 0, 16, 0)),
            )
            add(JLabel("前缀目录"), constraints(0, 2, insets = Insets(0, 0, 0, 10)))
            add(field, constraints(1, 2, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(
                hintLabel,
                constraints(1, 3, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = Insets(6, 0, 0, 0)),
            )
            add(JPanel(), constraints(0, 4, gridwidth = 2, weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.BOTH))
        }
    }

    override fun isModified(): Boolean {
        return currentPrefix() != loadedPrefix
    }

    override fun apply() {
        val normalizedPrefix = currentPrefix()
        ProjectPathSettings.getInstance(project).prefixDirectory = normalizedPrefix
        loadedPrefix = normalizedPrefix
        prefixField?.text = normalizedPrefix
    }

    override fun reset() {
        loadedPrefix = ProjectPathSettings.getInstance(project).prefixDirectory
        prefixField?.text = loadedPrefix
    }

    override fun disposeUIResources() {
        prefixField = null
    }

    private fun currentPrefix(): String {
        return ProjectPathSettings.normalizePrefix(prefixField?.text.orEmpty())
    }

    private fun constraints(
        gridx: Int,
        gridy: Int,
        gridwidth: Int = 1,
        weightx: Double = 0.0,
        weighty: Double = 0.0,
        fill: Int = GridBagConstraints.NONE,
        anchor: Int = GridBagConstraints.WEST,
        insets: Insets = Insets(0, 0, 0, 0),
    ): GridBagConstraints {
        return GridBagConstraints().apply {
            this.gridx = gridx
            this.gridy = gridy
            this.gridwidth = gridwidth
            this.weightx = weightx
            this.weighty = weighty
            this.fill = fill
            this.anchor = anchor
            this.insets = insets
        }
    }
}
