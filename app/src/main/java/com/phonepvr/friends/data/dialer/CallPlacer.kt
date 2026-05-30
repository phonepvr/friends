package com.phonepvr.friends.data.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Places outgoing calls through the platform's Telecom framework so the
 * call goes through whichever SIM the user has set as default (or the
 * one Bondwidth picks per-contact in a later phase). When CALL_PHONE
 * hasn't been granted yet, [place] returns Result.failure and the UI
 * prompts.
 *
 * v1 ignores multi-SIM selection: TelecomManager picks the default
 * account. Phase 7 adds a per-contact preferredSimAccountHandle and a
 * picker for outgoing calls without one set.
 */
@Singleton
class CallPlacer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class PlaceResult { OK, NO_PERMISSION, INVALID_NUMBER, ERROR }

    fun place(number: String): PlaceResult {
        val trimmed = number.trim()
        if (trimmed.isBlank()) return PlaceResult.INVALID_NUMBER
        if (!hasCallPhonePermission()) return PlaceResult.NO_PERMISSION
        return try {
            val uri: Uri = "tel:$trimmed".toUri()
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.placeCall(uri, null)
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
