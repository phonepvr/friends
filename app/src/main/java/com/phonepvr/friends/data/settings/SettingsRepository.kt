package com.phonepvr.friends.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phonepvr.friends.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
    /** Epoch millis of the most recent successful export, null if there isn't one yet. */
    val lastSuccessfulBackupAt: Long? = null,
    /** How long after the last successful backup the nudge starts firing. */
    val backupNudgeIntervalDays: Int = 30,
    /** Epoch millis when the user last dismissed the in-app nudge banner. */
    val backupNudgeDismissedAt: Long? = null,
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
                lastSuccessfulBackupAt = prefs[Keys.LAST_BACKUP]?.takeIf { it > 0L },
                backupNudgeIntervalDays = prefs[Keys.NUDGE_INTERVAL]
                    ?: DEFAULTS.backupNudgeIntervalDays,
                backupNudgeDismissedAt = prefs[Keys.NUDGE_DISMISSED]?.takeIf { it > 0L },
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

    suspend fun setLastSuccessfulBackupAt(epochMillis: Long) {
        dataStore.edit { it[Keys.LAST_BACKUP] = epochMillis }
    }

    suspend fun setBackupNudgeIntervalDays(days: Int) {
        dataStore.edit { it[Keys.NUDGE_INTERVAL] = days }
    }

    suspend fun setBackupNudgeDismissedAt(epochMillis: Long) {
        dataStore.edit { it[Keys.NUDGE_DISMISSED] = epochMillis }
    }

    /**
     * Serialisable snapshot of all user-configurable settings, used by the
     * backup JSON to round-trip across devices. The transient nudge-dismissed
     * timestamp is intentionally excluded — a restored device should evaluate
     * the nudge from a clean slate.
     */
    suspend fun snapshot(): Map<String, String> {
        val current = settings.first()
        return buildMap {
            put(Snapshot.LEAD_DAYS, current.notificationLeadDays.toString())
            put(Snapshot.HOUR, current.notificationHour.toString())
            put(Snapshot.APP_LOCK, current.appLockEnabled.toString())
            put(Snapshot.THEME, current.themeMode.name)
            put(Snapshot.DEFAULT_CADENCE, current.defaultCadenceDays.toString())
            current.lastSuccessfulBackupAt?.let {
                put(Snapshot.LAST_BACKUP, it.toString())
            }
            put(Snapshot.NUDGE_INTERVAL, current.backupNudgeIntervalDays.toString())
        }
    }

    /** Applies a snapshot from a backup. Unknown keys and bad values are skipped. */
    suspend fun restore(snapshot: Map<String, String>) {
        dataStore.edit { prefs ->
            snapshot[Snapshot.LEAD_DAYS]?.toIntOrNull()?.let { prefs[Keys.LEAD_DAYS] = it }
            snapshot[Snapshot.HOUR]?.toIntOrNull()?.let { prefs[Keys.HOUR] = it }
            snapshot[Snapshot.APP_LOCK]?.toBooleanStrictOrNull()
                ?.let { prefs[Keys.APP_LOCK] = it }
            snapshot[Snapshot.THEME]?.let { prefs[Keys.THEME] = it }
            snapshot[Snapshot.DEFAULT_CADENCE]?.toIntOrNull()
                ?.let { prefs[Keys.DEFAULT_CADENCE] = it }
            snapshot[Snapshot.LAST_BACKUP]?.toLongOrNull()
                ?.let { prefs[Keys.LAST_BACKUP] = it }
            snapshot[Snapshot.NUDGE_INTERVAL]?.toIntOrNull()
                ?.let { prefs[Keys.NUDGE_INTERVAL] = it }
        }
    }

    private fun themeModeOf(name: String): ThemeMode =
        runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)

    private object Keys {
        val LEAD_DAYS = intPreferencesKey("notification_lead_days")
        val HOUR = intPreferencesKey("notification_hour")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val THEME = stringPreferencesKey("theme_mode")
        val DEFAULT_CADENCE = intPreferencesKey("default_cadence_days")
        val LAST_BACKUP = longPreferencesKey("last_successful_backup_at")
        val NUDGE_INTERVAL = intPreferencesKey("backup_nudge_interval_days")
        val NUDGE_DISMISSED = longPreferencesKey("backup_nudge_dismissed_at")
    }

    private object Snapshot {
        const val LEAD_DAYS = "notification_lead_days"
        const val HOUR = "notification_hour"
        const val APP_LOCK = "app_lock_enabled"
        const val THEME = "theme_mode"
        const val DEFAULT_CADENCE = "default_cadence_days"
        const val LAST_BACKUP = "last_successful_backup_at"
        const val NUDGE_INTERVAL = "backup_nudge_interval_days"
    }

    private companion object {
        val DEFAULTS = AppSettings()
    }
}
