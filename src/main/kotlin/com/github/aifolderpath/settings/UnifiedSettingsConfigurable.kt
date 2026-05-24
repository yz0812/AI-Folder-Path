package com.github.aifolderpath.settings

import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

class UnifiedSettingsConfigurable(
    private val project: Project,
) : SearchableConfigurable {

    private val keymapProvider: () -> Keymap = {
        requireNotNull(KeymapManager.getInstance().activeKeymap) { "Active keymap is unavailable" }
    }
    private val shortcutService: ShortcutKeymapService = ShortcutKeymapService()

    private var prefixField: JBTextField? = null
    private var notificationCheckBox: JBCheckBox? = null
    private var shortcutPanel: ShortcutSettingsPanel? = null
    private var loadedPrefix: String = ""
    private var loadedNotificationEnabled: Boolean = true
    private var loadedShortcutState: EditableShortcutPage? = null

    override fun getId(): String = "com.github.aifolderpath.settings"

    override fun getDisplayName(): String = "AI Folder Path"

    override fun createComponent(): JComponent {
        loadedPrefix = ProjectPathSettings.getInstance(project).prefixDirectory
        val field = JBTextField(32).apply {
            text = loadedPrefix
            toolTipText = "例如：repo/backend"
        }
        prefixField = field

        loadedNotificationEnabled = NotificationSettings.getInstance().copyNotificationEnabled
        val notificationBox = JBCheckBox("复制成功后显示通知", loadedNotificationEnabled)
        notificationCheckBox = notificationBox

        val shortcutState = shortcutService.buildEditableState(keymapProvider())
        loadedShortcutState = shortcutState
        val shortcuts = ShortcutSettingsPanel(shortcutState)
        shortcutPanel = shortcuts

        val content = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(12, 16, 16, 16)
            add(buildPrefixSection(field), gbc(0, 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(buildNotificationSection(notificationBox), gbc(0, 1, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = JBUI.insetsTop(16)))
            add(buildShortcutSection(shortcuts.root()), gbc(0, 2, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = JBUI.insetsTop(16)))
            add(JPanel().apply { isOpaque = false }, gbc(0, 3, weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.BOTH))
        }

        return JScrollPane(content).apply {
            border = JBUI.Borders.empty()
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }
    }

    override fun isModified(): Boolean {
        val prefixModified = currentPrefix() != loadedPrefix
        val notificationModified = (notificationCheckBox?.isSelected ?: loadedNotificationEnabled) != loadedNotificationEnabled
        val shortcutModified = shortcutPanel?.snapshot()?.let { it != loadedShortcutState } ?: false
        return prefixModified || notificationModified || shortcutModified
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val panel = shortcutPanel
        val snapshot = panel?.snapshot()
        if (panel != null && snapshot != null && snapshot != loadedShortcutState) {
            val activeKeymap = keymapProvider()
            shortcutService.validateActiveKeymap(activeKeymap, snapshot)
            val validationErrors = snapshot.cards.mapNotNull { it.validationMessage }.distinct()
            if (validationErrors.isNotEmpty()) {
                throw ConfigurationException(validationErrors.joinToString("\n"))
            }
            val conflicts = try {
                shortcutService.detectConflicts(snapshot)
            } catch (e: IllegalArgumentException) {
                throw ConfigurationException(e.message ?: "快捷键校验失败")
            }
            if (conflicts.isNotEmpty()) {
                throw ConfigurationException(ShortcutSettingsConfigurable.buildConflictMessage(conflicts))
            }
            val savedKeymap = try {
                shortcutService.applyChanges(snapshot)
            } catch (e: ConfigurationException) {
                throw e
            } catch (e: Exception) {
                throw ConfigurationException("快捷键设置保存失败：${e.message ?: e.javaClass.simpleName}")
            }
            loadedShortcutState = shortcutService.buildEditableState(savedKeymap)
            panel.setState(requireNotNull(loadedShortcutState))
        }

        val normalizedPrefix = currentPrefix()
        if (normalizedPrefix != loadedPrefix) {
            ProjectPathSettings.getInstance(project).prefixDirectory = normalizedPrefix
            loadedPrefix = normalizedPrefix
            prefixField?.text = normalizedPrefix
        }

        val notificationEnabled = notificationCheckBox?.isSelected ?: loadedNotificationEnabled
        if (notificationEnabled != loadedNotificationEnabled) {
            NotificationSettings.getInstance().copyNotificationEnabled = notificationEnabled
            loadedNotificationEnabled = notificationEnabled
        }
    }

    override fun reset() {
        loadedPrefix = ProjectPathSettings.getInstance(project).prefixDirectory
        prefixField?.text = loadedPrefix

        loadedNotificationEnabled = NotificationSettings.getInstance().copyNotificationEnabled
        notificationCheckBox?.isSelected = loadedNotificationEnabled

        val state = shortcutService.buildEditableState(keymapProvider())
        loadedShortcutState = state
        shortcutPanel?.setState(state)
    }

    override fun disposeUIResources() {
        prefixField = null
        notificationCheckBox = null
        shortcutPanel = null
        loadedShortcutState = null
    }

    fun shortcutPanel(): ShortcutSettingsPanel = requireNotNull(shortcutPanel)

    private fun currentPrefix(): String {
        return ProjectPathSettings.normalizePrefix(prefixField?.text.orEmpty())
    }

    private fun buildPrefixSection(field: JBTextField): JComponent {
        val description = JBLabel("配置复制结果中的前缀目录，仅作用于当前项目。留空时保持原输出。").apply {
            isAllowAutoWrapping = true
            foreground = UIUtil.getContextHelpForeground()
        }
        val hint = JBLabel("示例：repo/backend → @repo/backend/module/path").apply {
            isAllowAutoWrapping = true
            foreground = UIUtil.getContextHelpForeground()
        }

        val body = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(description, gbc(0, 0, gridwidth = 2, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = JBUI.insetsBottom(10)))
            add(JLabel("前缀目录"), gbc(0, 1, insets = JBUI.insetsRight(10)))
            add(field, gbc(1, 1, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(hint, gbc(1, 2, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = JBUI.insetsTop(6)))
        }

        return JPanel(BorderLayout()).apply {
            border = IdeBorderFactory.createTitledBorder("项目路径前缀 (项目级)", false)
            add(body, BorderLayout.CENTER)
        }
    }

    private fun buildNotificationSection(checkBox: JBCheckBox): JComponent {
        val hint = JBLabel("关闭后复制成功不再弹气泡通知；错误类提示仍会显示。作用于全部项目。").apply {
            isAllowAutoWrapping = true
            foreground = UIUtil.getContextHelpForeground()
        }
        val body = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(checkBox, gbc(0, 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
            add(hint, gbc(0, 1, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL, insets = JBUI.insetsTop(6)))
        }
        return JPanel(BorderLayout()).apply {
            border = IdeBorderFactory.createTitledBorder("通知 (全局)", false)
            add(body, BorderLayout.CENTER)
        }
    }

    private fun buildShortcutSection(content: JComponent): JComponent {
        val description = JBLabel("修改将写入当前活动 Keymap。只读 Keymap 会自动复制为可编辑副本，作用于全部项目。").apply {
            isAllowAutoWrapping = true
            foreground = UIUtil.getContextHelpForeground()
        }
        val body = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(description, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout()).apply {
            border = IdeBorderFactory.createTitledBorder("快捷键 (全局 Keymap)", false)
            add(body, BorderLayout.CENTER)
        }
    }

    private fun gbc(
        gridx: Int,
        gridy: Int,
        gridwidth: Int = 1,
        weightx: Double = 0.0,
        weighty: Double = 0.0,
        fill: Int = GridBagConstraints.NONE,
        anchor: Int = GridBagConstraints.WEST,
        insets: java.awt.Insets = JBUI.emptyInsets(),
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
