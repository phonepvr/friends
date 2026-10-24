package com.phonepvr.friends.ui.dialer

import com.phonepvr.friends.domain.model.CallType

/**
 * The filter chips at the top of the Calls tab. Missed means calls you didn't
 * answer; a call you declined counts as incoming (it reached you), as in the stock
 * dialer, and so does one turned away by blocking (it was incoming; the row says
 * "Blocked").
 */
enum class RecentsFilter(val label: String, val emptyMessage: String) {
    ALL("All", "No calls in the last 30 days."),
    MISSED("Missed", "No missed calls in the last 30 days."),
    INCOMING("Incoming", "No incoming calls in the last 30 days."),
    OUTGOING("Outgoing", "No outgoing calls in the last 30 days."),
    ;

    fun matches(type: CallType): Boolean = when (this) {
        ALL -> true
        MISSED -> type == CallType.MISSED
        INCOMING ->
            type == CallType.INCOMING || type == CallType.REJECTED || type == CallType.BLOCKED
        OUTGOING -> type == CallType.OUTGOING
    }
}
