package com.tracmio.app.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class TracmioSecurityEngineTest {
    @Test
    fun backupEncryptionAndDecryptionRoundtripWorks() {
        val originalJson = """{"app":"Tracmio","versionName":"16.13","balance":25000}"""
        val password = "StrongVaultPassword!1613".toCharArray()
        val encrypted = TracmioSecurityEngine.encryptBackup(originalJson, password)

        val envelope = org.json.JSONObject(encrypted)
        assertEquals("AES/GCM/NoPadding", envelope.getString("cipher"))
        assertEquals("PBKDF2WithHmacSHA256", envelope.getString("kdf"))
        assertTrue(envelope.getInt("iterations") >= 100_000)
        assertNotEquals(originalJson, encrypted)

        val decrypted = TracmioSecurityEngine.decryptBackup(encrypted, password)
        assertEquals(originalJson, decrypted)
    }

    @Test
    fun backupDecryptionRejectsWrongPasswordAndTamperedEnvelope() {
        val originalJson = """{"app":"Tracmio","accounts":[{"name":"Bank","balance":1000}]}"""
        val encrypted = TracmioSecurityEngine.encryptBackup(originalJson, "CorrectPass123!".toCharArray())

        val wrongPassFailed = runCatching {
            TracmioSecurityEngine.decryptBackup(encrypted, "WrongPass123!".toCharArray())
        }.isFailure
        assertTrue(wrongPassFailed)

        val tampered = org.json.JSONObject(encrypted).apply {
            val ct = getString("cipherText")
            val flipped = (if (ct.first() == 'A') 'B' else 'A') + ct.drop(1)
            put("cipherText", flipped)
        }.toString()
        assertTrue(
            runCatching {
                TracmioSecurityEngine.decryptBackup(tampered, "CorrectPass123!".toCharArray())
            }.isFailure
        )

        val lowIter = org.json.JSONObject(encrypted).apply {
            put("iterations", 1000)
        }.toString()
        assertTrue(
            runCatching {
                TracmioSecurityEngine.decryptBackup(lowIter, "CorrectPass123!".toCharArray())
            }.isFailure
        )
    }

    @Test
    fun secretHashingUsesSaltedPbkdf2AndSupportsLegacyUpgradeVerify() {
        val hash1 = TracmioSecurityEngine.hashSecret("1234")
        val hash2 = TracmioSecurityEngine.hashSecret("1234")

        assertTrue(hash1.startsWith("pbkdf2_sha256$"))
        assertNotEquals(hash1, hash2)
        assertNotEquals("1234", hash1)
        assertTrue(TracmioSecurityEngine.verifySecret("1234", hash1))
        assertTrue(TracmioSecurityEngine.verifySecret("1234", hash2))
        assertFalse(TracmioSecurityEngine.verifySecret("5678", hash1))

        val legacySha = MessageDigest.getInstance("SHA-256")
            .digest("tracmio_pin_salt:2468".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertTrue(TracmioSecurityEngine.isLegacySha256Hash(legacySha))
        assertTrue(TracmioSecurityEngine.verifySecret("2468", legacySha))
        assertFalse(TracmioSecurityEngine.verifySecret("9999", legacySha))
    }

    @Test
    fun sanitizeInputStripsScriptsAndControlChars() {
        val dirty = "  <script>alert('xss')</script>Coffee \u0007 #chai  "
        val clean = TracmioSecurityEngine.sanitizeInput(dirty, 50)
        assertEquals("Coffee  #chai", clean)
    }
}
