package com.phonepvr.friends.ui.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.domain.quotes.Quote
import com.phonepvr.friends.domain.quotes.QuoteRepository
import com.phonepvr.friends.role.DialerRoleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val quoteRepository: QuoteRepository,
    private val dialerRoleManager: DialerRoleManager,
) : ViewModel() {

    private val _quote = MutableStateFlow<Quote?>(null)
    /** Today's quote, shown on the welcome slide. */
    val quote: StateFlow<Quote?> = _quote.asStateFlow()

    private val _isDefaultDialer = MutableStateFlow(dialerRoleManager.isDefaultDialer())
    /** Whether Friends is currently the system default phone app. */
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    init {
        viewModelScope.launch {
            _quote.value = quoteRepository.quoteOfTheDay()
        }
    }

    /**
     * Re-read after returning from the system "default phone app" picker.
     * Returns the fresh state so the caller can guide recovery when the
     * request didn't take (e.g. Android 13+ silently blocks the role for
     * sideloaded apps via "restricted settings").
     */
    fun refreshDefaultDialer(): Boolean {
        val isDefault = dialerRoleManager.isDefaultDialer()
        _isDefaultDialer.value = isDefault
        return isDefault
    }

    /** Intent for the default-phone-app picker, or null if unavailable here. */
    fun makeDialerRoleIntent(): Intent? = dialerRoleManager.makeAcquireRoleIntent()

    /** Direct link to system Settings → Apps → Default apps. */
    fun makeDefaultAppsSettingsIntent(): Intent =
        dialerRoleManager.makeDefaultAppsSettingsIntent()

    /** Link to this app's "App info" page for the restricted-settings toggle. */
    fun makeAppInfoIntent(): Intent = dialerRoleManager.makeAppInfoIntent()
}
