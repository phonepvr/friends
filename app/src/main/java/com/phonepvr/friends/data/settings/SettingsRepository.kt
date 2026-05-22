package com.phonepvr.friends.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phonepvr.friends.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** User-configurable preferences, with the defaults used before anything is set. */
data class AppSettings(
    val notificationLeadDays: Int = 7,
    val notificationHour: Int = 9,
    val appLockEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultCadenceDays: Int = 30,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "friends_settings",
)

/** Persists [AppSettings] in a local Preferences DataStore. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            // A first-launch read can fail with IOException; fall back to defaults.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                notificationLeadDays = prefs[Keys.LEAD_DAYS] ?: DEFAULTS.notificationLeadDays,
                notificationHour = prefs[Keys.HOUR] ?: DEFAULTS.notificationHour,
                appLockEnabled = prefs[Keys.APP_LOCK] ?: DEFAULTS.appLockEnabled,
                themeMode = prefs[Keys.THEME]?.let(::themeModeOf) ?: DEFAULTS.themeMode,
                defaultCadenceDays = prefs[Keys.DEFAULT_CADENCE]
                    ?: DEFAULTS.defaultCadenceDays,
            )
        }

    suspend fun setNotificationLeadDays(days: Int) {
        dataStore.edit { it[Keys.LEAD_DAYS] = days }
    }

    suspend fun setNotificationHour(hour: Int) {
        dataStore.edit { it[Keys.HOUR] = hour }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setDefaultCadenceDays(days: Int) {
        dataStore.edit { it[Keys.DEFAULT_CADENCE] = days }
    }

    private fun themeModeOf(name: String): ThemeMode =
        runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)

    private object Keys {
        val LEAD_DAYS = intPreferencesKey("notification_lead_days")
        val HOUR = intPreferencesKey("notification_hour")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val THEME = stringPreferencesKey("theme_mode")
        val DEFAULT_CADENCE = intPreferencesKey("default_cadence_days")
    }

    private companion object {
        val DEFAULTS = AppSettings()
    }
}
