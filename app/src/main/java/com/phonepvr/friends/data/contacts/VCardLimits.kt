package com.phonepvr.friends.data.contacts

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** The file (or the list of cards in it) is bigger than a vCard import will accept. */
class VCardTooLargeException(message: String) : IOException(message)

/**
 * Size limits for vCard files opened from outside the app (an attachment, a file
 * manager). The stream is untrusted: without a cap, a huge or never-ending one
 * would be read fully into memory and crash the app.
 */
object VCardLimits {
    /** Plenty for thousands of contacts, including embedded photos. */
    const val MAX_BYTES = 10 * 1024 * 1024

    /** More cards than this is a bulk-import job, not something to review on a phone. */
    const val MAX_CARDS = 5_000

    /**
     * Reads [input] as UTF-8 text, giving up with [VCardTooLargeException] as soon as it
     * holds more than [maxBytes] — it never buffers beyond that, however long the
     * stream is.
     */
    fun readCapped(input: InputStream, maxBytes: Int = MAX_BYTES): String {
        val buffer = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream()
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > maxBytes) {
                throw VCardTooLargeException(
                    "This file is too large to import (the limit is ${maxBytes / (1024 * 1024)} MB).",
                )
            }
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun requireCardCount(count: Int) {
        if (count > MAX_CARDS) {
            throw VCardTooLargeException("This file has too many contacts to import at once (the limit is $MAX_CARDS).")
        }
    }
}
