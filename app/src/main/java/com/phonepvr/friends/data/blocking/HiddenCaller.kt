package com.phonepvr.friends.data.blocking

import android.telecom.TelecomManager

/**
 * Whether an incoming call hides its number: a private (restricted), unknown,
 * unavailable or payphone presentation, or simply no number. Pure so it can be
 * unit-tested; the screening service applies it.
 */
object HiddenCaller {
    fun isHidden(numberPresentation: Int, number: String?): Boolean =
        numberPresentation != TelecomManager.PRESENTATION_ALLOWED || number.isNullOrBlank()
}
