package com.phonepvr.friends.data.blocking

/**
 * Whether the Block action can work, and if not, why. Android only lets the
 * default phone app (or SMS app) write the system block list, so a phone where
 * Bondwidth isn't the phone app gets an explanation instead of a missing option.
 */
enum class BlockAvailability {
    /** Bondwidth is the phone app and this user can block numbers. */
    AVAILABLE,

    /** Blocking works on this phone, but only once Bondwidth is the phone app. */
    NEEDS_PHONE_APP,

    /** Not possible here at all (tablet, secondary user, no telephony). */
    UNAVAILABLE,
    ;

    companion object {
        fun of(isDefaultPhoneApp: Boolean, userCanBlockNumbers: Boolean): BlockAvailability = when {
            !userCanBlockNumbers -> UNAVAILABLE
            isDefaultPhoneApp -> AVAILABLE
            else -> NEEDS_PHONE_APP
        }
    }
}
