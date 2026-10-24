package com.phonepvr.friends.data.dialer

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Pure helpers for [SimMemory]: how numbers and SIMs are written down. */
object SimMemoryKeys {
    /**
     * The same number however it's written: its last 9 digits, as everywhere else
     * in the app ("+91 98765 43210", "098765 43210" and "9876543210" match).
     * Null when there are too few digits to be a phone number.
     */
    fun numberKey(number: String): String? =
        number.filter(Char::isDigit).takeLast(SUFFIX_DIGITS).takeIf { it.length >= MIN_DIGITS }

    fun encode(handle: PhoneAccountHandle): String =
        handle.componentName.flattenToString() + SEPARATOR + handle.id

    fun decode(value: String): PhoneAccountHandle? {
        val at = value.indexOf(SEPARATOR).takeIf { it > 0 } ?: return null
        val component = ComponentName.unflattenFromString(value.substring(0, at)) ?: return null
        return PhoneAccountHandle(component, value.substring(at + 1))
    }

    private const val SUFFIX_DIGITS = 9
    private const val MIN_DIGITS = 3
    private const val SEPARATOR = '\n'
}

private val Context.simMemoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "sim_memory")

/**
 * Which SIM to call a number from, remembered per number on dual-SIM phones.
 * Kept on this device only (SIMs belong to the phone) and outside backups.
 * A remembered SIM that's no longer in the phone is ignored, so the chooser
 * shows again.
 */
@Singleton
class SimMemory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** numberKey → encoded SIM, kept warm so a call can be placed without waiting. */
    val choices: StateFlow<Map<String, String>> = context.simMemoryDataStore.data
        .map { prefs -> prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The remembered SIM for [number], if it's still one of [available]. */
    fun simFor(number: String, available: List<PhoneAccountHandle>): PhoneAccountHandle? {
        val key = SimMemoryKeys.numberKey(number) ?: return null
        val handle = choices.value[key]?.let(SimMemoryKeys::decode) ?: return null
        return handle.takeIf { it in available }
    }

    fun remember(number: String, handle: PhoneAccountHandle) {
        val key = SimMemoryKeys.numberKey(number) ?: return
        scope.launch {
            context.simMemoryDataStore.edit { it[stringPreferencesKey(key)] = SimMemoryKeys.encode(handle) }
        }
    }

    fun forget(number: String) {
        val key = SimMemoryKeys.numberKey(number) ?: return
        scope.launch { context.simMemoryDataStore.edit { it.remove(stringPreferencesKey(key)) } }
    }
}
