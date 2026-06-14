package com.phonepvr.friends.data.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads an image at a content URI and produces a square JPEG suitable for a
 * Contacts.Photo data row. The decode is downsampled near [MAX_DIMENSION] to
 * keep memory bounded when the user picks a 12-megapixel photo, then
 * centre-cropped to match every other Android contacts app so the result looks
 * right when the system trims it to a circle.
 */
@Singleton
class ContactPhotoProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val source = decodeBitmap(uri) ?: return@runCatching null
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

    /**
     * Decodes [uri] to a downsampled software Bitmap.
     *
     * Prefers [ImageDecoder] on API 28+: it reads HEIC/HEIF, WebP and AVIF —
     * the formats modern phone cameras and galleries hand back, which the old
     * two-pass BitmapFactory path silently failed to decode (the symptom was
     * "Couldn't read that image" even though Coil could preview it) — and it
     * applies the photo's EXIF orientation. A software allocator is requested
     * so the result can be cropped and re-compressed (hardware bitmaps can't).
     * Falls back to BitmapFactory if ImageDecoder throws, and is the only path
     * on API 26–27.
     */
    private fun decodeBitmap(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val viaImageDecoder = runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val sample = computeSampleSize(
                        info.size.width,
                        info.size.height,
                        MAX_DIMENSION * 2,
                    )
                    if (sample > 1) decoder.setTargetSampleSize(sample)
                }
            }.getOrNull()
            if (viaImageDecoder != null) return viaImageDecoder
        }
        return decodeWithBitmapFactory(uri)
    }

    private fun decodeWithBitmapFactory(uri: Uri): Bitmap? {
        // First pass: read the dimensions so we can pick an inSampleSize that
        // avoids decoding a 50MB Bitmap into memory just to throw most of it away.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION * 2)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
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
