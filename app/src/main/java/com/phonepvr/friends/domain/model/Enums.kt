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
}

enum class ConfirmationStatus {
    PENDING,
    CONFIRMED,
    DISMISSED,
}
