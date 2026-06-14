package com.phonepvr.friends.data.calllog

import com.phonepvr.friends.domain.model.CallType
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

    /**
     * Parses CSV produced by [toCsv] back into calls. Skips the header, blank
     * lines, and any malformed row (too few columns, unknown type, or a
     * non-numeric date), so a partial or hand-edited file imports what it can.
     * The provider id isn't in the CSV, so parsed calls carry id 0.
     */
    fun fromCsv(text: String): List<DeviceCall> {
        val out = mutableListOf<DeviceCall>()
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val cols = line.split(',')
            if (cols.size < 4) continue
            // Unknown type (incl. the header's "type") and a non-numeric date
            // (incl. the header's "date_epoch_ms") both fall through to skip.
            val type = runCatching { CallType.valueOf(cols[1].trim()) }.getOrNull() ?: continue
            val date = cols[2].trim().toLongOrNull() ?: continue
            val duration = cols[3].trim().toLongOrNull() ?: 0L
            out.add(
                DeviceCall(
                    number = cols[0].trim(),
                    type = type,
                    timestampMillis = date,
                    durationSeconds = duration,
                ),
            )
        }
        return out
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
