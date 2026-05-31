package com.phonepvr.friends.ui.contacts

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ParsedVCard
import com.phonepvr.friends.data.contacts.VCardImporter
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the screen shown when the user opens a .vcf attachment. Parses the
 * file up front so the user sees what they're about to add, then imports on
 * confirm. Distinct from the Settings import (which is fire-and-forget) so
 * that an externally-opened file gets a review step before it writes to the
 * address book.
 */
sealed interface ImportVCardState {
    data object Loading : ImportVCardState
    data class Preview(val cards: List<ParsedVCard>) : ImportVCardState
    data class Importing(val done: Int, val total: Int) : ImportVCardState
    data class Done(val imported: Int, val skipped: Int) : ImportVCardState
    data class Error(val message: String) : ImportVCardState
}

@HiltViewModel
class ImportVCardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val importer: VCardImporter,
) : ViewModel() {

    private val sourceUri: Uri? =
        savedStateHandle.get<String>(Routes.IMPORT_VCARD_URI_ARG)
            ?.let { runCatching { Uri.parse(Uri.decode(it)) }.getOrNull() }

    private val _state = MutableStateFlow<ImportVCardState>(ImportVCardState.Loading)
    val state: StateFlow<ImportVCardState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val uri = sourceUri
        if (uri == null) {
            _state.value = ImportVCardState.Error("No file to open.")
            return
        }
        viewModelScope.launch {
            _state.value = runCatching { importer.preview(uri) }.fold(
                onSuccess = { cards ->
                    if (cards.isEmpty()) {
                        ImportVCardState.Error("No contacts found in this file.")
                    } else {
                        ImportVCardState.Preview(cards)
                    }
                },
                onFailure = { ImportVCardState.Error(it.message ?: "Couldn't read the file.") },
            )
        }
    }

    fun confirmImport() {
        val cards = (_state.value as? ImportVCardState.Preview)?.cards ?: return
        _state.value = ImportVCardState.Importing(0, cards.size)
        viewModelScope.launch {
            _state.value = runCatching {
                importer.importCards(cards) { index, total ->
                    _state.value = ImportVCardState.Importing(index, total)
                }
            }.fold(
                onSuccess = { ImportVCardState.Done(it.imported, it.skipped) },
                onFailure = { ImportVCardState.Error(it.message ?: "Import failed.") },
            )
        }
    }
}
