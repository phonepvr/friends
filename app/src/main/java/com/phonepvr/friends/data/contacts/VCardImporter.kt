package com.phonepvr.friends.data.contacts

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of importing a vCard stream. */
data class VCardImportResult(val imported: Int, val skipped: Int)

/**
 * Reads a vCard file at a content URI and writes each card to the system
 * Contacts provider via [ContactWriter]. Shared by the Settings "Import
 * contacts" flow and the open-a-.vcf-attachment flow so both behave
 * identically. Cards without a usable display name are skipped.
 */
@Singleton
class VCardImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactWriter: ContactWriter,
) {
    /** Parses the file and returns its cards without writing anything — used
     *  for the preview an attachment shows before the user confirms. */
    suspend fun preview(source: Uri): List<ParsedVCard> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use {
            it.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IllegalStateException("Couldn't open the vCard file.")
        VCardParser.parse(text)
    }

    /**
     * Writes [cards] to the contacts provider. [onProgress] fires before
     * each insert with (index, total) so the UI can show a counter.
     */
    suspend fun importCards(
        cards: List<ParsedVCard>,
        onProgress: (index: Int, total: Int) -> Unit = { _, _ -> },
    ): VCardImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var skipped = 0
        cards.forEachIndexed { index, card ->
            onProgress(index, cards.size)
            val form = ContactForm(
                displayName = card.displayName,
                // vCard import doesn't preserve type labels (yet) — every
                // number lands as the default Mobile. The user can reassign
                // types in the contact editor.
                phones = card.phones.map { PhoneEntry(number = it) },
                emails = card.emails,
                notes = card.notes.orEmpty(),
                organization = card.organization.orEmpty(),
                birthday = card.birthday,
            )
            if (contactWriter.create(form) != null) imported++ else skipped++
        }
        VCardImportResult(imported, skipped)
    }

    /** Convenience: parse + import in one call, for the Settings flow. */
    suspend fun importFrom(
        source: Uri,
        onProgress: (index: Int, total: Int) -> Unit = { _, _ -> },
    ): VCardImportResult {
        val cards = preview(source)
        return importCards(cards, onProgress)
    }
}
