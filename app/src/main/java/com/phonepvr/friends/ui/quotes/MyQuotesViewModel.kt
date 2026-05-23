package com.phonepvr.friends.ui.quotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.domain.quotes.Quote
import com.phonepvr.friends.domain.quotes.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyQuotesViewModel @Inject constructor(
    private val quoteRepository: QuoteRepository,
) : ViewModel() {

    private val _todayQuote = MutableStateFlow<Quote?>(null)
    val todayQuote: StateFlow<Quote?> = _todayQuote.asStateFlow()

    val userQuotes: StateFlow<List<String>> = quoteRepository.userQuotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // First fetch happens once on construction; the cache layer keeps
        // every subsequent call cheap until the date rolls over.
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _todayQuote.value = quoteRepository.quoteOfTheDay()
        }
    }

    fun addQuote(text: String) {
        viewModelScope.launch { quoteRepository.addUserQuote(text) }
    }

    fun removeQuote(text: String) {
        viewModelScope.launch { quoteRepository.removeUserQuote(text) }
    }
}
