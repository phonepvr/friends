package com.phonepvr.friends.data.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads an image at a content URI and produces a square JPEG suitable for a
 * Contacts.Photo data row. Two-pass decode (bounds → sampled) keeps memory
 * bounded when the user picks a 12-megapixel photo from the gallery, and the
 * centre-crop matches every other Android contacts app so the result looks
 * right when the system trims it to a circle.
 */
@Singleton
class ContactPhotoProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            // First pass: read the dimensions so we can pick an inSampleSize
            // that avoids decoding a 50MB Bitmap into memory just to throw
            // most of it away.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@runCatching null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = computeSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    MAX_DIMENSION * 2,
                )
            }
            val source = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@runCatching null

            val cropped = centreSquareCrop(source)
            val scaled = if (cropped.width <= MAX_DIMENSION) {
                cropped
            } else {
                Bitmap.createScaledBitmap(cropped, MAX_DIMENSION, MAX_DIMENSION, true)
            }
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        }.getOrNull()
    }

    private fun centreSquareCrop(source: Bitmap): Bitmap {
        if (source.width == source.height) return source
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        return Bitmap.createBitmap(source, x, y, side, side)
    }

    private fun computeSampleSize(width: Int, height: Int, target: Int): Int {
        var size = 1
        while (width / size > target || height / size > target) size *= 2
        return size
    }

    private companion object {
        // The contacts provider thumbnails down to ~96px itself; storing
        // 720px gives the full-size shot enough resolution for the in-call
        // hero header without ballooning the contacts DB.
        const val MAX_DIMENSION = 720
        const val JPEG_QUALITY = 85
    }
}
