package com.phonepvr.friends.data.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Places outgoing calls through the platform's Telecom framework.
 *
 * Multi-SIM aware: [callCapableAccounts] lists the SIMs that can place
 * calls, and [place] takes an optional [PhoneAccountHandle] to route through
 * a specific one. With no account (or one SIM), Telecom uses the system
 * default — so single-SIM users see no behaviour change.
 */
@Singleton
class CallPlacer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class PlaceResult { OK, NO_PERMISSION, INVALID_NUMBER, ERROR }

    /** A call-capable SIM / phone account, reduced to what a picker needs. */
    data class SimAccount(val handle: PhoneAccountHandle, val label: String)

    private val telecom: TelecomManager
        get() = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    /**
     * The SIMs that can place calls. Empty or one entry → no SIM choice to
     * make. Requires READ_PHONE_STATE (already granted for the dialer role);
     * returns empty if it's missing rather than throwing.
     */
    fun callCapableAccounts(): List<SimAccount> {
        if (!hasCallPhonePermission()) return emptyList()
        return try {
            telecom.callCapablePhoneAccounts.mapNotNull { handle ->
                val account = telecom.getPhoneAccount(handle) ?: return@mapNotNull null
                val label = account.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: handle.id
                SimAccount(handle = handle, label = label)
            }
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * True when the user should be asked which SIM to use: more than one
     * call-capable account AND no system-level default outgoing account set.
     * When a default exists we honour it silently.
     */
    fun needsSimChoice(): Boolean {
        val accounts = callCapableAccounts()
        if (accounts.size < 2) return false
        return defaultOutgoingHandle() == null
    }

    private fun defaultOutgoingHandle(): PhoneAccountHandle? = try {
        // tel scheme is what we place with; a per-scheme default counts.
        telecom.getDefaultOutgoingPhoneAccount(android.telecom.PhoneAccount.SCHEME_TEL)
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }

    fun place(number: String): PlaceResult = place(number, account = null)

    fun place(number: String, account: PhoneAccountHandle?): PlaceResult {
        val trimmed = number.trim()
        if (trimmed.isBlank()) return PlaceResult.INVALID_NUMBER
        if (!hasCallPhonePermission()) return PlaceResult.NO_PERMISSION
        return try {
            val uri: Uri = "tel:$trimmed".toUri()
            val extras = account?.let {
                Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
                }
            }
            telecom.placeCall(uri, extras)
            PlaceResult.OK
        } catch (e: SecurityException) {
            PlaceResult.NO_PERMISSION
        } catch (e: Exception) {
            PlaceResult.ERROR
        }
    }

    fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
}
