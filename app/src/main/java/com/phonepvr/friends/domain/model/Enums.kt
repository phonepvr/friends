package com.phonepvr.friends.domain.model

enum class EventType {
    BIRTHDAY,
    WEDDING_ANNIVERSARY,
    CUSTOM,
}

enum class InteractionType {
    CALL,
    MEET,
    MESSAGE,
    OTHER,
}

enum class EntrySource {
    MANUAL,
    CALL_LOG,
}

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,

    /**
     * Turned away by the block list or call screening without ringing. Only Recents
     * reads these (CallLogReader.recentCalls(includeBlocked = true)); they never
     * reach a bond's history, stats or backups.
     */
    BLOCKED,
}

enum class ConfirmationStatus {
    PENDING,
    CONFIRMED,
    DISMISSED,
}
