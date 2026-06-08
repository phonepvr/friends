package com.phonepvr.friends.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tasteful, semantic haptics. Maps intent → the right
 * [HapticFeedbackConstants], honouring the system haptic setting and needing
 * no VIBRATE permission. Use via [rememberHaptics].
 *
 *  - [Confirm]   a positive action landed (saved, merged, assigned)
 *  - [Reject]    a destructive / negative action (block, decline)
 *  - [LongPress] a long-press opened a menu/sheet
 *  - [Tick]      a light selection change (tab switch, toggle)
 */
enum class Haptic { Confirm, Reject, LongPress, Tick }

class Haptics(private val view: View) {
    fun perform(kind: Haptic) {
        val constant = when (kind) {
            Haptic.Confirm ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            Haptic.Reject ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            Haptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
            Haptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
