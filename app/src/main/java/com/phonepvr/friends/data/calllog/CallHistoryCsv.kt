package com.phonepvr.friends.data.calllog

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * CSV serialisation of device call-log entries for the call-history export.
 *
 * Layout: a header row plus one row per call —
 * `number,type,date_epoch_ms,duration_seconds,timestamp_utc`. The
 * `date_epoch_ms` column is the authoritative timestamp (used on re-import);
 * `timestamp_utc` is an ISO-8601 string for humans reading the file.
 *
 * Pure Kotlin (no Android dependencies) so it's unit-testable on the JVM.
 */
object CallHistoryCsv {

    const val HEADER = "number,type,date_epoch_ms,duration_seconds,timestamp_utc"

    fun toCsv(calls: List<DeviceCall>): String = buildString {
        append(HEADER).append('\n')
        for (call in calls) {
            append(sanitize(call.number)).append(',')
            append(call.type.name).append(',')
            append(call.timestampMillis).append(',')
            append(call.durationSeconds).append(',')
            append(iso(call.timestampMillis)).append('\n')
        }
    }

    private fun iso(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))

    /**
     * Call-log numbers don't contain commas or newlines, but strip them
     * defensively so a single field can never break the column alignment a
     * naive importer relies on.
     */
    private fun sanitize(number: String): String =
        number.replace(",", " ").replace("\r", "").replace("\n", "")
}
