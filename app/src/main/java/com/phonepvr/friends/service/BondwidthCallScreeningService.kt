package com.phonepvr.friends.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.phonepvr.friends.data.blocking.HiddenCaller
import com.phonepvr.friends.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Turns away calls that hide their number when "Block hidden numbers" is on.
 *
 * Android binds the default phone app's CallScreeningService for every incoming
 * call (Telecom's CallScreeningServiceFilter, PACKAGE_TYPE_DEFAULT_DIALER), before
 * the call rings; so this works only while Bondwidth is the phone app. A call it
 * disallows is rejected without ringing or a notification and logged as BLOCKED
 * (block reason "call screening service"), so Recents lists it as blocked.
 */
@AndroidEntryPoint
class BondwidthCallScreeningService : CallScreeningService() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onScreenCall(details: Call.Details) {
        scope.launch {
            val response = runCatching { decide(details) }.getOrNull() ?: ALLOW
            runCatching { respondToCall(details, response) }
        }
    }

    private suspend fun decide(details: Call.Details): CallResponse {
        // Only incoming calls are screened here; never get in the way of a call
        // the user is making.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            details.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            return ALLOW
        }
        // Telecom waits only a few seconds for an answer; if settings can't be
        // read in time, let the call ring rather than risk losing it.
        val blockHidden = withTimeoutOrNull(SETTINGS_TIMEOUT_MS) {
            settingsRepository.settings.first().blockHiddenCallers
        } ?: false
        if (!blockHidden) return ALLOW
        val number = details.handle?.schemeSpecificPart
        return if (HiddenCaller.isHidden(details.handlePresentation, number)) BLOCK else ALLOW
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SETTINGS_TIMEOUT_MS = 1_500L

        val ALLOW: CallResponse = CallResponse.Builder().build()

        val BLOCK: CallResponse = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(true)
            .setSkipCallLog(false)
            .build()
    }
}
