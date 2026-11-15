package com.tracmio.app.data.security

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object TracmioSecurityEngine {
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGO = "AES/GCM/NoPadding"
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val BACKUP_ITERATIONS = 150_000
    private const val SECRET_HASH_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val LEGACY_PIN_SALT = "tracmio_pin_salt"
    private const val HASH_PREFIX = "pbkdf2_sha256"
    private const val MAX_PLAIN_BACKUP_CHARS = 25 * 1024 * 1024
    private const val MAX_ENVELOPE_CHARS = 40 * 1024 * 1024

    sealed class VaultInitState {
        data object Loading : VaultInitState()
        data object Ready : VaultInitState()
        data class SecurityError(
            val title: String,
            val detail: String,
            val canRetry: Boolean = true
        ) : VaultInitState()
    }

    data class DatabaseHealthReport(
        val isHealthy: Boolean,
        val pragmaIntegrity: String,
        val foreignKeyErrors: Int,
        val accountCount: Int,
        val transactionCount: Int,
        val trackerCount: Int,
        val orphanedTransactions: Int,
        val orphanDependentRecords: Int = 0,
        val duplicateUidCount: Int = 0,
        val invalidAmountCount: Int = 0,
        val diagnosticSummary: String
    )

    fun hashSecret(secret: String, iterations: Int = SECRET_HASH_ITERATIONS): String {
        require(secret.isNotEmpty()) { "Secret must not be empty" }
        val safeIterations = iterations.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(secret.toCharArray(), salt, safeIterations, KEY_LENGTH_BITS)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        hash.fill(0)
        return "$HASH_PREFIX$$safeIterations$$saltB64$$hashB64"
    }

    fun isLegacySha256Hash(storedHash: String): Boolean {
        return storedHash.length == 64 && storedHash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    fun verifySecret(candidate: String, storedHash: String): Boolean {
        if (candidate.isEmpty() || storedHash.isEmpty()) return false
        if (storedHash.startsWith("$HASH_PREFIX$")) {
            val parts = storedHash.split("$")
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return false
            val salt = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return false
            val expected = runCatching { Base64.decode(parts[3], Base64.NO_WRAP) }.getOrNull() ?: return false
            if (salt.size < 16 || expected.size < 16) return false
            val actual = pbkdf2(candidate.toCharArray(), salt, iterations, expected.size * 8)
            val match = MessageDigest.isEqual(expected, actual)
            actual.fill(0)
            return match
        }
        if (isLegacySha256Hash(storedHash)) {
            val legacyDigest = sha256Hex("$LEGACY_PIN_SALT:$candidate")
            return MessageDigest.isEqual(
                storedHash.lowercase().toByteArray(Charsets.UTF_8),
                legacyDigest.toByteArray(Charsets.UTF_8)
            )
        }
        return false
    }

    private fun pbkdf2(chars: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(chars, salt, iterations, keyBits)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun sha256Hex(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun encryptBackup(plainJson: String, password: CharArray): String {
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(plainJson.length <= MAX_PLAIN_BACKUP_CHARS) { "Backup payload exceeds maximum size" }
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val spec = PBEKeySpec(password.copyOf(), salt, BACKUP_ITERATIONS, KEY_LENGTH_BITS)
        val secretKeyBytes = try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }

        val cipherBytes = try {
            val keySpec = SecretKeySpec(secretKeyBytes, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        } finally {
            secretKeyBytes.fill(0)
        }

        val envelope = JSONObject()
        envelope.put("kdf", PBKDF2_ALGO)
        envelope.put("cipher", CIPHER_ALGO)
        envelope.put("iterations", BACKUP_ITERATIONS)
        envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        envelope.put("cipherText", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        return envelope.toString()
    }

    fun decryptBackup(encryptedEnvelopeJson: String, password: CharArray): String {
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(encryptedEnvelopeJson.length <= MAX_ENVELOPE_CHARS) { "Encrypted backup envelope exceeds allowed size" }
        val obj = JSONObject(encryptedEnvelopeJson)
        val kdfName = obj.optString("kdf", "")
        val cipherName = obj.optString("cipher", "")
        val iterations = obj.optInt("iterations", 0)
        require(kdfName == PBKDF2_ALGO) { "Unsupported KDF: $kdfName" }
        require(cipherName == CIPHER_ALGO) { "Unsupported cipher: $cipherName" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid PBKDF2 iteration count: $iterations" }

        val salt = Base64.decode(obj.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
        val cipherBytes = Base64.decode(obj.getString("cipherText"), Base64.NO_WRAP)
        require(salt.size in 16..64) { "Invalid KDF salt length" }
        require(iv.size == 12) { "Invalid GCM IV length" }
        require(cipherBytes.size >= 16) { "Corrupted ciphertext payload" }

        val spec = PBEKeySpec(password.copyOf(), salt, iterations, KEY_LENGTH_BITS)
        val secretKeyBytes = try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }

        val plainBytes = try {
            val keySpec = SecretKeySpec(secretKeyBytes, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherBytes)
        } finally {
            secretKeyBytes.fill(0)
        }
        return String(plainBytes, Charsets.UTF_8)
    }

    fun sanitizeInput(raw: String, maxLength: Int = 500): String {
        val noScripts = raw
            .replace(Regex("(?i)<script[^>]*>.*?</script>"), "")
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .trim()
        return if (noScripts.length > maxLength) noScripts.take(maxLength) else noScripts
    }
}
