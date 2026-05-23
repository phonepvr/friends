package com.phonepvr.friends.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.domain.quotes.Quote
import com.phonepvr.friends.domain.quotes.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val quoteRepository: QuoteRepository,
) : ViewModel() {

    private val _quote = MutableStateFlow<Quote?>(null)
    /** Today's quote, shown on the welcome slide. */
    val quote: StateFlow<Quote?> = _quote.asStateFlow()

    init {
        viewModelScope.launch {
            _quote.value = quoteRepository.quoteOfTheDay()
        }
    }
}
