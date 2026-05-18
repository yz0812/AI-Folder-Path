package com.github.aifolderpath.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

@State(
    name = "AIFolderPathProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class ProjectPathSettings : PersistentStateComponent<ProjectPathSettings.State> {
    data class State(
        var prefixDirectory: String = "",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var prefixDirectory: String
        get() = normalizePrefix(state.prefixDirectory)
        set(value) {
            state.prefixDirectory = normalizePrefix(value)
        }

    companion object {
        fun getInstance(project: Project): ProjectPathSettings {
            return project.getService(ProjectPathSettings::class.java)
        }

        fun normalizePrefix(value: String): String {
            return value.trim().replace('\\', '/').trim('/')
        }
    }
}
