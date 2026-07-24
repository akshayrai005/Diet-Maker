package com.nutriai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.local.ThemePrefs
import com.nutriai.data.local.ThemeStore
import com.nutriai.data.remote.dto.PublicUser
import com.nutriai.notifications.ReminderGroup
import com.nutriai.notifications.ReminderPrefs
import com.nutriai.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val loading: Boolean = true,
    val user: PublicUser? = null,
    val deleting: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppRepository,
    private val reminderPrefs: ReminderPrefs,
    private val reminderScheduler: ReminderScheduler,
    private val themeStore: ThemeStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val reminders: StateFlow<Map<ReminderGroup, Boolean>> =
        reminderPrefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val theme: StateFlow<ThemePrefs> =
        themeStore.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePrefs())

    fun setReminder(group: ReminderGroup, enabled: Boolean) {
        viewModelScope.launch {
            reminderPrefs.setEnabled(group, enabled)
            if (enabled) reminderScheduler.scheduleGroup(group) else reminderScheduler.cancelGroup(group)
        }
    }

    fun setAccent(accent: String) { viewModelScope.launch { themeStore.setAccent(accent) } }
    fun setThemeMode(mode: String) { viewModelScope.launch { themeStore.setMode(mode) } }

    private val _serverReminders = MutableStateFlow<com.nutriai.data.remote.dto.ReminderPrefsDto?>(null)
    val serverReminders: StateFlow<com.nutriai.data.remote.dto.ReminderPrefsDto?> = _serverReminders.asStateFlow()

    fun loadReminderPrefs() {
        viewModelScope.launch { _serverReminders.value = repository.reminderPrefs().getOrNull() }
    }

    /** Persist the user's workout time (HH:MM) + walk toggle to the server mirror. */
    fun saveReminderPrefs(prefs: com.nutriai.data.remote.dto.ReminderPrefsDto) {
        viewModelScope.launch {
            repository.putReminderPrefs(prefs).getOrNull()?.let { _serverReminders.value = it }
        }
    }

    init {
        viewModelScope.launch {
            val u = repository.me().getOrNull()
            _state.value = _state.value.copy(loading = false, user = u)
        }
        loadReminderPrefs()
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onDone()
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        _state.value = _state.value.copy(deleting = true)
        viewModelScope.launch {
            repository.deleteAccount()
            repository.logout()
            onDone()
        }
    }
}
