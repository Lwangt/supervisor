package com.planlist.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** 应用设置。 */
data class AppSettings(
    val catchUpDays: Int = 1,
    val remindersEnabled: Boolean = true,
    val showMacros: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val snoozeMinutes: Int = 10,
    val onboarded: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "planlist_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CATCH_UP_DAYS = intPreferencesKey("catchUpDays")
        val REMINDERS_ENABLED = booleanPreferencesKey("remindersEnabled")
        val SHOW_MACROS = booleanPreferencesKey("showMacros")
        val THEME_MODE = stringPreferencesKey("themeMode")
        val SNOOZE_MINUTES = intPreferencesKey("snoozeMinutes")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            catchUpDays = prefs[Keys.CATCH_UP_DAYS] ?: 1,
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: true,
            showMacros = prefs[Keys.SHOW_MACROS] ?: true,
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            snoozeMinutes = prefs[Keys.SNOOZE_MINUTES] ?: 10,
            onboarded = prefs[Keys.ONBOARDED] ?: false,
        )
    }

    /** 读取一次当前设置（给通知/接收器这类非 Compose 场景用）。 */
    suspend fun current(): AppSettings = settings.first()

    suspend fun setCatchUpDays(value: Int) = edit { it[Keys.CATCH_UP_DAYS] = value.coerceIn(0, 7) }

    suspend fun setRemindersEnabled(value: Boolean) = edit { it[Keys.REMINDERS_ENABLED] = value }

    suspend fun setShowMacros(value: Boolean) = edit { it[Keys.SHOW_MACROS] = value }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.name }

    suspend fun setSnoozeMinutes(value: Int) = edit { it[Keys.SNOOZE_MINUTES] = value.coerceIn(1, 120) }

    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.ONBOARDED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
