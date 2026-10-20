package com.phonepvr.friends.domain.model

/**
 * Whether a call from the device call log counts as "staying in touch" with a bond,
 * i.e. moves its cadence clock and its "last contact" date.
 *
 * Only real conversations count: an incoming or outgoing call that stayed connected
 * for at least [MIN_CONNECTED_SECONDS]. Missed and rejected calls never count, and
 * neither do calls that were picked up and dropped within a couple of seconds
 * (an accidental tap, an instant hang-up, a voicemail greeting cut short).
 */
object CallContactRule {

    const val MIN_CONNECTED_SECONDS = 3L

    fun countsAsContact(type: CallType?, durationSeconds: Long?): Boolean =
        (type == CallType.INCOMING || type == CallType.OUTGOING) &&
            (durationSeconds ?: 0L) >= MIN_CONNECTED_SECONDS
}
