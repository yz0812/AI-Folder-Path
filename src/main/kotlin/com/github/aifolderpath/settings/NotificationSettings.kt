package com.github.aifolderpath.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "AIFolderPathNotificationSettings",
    storages = [Storage("AIFolderPath.xml")],
)
class NotificationSettings : PersistentStateComponent<NotificationSettings.State> {
    data class State(
        var copyNotificationEnabled: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var copyNotificationEnabled: Boolean
        get() = state.copyNotificationEnabled
        set(value) {
            state.copyNotificationEnabled = value
        }

    companion object {
        fun getInstance(): NotificationSettings {
            return ApplicationManager.getApplication().getService(NotificationSettings::class.java)
        }
    }
}
