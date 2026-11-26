package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.saeDataStore: DataStore<Preferences> by preferencesDataStore(name = "sae_tts_prefs")

data class EngineSettings(
    val threadCount: Int = 2,
    val supertonicSteps: Int = 5,
    val preferDarkTheme: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val THREAD_COUNT = intPreferencesKey("thread_count")
        val SUPERTONIC_STEPS = intPreferencesKey("supertonic_steps")
        val PREFER_DARK_THEME = booleanPreferencesKey("prefer_dark_theme")
    }

    val settingsFlow: Flow<EngineSettings> = context.saeDataStore.data.map { prefs ->
        EngineSettings(
            threadCount = (prefs[Keys.THREAD_COUNT] ?: 2).coerceIn(1, 4),
            supertonicSteps = (prefs[Keys.SUPERTONIC_STEPS] ?: 5).coerceIn(2, 12),
            preferDarkTheme = prefs[Keys.PREFER_DARK_THEME] ?: true,
        )
    }

    suspend fun setThreadCount(count: Int) {
        context.saeDataStore.edit { it[Keys.THREAD_COUNT] = count.coerceIn(1, 4) }
    }

    suspend fun setSupertonicSteps(steps: Int) {
        context.saeDataStore.edit { it[Keys.SUPERTONIC_STEPS] = steps.coerceIn(2, 12) }
    }

    suspend fun setPreferDarkTheme(dark: Boolean) {
        context.saeDataStore.edit { it[Keys.PREFER_DARK_THEME] = dark }
    }
}
