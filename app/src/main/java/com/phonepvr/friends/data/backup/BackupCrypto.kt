package com.phonepvr.friends.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional passphrase encryption for backup files. The payload (a ZIP) is
 * sealed with AES-256-GCM; the key is stretched from the passphrase with
 * PBKDF2. An encrypted file is laid out as:
 *
 *     [magic header][16-byte salt][12-byte IV][GCM ciphertext + tag]
 *
 * The magic header lets import tell an encrypted backup from a plain ZIP
 * without relying on the file name. Everything here comes from the JDK, so
 * encryption needs no network access.
 */
object BackupCrypto {
    private val MAGIC = "FRIENDSBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** True when [data] starts with the encrypted-backup magic header. */
    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(passphrase, salt),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return MAGIC + salt + iv + cipher.doFinal(plaintext)
    }

    /**
     * @throws javax.crypto.AEADBadTagException when the passphrase is wrong.
     * @throws IllegalArgumentException when the file is not a well-formed
     *   encrypted backup.
     */
    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        require(isEncrypted(data)) { "Not an encrypted backup." }
        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_LENGTH
        val cipherStart = ivStart + IV_LENGTH
        require(data.size > cipherStart) { "Encrypted backup is truncated." }
        val salt = data.copyOfRange(saltStart, ivStart)
        val iv = data.copyOfRange(ivStart, cipherStart)
        val ciphertext = data.copyOfRange(cipherStart, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(passphrase, salt),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(keyBytes, "AES")
    }
}
