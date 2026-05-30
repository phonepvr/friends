package com.phonepvr.friends.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import java.io.File

/**
 * Circular avatar showing a contact's photo, falling back to their initial
 * when none is available. The photo can come from two places, tried in
 * priority order:
 *
 *  1. [photoRelativePath] — a local JPEG under filesDir, copied when the
 *     contact was bonded. Survives backup/restore and needs no contacts
 *     permission to read.
 *  2. [photoUri] — the live system-contact photo (a content:// URI from
 *     ContactsContract.PHOTO_URI). Lets NON-bonded contacts show their
 *     picture everywhere, not just on the detail screen.
 *  3. The person's initial in a tinted bubble.
 *
 * Coil's AsyncImage reads both a File and a content URI; neither touches
 * the network, so this stays compatible with Bondwidth's no-INTERNET rule.
 */
@Composable
fun PersonAvatar(
    photoRelativePath: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    photoUri: String? = null,
) {
    val context = LocalContext.current
    val photoFile = remember(photoRelativePath) {
        photoRelativePath
            ?.let { File(context.filesDir, it) }
            ?.takeIf { it.exists() }
    }
    // Local file wins; otherwise hand Coil the system photo URI as-is.
    val model: Any? = photoFile ?: photoUri?.takeIf { it.isNotBlank() }?.toUri()
    if (model != null) {
        AsyncImage(
            model = model,
            // Decorative: the person's name is always shown alongside it.
            contentDescription = null,
            modifier = modifier
                .size(diameter)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .size(diameter)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
