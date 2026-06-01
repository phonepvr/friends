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
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /**
     * When true on Android 12+, the OS wallpaper-derived palette wins over the
     * app's hand-tuned warm palette. Default is true — Material You by default;
     * the Settings → Appearance toggle switches back to the bundled palette.
     * (No effect below Android 12, which always uses the bundled palette.)
     */
    val dynamicColorEnabled: Boolean = true,
    val defaultCadenceDays: Int = 30,
    /** Epoch millis of the most recent successful export, null if there isn't one yet. */
    val lastSuccessfulBackupAt: Long? = null,
    /** How long after the last successful backup the nudge starts firing. */
    val backupNudgeIntervalDays: Int = 30,
    /** Epoch millis when the user last dismissed the in-app nudge banner. */
    val backupNudgeDismissedAt: Long? = null,
    /**
     * User-supplied quotes that mix into the daily rotation alongside the
     * bundled set. Included in the backup snapshot so they survive a
     * device move.
     */
    val userQuotes: List<String> = emptyList(),
    /** ISO date (yyyy-MM-dd) when [lastQuoteText] was picked. Device-local. */
    val lastQuoteDate: String = "",
    /** Text of the most recently picked quote, used to keep the choice stable
     *  through the day and to avoid back-to-back repeats. Device-local. */
    val lastQuoteText: String = "",
    /**
     * Set to true once the user has skipped or completed first-run onboarding.
     * Local to the install — restoring a backup on a new device still runs
     * onboarding so the new user sees the privacy and permissions story.
     */
    val hasSeenOnboarding: Boolean = false,
    /** Set to true the first time we've shown the contacts permission explainer. */
    val contactsRationaleShown: Boolean = false,
    /** Set to true the first time we've shown the call-log permission explainer. */
    val callLogRationaleShown: Boolean = false,
    /** Set to true once existing rows have been backfilled with the default cadence. */
    val cadenceBackfilled: Boolean = false,
    /** Stable ids of coach-mark tooltips the user has already dismissed. */
    val dismissedTooltipIds: Set<String> = emptySet(),
    /**
     * When true, the window carries WindowManager.LayoutParams.FLAG_SECURE so
     * the app does not appear in screenshots, screen recordings, the recents
     * card preview, or casts. Off by default so first-run UX isn't affected.
     */
    val hideFromScreenshots: Boolean = false,
    /**
     * Pre-written replies offered on long-press of Reject on the incoming
     * call screen. Order is preserved (top of the list = first chip). On
     * first launch this is the bundled default; once the user edits it the
     * stored list wins, even if they empty it (= hide the long-press hint).
     */
    val quickReplyMessages: List<String> = DEFAULT_QUICK_REPLIES,
)

/** First-launch defaults for [AppSettings.quickReplyMessages]. */
val DEFAULT_QUICK_REPLIES: List<String> = listOf(
    "Can't talk right now — call you back.",
    "On my way.",
    "Can't talk. What's up?",
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
                dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR]
                    ?: DEFAULTS.dynamicColorEnabled,
                defaultCadenceDays = prefs[Keys.DEFAULT_CADENCE]
                    ?: DEFAULTS.defaultCadenceDays,
                lastSuccessfulBackupAt = prefs[Keys.LAST_BACKUP]?.takeIf { it > 0L },
                backupNudgeIntervalDays = prefs[Keys.NUDGE_INTERVAL]
                    ?: DEFAULTS.backupNudgeIntervalDays,
                backupNudgeDismissedAt = prefs[Keys.NUDGE_DISMISSED]?.takeIf { it > 0L },
                userQuotes = prefs[Keys.USER_QUOTES]?.toList().orEmpty(),
                lastQuoteDate = prefs[Keys.LAST_QUOTE_DATE].orEmpty(),
                lastQuoteText = prefs[Keys.LAST_QUOTE_TEXT].orEmpty(),
                hasSeenOnboarding = prefs[Keys.HAS_SEEN_ONBOARDING] ?: false,
                contactsRationaleShown = prefs[Keys.CONTACTS_RATIONALE] ?: false,
                callLogRationaleShown = prefs[Keys.CALL_LOG_RATIONALE] ?: false,
                cadenceBackfilled = prefs[Keys.CADENCE_BACKFILLED] ?: false,
                dismissedTooltipIds = prefs[Keys.DISMISSED_TOOLTIPS].orEmpty(),
                hideFromScreenshots = prefs[Keys.HIDE_FROM_SCREENSHOTS] ?: false,
                // Stored as newline-joined to keep ordering. Absent → defaults;
                // explicit empty stored value → empty (user cleared the list,
                // intentionally hiding the long-press chip).
                quickReplyMessages = prefs[Keys.QUICK_REPLIES]
                    ?.let { decodeQuickReplies(it) }
                    ?: DEFAULT_QUICK_REPLIES,
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

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
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

    suspend fun addUserQuote(text: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.USER_QUOTES].orEmpty()
            prefs[Keys.USER_QUOTES] = current + text
        }
    }

    suspend fun removeUserQuote(text: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.USER_QUOTES].orEmpty()
            prefs[Keys.USER_QUOTES] = current - text
        }
    }

    suspend fun setLastQuote(date: String, text: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_QUOTE_DATE] = date
            prefs[Keys.LAST_QUOTE_TEXT] = text
        }
    }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        dataStore.edit { it[Keys.HAS_SEEN_ONBOARDING] = seen }
    }

    suspend fun setContactsRationaleShown(shown: Boolean) {
        dataStore.edit { it[Keys.CONTACTS_RATIONALE] = shown }
    }

    suspend fun setCallLogRationaleShown(shown: Boolean) {
        dataStore.edit { it[Keys.CALL_LOG_RATIONALE] = shown }
    }

    suspend fun setCadenceBackfilled(backfilled: Boolean) {
        dataStore.edit { it[Keys.CADENCE_BACKFILLED] = backfilled }
    }

    suspend fun dismissTooltip(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DISMISSED_TOOLTIPS].orEmpty()
            prefs[Keys.DISMISSED_TOOLTIPS] = current + id
        }
    }

    suspend fun setHideFromScreenshots(enabled: Boolean) {
        dataStore.edit { it[Keys.HIDE_FROM_SCREENSHOTS] = enabled }
    }

    /** Replaces the stored quick-reply messages. Trims and drops blanks. */
    suspend fun setQuickReplyMessages(messages: List<String>) {
        val cleaned = messages.map { it.trim() }.filter { it.isNotEmpty() }
        dataStore.edit { it[Keys.QUICK_REPLIES] = encodeQuickReplies(cleaned) }
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
            put(Snapshot.DYNAMIC_COLOR, current.dynamicColorEnabled.toString())
            put(Snapshot.DEFAULT_CADENCE, current.defaultCadenceDays.toString())
            current.lastSuccessfulBackupAt?.let {
                put(Snapshot.LAST_BACKUP, it.toString())
            }
            put(Snapshot.NUDGE_INTERVAL, current.backupNudgeIntervalDays.toString())
            if (current.userQuotes.isNotEmpty()) {
                // Newline-joined; restored quotes are split back on import.
                put(Snapshot.USER_QUOTES, current.userQuotes.joinToString("\n"))
            }
            // Round-trip the user's quick replies. We always include the key,
            // even when empty, so "user cleared the list" survives a restore
            // rather than silently falling back to defaults.
            put(
                Snapshot.QUICK_REPLIES,
                current.quickReplyMessages.joinToString(QUICK_REPLY_SEP),
            )
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
            snapshot[Snapshot.DYNAMIC_COLOR]?.toBooleanStrictOrNull()
                ?.let { prefs[Keys.DYNAMIC_COLOR] = it }
            snapshot[Snapshot.DEFAULT_CADENCE]?.toIntOrNull()
                ?.let { prefs[Keys.DEFAULT_CADENCE] = it }
            snapshot[Snapshot.LAST_BACKUP]?.toLongOrNull()
                ?.let { prefs[Keys.LAST_BACKUP] = it }
            snapshot[Snapshot.NUDGE_INTERVAL]?.toIntOrNull()
                ?.let { prefs[Keys.NUDGE_INTERVAL] = it }
            snapshot[Snapshot.USER_QUOTES]?.let { joined ->
                val parsed = joined.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (parsed.isNotEmpty()) prefs[Keys.USER_QUOTES] = parsed.toSet()
            }
            snapshot[Snapshot.QUICK_REPLIES]?.let { stored ->
                prefs[Keys.QUICK_REPLIES] = stored
            }
        }
    }

    private fun themeModeOf(name: String): ThemeMode =
        runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)

    private object Keys {
        val LEAD_DAYS = intPreferencesKey("notification_lead_days")
        val HOUR = intPreferencesKey("notification_hour")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val DEFAULT_CADENCE = intPreferencesKey("default_cadence_days")
        val LAST_BACKUP = longPreferencesKey("last_successful_backup_at")
        val NUDGE_INTERVAL = intPreferencesKey("backup_nudge_interval_days")
        val NUDGE_DISMISSED = longPreferencesKey("backup_nudge_dismissed_at")
        val USER_QUOTES = stringSetPreferencesKey("user_quotes")
        val LAST_QUOTE_DATE = stringPreferencesKey("last_quote_date")
        val LAST_QUOTE_TEXT = stringPreferencesKey("last_quote_text")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val CONTACTS_RATIONALE = booleanPreferencesKey("contacts_rationale_shown")
        val CALL_LOG_RATIONALE = booleanPreferencesKey("call_log_rationale_shown")
        val CADENCE_BACKFILLED = booleanPreferencesKey("cadence_backfilled")
        val DISMISSED_TOOLTIPS = stringSetPreferencesKey("dismissed_tooltips")
        val HIDE_FROM_SCREENSHOTS = booleanPreferencesKey("hide_from_screenshots")
        val QUICK_REPLIES = stringPreferencesKey("quick_reply_messages")
    }

    private object Snapshot {
        const val LEAD_DAYS = "notification_lead_days"
        const val HOUR = "notification_hour"
        const val APP_LOCK = "app_lock_enabled"
        const val THEME = "theme_mode"
        const val DYNAMIC_COLOR = "dynamic_color_enabled"
        const val DEFAULT_CADENCE = "default_cadence_days"
        const val LAST_BACKUP = "last_successful_backup_at"
        const val NUDGE_INTERVAL = "backup_nudge_interval_days"
        const val USER_QUOTES = "user_quotes"
        const val QUICK_REPLIES = "quick_reply_messages"
    }

    private companion object {
        val DEFAULTS = AppSettings()

        // Use a separator that won't appear in user text. Newlines are valid
        // inside a quick-reply (multi-line message), so use  (Record
        // Separator) — the ASCII control char nobody types accidentally.
        private const val QUICK_REPLY_SEP = ""

        fun encodeQuickReplies(list: List<String>): String =
            list.joinToString(QUICK_REPLY_SEP)

        fun decodeQuickReplies(stored: String): List<String> =
            if (stored.isEmpty()) emptyList() else stored.split(QUICK_REPLY_SEP)
    }
}
