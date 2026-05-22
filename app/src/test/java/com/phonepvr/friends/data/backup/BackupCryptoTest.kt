package com.phonepvr.friends.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupCryptoTest {

    @Test
    fun encryptThenDecrypt_roundTripsPayload() {
        val payload = "the quick brown fox jumps over the lazy dog".repeat(40)
            .toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(payload, "correct horse battery".toCharArray())
        val decrypted = BackupCrypto.decrypt(encrypted, "correct horse battery".toCharArray())
        assertArrayEquals(payload, decrypted)
    }

    @Test
    fun encryptThenDecrypt_emptyPayload_roundTrips() {
        val encrypted = BackupCrypto.encrypt(ByteArray(0), "pw".toCharArray())
        assertArrayEquals(ByteArray(0), BackupCrypto.decrypt(encrypted, "pw".toCharArray()))
    }

    @Test
    fun encryptedOutput_isDetectedAsEncrypted() {
        val encrypted = BackupCrypto.encrypt(byteArrayOf(1, 2, 3), "pw".toCharArray())
        assertTrue(BackupCrypto.isEncrypted(encrypted))
    }

    @Test
    fun plainZipBytes_areNotDetectedAsEncrypted() {
        // A real ZIP starts with the local file header magic "PK".
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)
        assertFalse(BackupCrypto.isEncrypted(zipBytes))
    }

    @Test
    fun shortInput_isNotDetectedAsEncrypted() {
        assertFalse(BackupCrypto.isEncrypted(byteArrayOf(1, 2)))
        assertFalse(BackupCrypto.isEncrypted(ByteArray(0)))
    }

    @Test(expected = AEADBadTagException::class)
    fun decrypt_withWrongPassphrase_throws() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "right".toCharArray())
        BackupCrypto.decrypt(encrypted, "wrong".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun decrypt_plainBytes_throws() {
        BackupCrypto.decrypt(byteArrayOf(0x50, 0x4B, 0x03, 0x04), "pw".toCharArray())
    }

    @Test
    fun ciphertext_differsEachTime_forRandomSaltAndIv() {
        val payload = "the same input every time".toByteArray()
        val first = BackupCrypto.encrypt(payload, "pw".toCharArray())
        val second = BackupCrypto.encrypt(payload, "pw".toCharArray())
        assertFalse(first.contentEquals(second))
    }
}
