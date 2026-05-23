package com.phonepvr.friends.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single source of truth for date display + parsing in the app.
 *
 * Display format is **dd/MM/yyyy** everywhere — profile, timeline, widget,
 * year-in-review, date-entry forms. No locale fallback: the user explicitly
 * asked for one format, so we keep it predictable.
 */

private val DD_MM_YYYY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val DD_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val DD_MMM_YYYY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")
private val DD_MMM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM")

fun formatDate(date: LocalDate): String = DD_MM_YYYY.format(date)

/** Used by widget rows and any place where the year is implicit (e.g. annual events). */
fun formatDayMonth(date: LocalDate): String = DD_MM.format(date)

/**
 * Used by event surfaces (birthday / anniversary) where we want the month
 * name rather than its number so a glance reveals which month it is.
 * Falls back to `dd-MMM` when the year is unknown.
 */
fun formatEventDay(day: Int, month: Int, year: Int?): String {
    // Use 2000 as a non-leap-safe stand-in when the year is null; only the
    // day + month are shown in that case.
    val safeYear = year ?: 2000
    val safeDay = minOf(day, java.time.YearMonth.of(safeYear, month).lengthOfMonth())
    val date = LocalDate.of(safeYear, month, safeDay)
    return if (year == null) DD_MMM.format(date) else DD_MMM_YYYY.format(date)
}

/** Epoch millis → dd/MM/yyyy in the system zone. */
fun formatTimestamp(epochMillis: Long): String =
    DD_MM_YYYY.format(
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate(),
    )

/** Day / month / optional year extracted from the masked text field. */
data class ParsedDate(val day: Int, val month: Int, val year: Int?) {
    fun toLocalDateOrNull(): LocalDate? {
        val y = year ?: return null
        return runCatching { LocalDate.of(y, month, day) }.getOrNull()
    }
}

/**
 * Parse the raw digit string produced by [DateTextField].
 *
 * Accepts 4 digits (`ddmm`, year unknown — only when the caller allowed
 * year-optional) or 8 digits (`ddmmyyyy`). Returns null for anything else
 * or for impossible day / month / leap-year combinations.
 */
fun parseDateDigits(digits: String): ParsedDate? {
    if (digits.any { !it.isDigit() }) return null
    return when (digits.length) {
        4 -> {
            val day = digits.substring(0, 2).toInt()
            val month = digits.substring(2, 4).toInt()
            if (!isValidDayMonth(day, month, year = null)) return null
            ParsedDate(day = day, month = month, year = null)
        }
        8 -> {
            val day = digits.substring(0, 2).toInt()
            val month = digits.substring(2, 4).toInt()
            val year = digits.substring(4, 8).toInt()
            if (!isValidDayMonth(day, month, year = year)) return null
            ParsedDate(day = day, month = month, year = year)
        }
        else -> null
    }
}

private fun isValidDayMonth(day: Int, month: Int, year: Int?): Boolean {
    if (month !in 1..12) return false
    if (day < 1) return false
    val maxDay = if (year != null) {
        YearMonth.of(year, month).lengthOfMonth()
    } else {
        // Year unknown: allow Feb 29 (a real birthday) but reject 31 in 30-day months.
        when (month) {
            2 -> 29
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
    return day <= maxDay
}

/**
 * Convert an existing day/month/optional-year triple back to the raw digits
 * the masked field stores. Used to seed the form from a saved EventEntity.
 */
fun packDateDigits(day: Int, month: Int, year: Int?): String = buildString {
    append(day.toString().padStart(2, '0'))
    append(month.toString().padStart(2, '0'))
    if (year != null) append(year.toString().padStart(4, '0'))
}

/** Seconds → `Xh Ym` / `Ym Zs` / `Zs`. Moved here so the call duration
 *  display and the YearInReview/widget surfaces share one formatter. */
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val secs = s % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
