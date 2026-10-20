package com.phonepvr.friends.ui.dialer

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.phonepvr.friends.data.dialer.PastedNumber

/**
 * Whether the clipboard probably holds a phone number, judged WITHOUT reading it.
 *
 * Reading the clip (ClipboardManager.getPrimaryClip) shows Android 12+'s
 * "Bondwidth pasted from your clipboard" notice, so it only happens when the user
 * taps Paste. Peeking at the ClipDescription doesn't: it says whether the clip is
 * text and, on Android 12+, how confident the system's text classifier is that it
 * contains a phone number.
 */
@Composable
fun rememberClipboardHasNumber(): Boolean {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    var hasNumber by remember { mutableStateOf(false) }
    // Since Android 10 only the focused app may look at the clipboard, so check again
    // whenever this window gains focus (coming back from the app the number was
    // copied in) and whenever the clipboard changes while it is open.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(clipboard, focused) {
        if (focused) hasNumber = clipboard.probablyHoldsNumber()
    }
    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            hasNumber = clipboard.probablyHoldsNumber()
        }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
    }
    return hasNumber
}

private fun ClipboardManager?.probablyHoldsNumber(): Boolean {
    if (this == null) return false
    val description = runCatching { if (hasPrimaryClip()) primaryClipDescription else null }
        .getOrNull() ?: return false
    if (!description.hasMimeType("text/*")) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
    ) {
        // 0 means the classifier found no phone number in the text.
        return runCatching { description.getConfidenceScore(TextClassifier.TYPE_PHONE) > 0f }
            .getOrDefault(true)
    }
    // Older Android, or not classified (yet): any text may hold a number.
    return true
}

/**
 * Reads the clipboard (only call this from a user's Paste tap) and pulls a dialable
 * number out of whatever was copied. Null when there is nothing number-like.
 */
fun readNumberFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return null
    if (clip.itemCount == 0) return null
    return PastedNumber.extract(clip.getItemAt(0).coerceToText(context))
}

fun copyNumberToClipboard(context: Context, number: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Phone number", number))
}
