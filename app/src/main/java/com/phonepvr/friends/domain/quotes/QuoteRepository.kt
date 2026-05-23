package com.phonepvr.friends.domain.quotes

import android.content.Context
import com.phonepvr.friends.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val ATTR_SEPARATOR = " — "

/**
 * Owns the quote-of-the-day pick.
 *
 * - 100 quotes ship in assets/quotes.txt; users can add their own through
 *   Settings → My quotes. Both lists merge into one rotation.
 * - One pick per device per calendar day. The pick is cached so the app and
 *   the widget show the same quote on the same day even if [quoteOfTheDay]
 *   is called many times.
 * - When the date rolls over the next call re-picks at random from the merged
 *   list, excluding yesterday's quote so two days in a row never match.
 *   Adding a new user quote makes it eligible from the very next pick.
 */
@Singleton
class QuoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    @Volatile private var bundledCache: List<Quote>? = null

    /** Bundled quotes, parsed once and cached for the process lifetime. */
    suspend fun bundledQuotes(): List<Quote> {
        bundledCache?.let { return it }
        val loaded = withContext(Dispatchers.IO) { readBundled() }
        bundledCache = loaded
        return loaded
    }

    /** Live view of quotes the user added themselves. */
    fun userQuotes(): Flow<List<String>> = settingsRepository.settings.map { it.userQuotes }

    suspend fun addUserQuote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        settingsRepository.addUserQuote(trimmed)
    }

    suspend fun removeUserQuote(text: String) {
        settingsRepository.removeUserQuote(text)
    }

    /**
     * Today's quote. Returns the cached pick if it was made today, otherwise
     * draws a fresh one from the merged pool, persists it, and returns it.
     */
    suspend fun quoteOfTheDay(today: LocalDate = LocalDate.now()): Quote {
        val isoToday = today.toString()
        val settings = settingsRepository.settings.first()
        val cachedLine = settings.lastQuoteText
        if (settings.lastQuoteDate == isoToday && cachedLine.isNotBlank()) {
            return parseLine(cachedLine)
        }
        val pool: List<String> = buildList {
            bundledQuotes().forEach { add(toLine(it)) }
            addAll(settings.userQuotes)
        }
        if (pool.isEmpty()) return Quote("Stay in touch.", null) // safety net
        // Exclude yesterday's pick so a fresh day reads fresh. Falls back to
        // the full pool when only one quote exists.
        val candidates = if (pool.size > 1 && cachedLine.isNotBlank()) {
            pool.filter { it != cachedLine }
        } else {
            pool
        }
        val pick = candidates[Random.nextInt(candidates.size)]
        settingsRepository.setLastQuote(date = isoToday, text = pick)
        return parseLine(pick)
    }

    private fun readBundled(): List<Quote> = context.assets.open("quotes.txt")
        .bufferedReader()
        .useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.map(::parseLine).toList()
        }

    private fun toLine(quote: Quote): String = quote.attribution?.let {
        "${quote.text}$ATTR_SEPARATOR$it"
    } ?: quote.text

    private fun parseLine(line: String): Quote {
        val idx = line.indexOf(ATTR_SEPARATOR)
        return if (idx > 0 && idx < line.length - ATTR_SEPARATOR.length) {
            Quote(
                text = line.substring(0, idx),
                attribution = line.substring(idx + ATTR_SEPARATOR.length),
            )
        } else {
            Quote(text = line, attribution = null)
        }
    }
}
