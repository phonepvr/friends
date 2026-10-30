package com.phonepvr.friends.ui.dialer

import com.phonepvr.friends.data.calllog.DeviceCall

/**
 * Recents rows need a key that is unique in the list, or Compose throws and the
 * app closes (issue #68). Time + number is not unique: some phones (seen on a
 * GrapheneOS secondary user) log the same call twice. Every call-log row has its
 * own provider id, so that is the key; synthetic entries (id 0) fall back to their
 * position.
 */
internal object RecentsList {

    /** Drops exact repeats (same time, number, type and duration), keeping the first. */
    fun withoutDuplicates(calls: List<DeviceCall>): List<DeviceCall> =
        calls.distinctBy { listOf(it.timestampMillis, it.number, it.type, it.durationSeconds) }

    fun key(entry: RecentEntry, index: Int): Any =
        if (entry.id != 0L) "call-${entry.id}" else "row-$index-${entry.timestampMillis}-${entry.number}"
}
