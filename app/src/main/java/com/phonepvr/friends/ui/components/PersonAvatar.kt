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
import coil.compose.AsyncImage
import java.io.File

/**
 * Circular avatar showing the stored contact photo, or the person's initial
 * when no photo is available. The photo is a local file, so no network is used.
 */
@Composable
fun PersonAvatar(
    photoRelativePath: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
) {
    val context = LocalContext.current
    val photoFile = remember(photoRelativePath) {
        photoRelativePath
            ?.let { File(context.filesDir, it) }
            ?.takeIf { it.exists() }
    }
    if (photoFile != null) {
        AsyncImage(
            model = photoFile,
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
