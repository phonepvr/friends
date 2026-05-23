package com.phonepvr.friends.domain.quotes

/**
 * One quote ready to render. [attribution] is null for original lines (entries
 * 61-100 of the bundled set) and for user-added quotes that don't carry an
 * author. The Settings UI shows the attribution after a long em-dash; widgets
 * show just the text.
 */
data class Quote(val text: String, val attribution: String?)
