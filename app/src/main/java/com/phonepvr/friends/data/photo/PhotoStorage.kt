package com.phonepvr.friends.data.photo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores contact photos as files under `filesDir/photos`. Paths are kept
 * relative to `filesDir` so they stay valid after a backup is restored onto a
 * different device.
 */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val photosDir: File
        get() = File(context.filesDir, PHOTOS_DIR)

    /** Resolves a stored photo's relative path to an absolute file. */
    fun fileFor(relativePath: String): File = File(context.filesDir, relativePath)

    /** Copies [input] into the photos folder and returns its relative path. */
    fun savePhoto(uuid: String, input: InputStream): String {
        val dir = photosDir.apply { mkdirs() }
        val file = File(dir, "$uuid.jpg")
        file.outputStream().use { output -> input.copyTo(output) }
        return "$PHOTOS_DIR/${file.name}"
    }

    /** Every stored photo file, used when assembling a backup. */
    fun allPhotos(): List<File> =
        photosDir.listFiles()?.filter { it.isFile }.orEmpty()

    /** Replaces all stored photos with [photos], keyed by relative path. */
    fun replaceAll(photos: Map<String, ByteArray>) {
        photosDir.deleteRecursively()
        photos.forEach { (relativePath, bytes) ->
            val file = fileFor(relativePath)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    companion object {
        const val PHOTOS_DIR = "photos"
    }
}
