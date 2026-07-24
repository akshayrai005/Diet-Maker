package com.nutriai.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeStore by preferencesDataStore(name = "theme")

/** Accent palette + light/dark preference. Persisted locally, offline. */
data class ThemePrefs(
    val accent: String = "green", // green | pink | yellow
    val mode: String = "system", // system | light | dark
)

@Singleton
class ThemeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val accentKey = stringPreferencesKey("accent")
    private val modeKey = stringPreferencesKey("mode")

    val prefs: Flow<ThemePrefs> = context.themeStore.data.map { p ->
        ThemePrefs(accent = p[accentKey] ?: "green", mode = p[modeKey] ?: "system")
    }

    suspend fun setAccent(accent: String) {
        context.themeStore.edit { it[accentKey] = accent }
    }

    suspend fun setMode(mode: String) {
        context.themeStore.edit { it[modeKey] = mode }
    }
}
