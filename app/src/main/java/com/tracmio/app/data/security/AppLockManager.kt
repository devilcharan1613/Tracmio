package com.tracmio.app.data.security

import android.content.Context
import android.content.SharedPreferences
import com.tracmio.app.data.AppLockState
import java.security.SecureRandom

class AppLockManager(
    context: Context,
    private val securePrefsProvider: (() -> SharedPreferences)? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val appContext = context.applicationContext

    private fun openSecurePrefsOrThrow(): SharedPreferences {
        securePrefsProvider?.let { return it() }
        val masterKey = androidx.security.crypto.MasterKey.Builder(appContext)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val secure = androidx.security.crypto.EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        migrateLegacyPlaintextPrefsIfNeeded(secure)
        return secure
    }

    private fun migrateLegacyPlaintextPrefsIfNeeded(secure: SharedPreferences) {
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacy.contains(KEY_ENABLED) && !secure.contains(KEY_ENABLED)) {
            val committed = secure.edit()
                .putBoolean(KEY_ENABLED, legacy.getBoolean(KEY_ENABLED, false))
                .putString(KEY_PIN_HASH, legacy.getString(KEY_PIN_HASH, ""))
                .putInt(KEY_PIN_LENGTH, legacy.getInt(KEY_PIN_LENGTH, 4))
                .putBoolean(KEY_BIOMETRIC, legacy.getBoolean(KEY_BIOMETRIC, false))
                .putString(KEY_SEC_QUESTION, legacy.getString(KEY_SEC_QUESTION, ""))
                .putString(KEY_SEC_ANSWER_HASH, legacy.getString(KEY_SEC_ANSWER_HASH, ""))
                .putString(KEY_RECOVERY_KEY_HASH, legacy.getString(KEY_RECOVERY_KEY_HASH, ""))
                .commit()
            if (committed) {
                legacy.edit().clear().commit()
            }
        } else if (legacy.all.isNotEmpty()) {
            legacy.edit().clear().apply()
        }
        // Always scrub any legacy plaintext recovery key if present from older builds
        if (secure.contains(KEY_LEGACY_RECOVERY_PLAIN)) {
            secure.edit().remove(KEY_LEGACY_RECOVERY_PLAIN).apply()
        }
    }

    private val prefsResult: Result<SharedPreferences> = runCatching { openSecurePrefsOrThrow() }

    val isSecureStorageAvailable: Boolean
        get() = prefsResult.isSuccess

    private fun requireSecurePrefs(): SharedPreferences {
        return prefsResult.getOrElse { err ->
            throw IllegalStateException("Android Keystore secure storage unavailable for App Lock: ${err.localizedMessage}", err)
        }
    }

    fun enableLock(
        pin: String,
        biometricEnabled: Boolean,
        securityQuestion: String,
        securityAnswer: String
    ): String {
        val prefs = requireSecurePrefs()
        val cleanPin = pin.filter { it.isDigit() }
        require(cleanPin.length in 4..6) { "PIN must be 4 to 6 digits" }
        val cleanQuestion = securityQuestion.trim()
        val cleanAnswer = securityAnswer.trim().lowercase()
        require(cleanQuestion.isNotBlank() && cleanAnswer.isNotBlank()) { "Security question and answer are required" }

        val recoveryKey = generateRecoveryKey()
        val committed = prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_PIN_HASH, TracmioSecurityEngine.hashSecret(cleanPin))
            .putInt(KEY_PIN_LENGTH, cleanPin.length)
            .putBoolean(KEY_BIOMETRIC, biometricEnabled)
            .putString(KEY_SEC_QUESTION, cleanQuestion)
            .putString(KEY_SEC_ANSWER_HASH, TracmioSecurityEngine.hashSecret(cleanAnswer))
            .putString(KEY_RECOVERY_KEY_HASH, TracmioSecurityEngine.hashSecret(recoveryKey.uppercase()))
            .remove(KEY_LEGACY_RECOVERY_PLAIN)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .commit()
        check(committed) { "Failed to persist App Lock credentials" }
        return recoveryKey
    }

    fun disableLock() {
        val prefs = requireSecurePrefs()
        prefs.edit()
            .putBoolean(KEY_ENABLED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .remove(KEY_LEGACY_RECOVERY_PLAIN)
            .commit()
    }

    fun getRemainingLockoutSeconds(nowMillis: Long = timeProvider()): Int {
        val prefs = prefsResult.getOrNull() ?: return 0
        val untilMs = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        if (untilMs <= nowMillis) return 0
        return (((untilMs - nowMillis) + 999L) / 1000L).toInt().coerceAtLeast(1)
    }

    fun getFailedAttempts(): Int {
        return prefsResult.getOrNull()?.getInt(KEY_FAILED_ATTEMPTS, 0) ?: 0
    }

    fun getState(): AppLockState {
        val prefs = prefsResult.getOrNull()
        if (prefs == null) {
            return AppLockState(
                isEnabled = false,
                secureStorageAvailable = false
            )
        }
        val now = timeProvider()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        val remainingSec = if (lockoutUntil > now) (((lockoutUntil - now) + 999L) / 1000L).toInt().coerceAtLeast(1) else 0
        return AppLockState(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            pin = "",
            pinLength = prefs.getInt(KEY_PIN_LENGTH, 4),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
            securityQuestion = prefs.getString(KEY_SEC_QUESTION, "") ?: "",
            securityAnswerHash = "",
            recoveryKey = "",
            failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            lockoutRemainingSeconds = remainingSec,
            secureStorageAvailable = true
        )
    }

    fun verifyPin(candidatePin: String): Boolean {
        val prefs = prefsResult.getOrNull() ?: return false
        val now = timeProvider()
        if (prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L) > now) {
            return false
        }
        val cleanCandidate = candidatePin.trim()
        val stored = prefs.getString(KEY_PIN_HASH, "") ?: ""
        val matched = TracmioSecurityEngine.verifySecret(cleanCandidate, stored)
        if (matched) {
            val editor = prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            if (TracmioSecurityEngine.isLegacySha256Hash(stored)) {
                editor.putString(KEY_PIN_HASH, TracmioSecurityEngine.hashSecret(cleanCandidate))
            }
            editor.commit()
            return true
        }

        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val cooldownMs = when {
            attempts >= 10 -> 300_000L
            attempts >= 8 -> 60_000L
            attempts >= 5 -> 30_000L
            else -> 0L
        }
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .putLong(KEY_LOCKOUT_UNTIL_MS, if (cooldownMs > 0L) now + cooldownMs else 0L)
            .commit()
        return false
    }

    fun resetLockoutOnBiometricSuccess() {
        val prefs = prefsResult.getOrNull() ?: return
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .commit()
    }

    fun verifyRecoveryAndReset(secretOrRecoveryKey: String, newPin: String?): Boolean {
        val prefs = prefsResult.getOrNull() ?: return false
        val trimmed = secretOrRecoveryKey.trim()
        val answerHash = prefs.getString(KEY_SEC_ANSWER_HASH, "") ?: ""
        val keyHash = prefs.getString(KEY_RECOVERY_KEY_HASH, "") ?: ""

        val matchesAnswer = answerHash.isNotEmpty() && TracmioSecurityEngine.verifySecret(trimmed.lowercase(), answerHash)
        val matchesKey = keyHash.isNotEmpty() && TracmioSecurityEngine.verifySecret(trimmed.uppercase(), keyHash)
        if (!matchesAnswer && !matchesKey) return false

        if (matchesAnswer && TracmioSecurityEngine.isLegacySha256Hash(answerHash)) {
            prefs.edit().putString(KEY_SEC_ANSWER_HASH, TracmioSecurityEngine.hashSecret(trimmed.lowercase())).commit()
        }
        if (matchesKey && TracmioSecurityEngine.isLegacySha256Hash(keyHash)) {
            prefs.edit().putString(KEY_RECOVERY_KEY_HASH, TracmioSecurityEngine.hashSecret(trimmed.uppercase())).commit()
        }

        if (newPin.isNullOrBlank()) {
            disableLock()
        } else {
            val clean = newPin.filter { it.isDigit() }
            require(clean.length in 4..6) { "New PIN must be 4 to 6 digits" }
            prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_PIN_HASH, TracmioSecurityEngine.hashSecret(clean))
                .putInt(KEY_PIN_LENGTH, clean.length)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
                .commit()
        }
        return true
    }

    private fun generateRecoveryKey(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        fun block() = (1..4).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        return "TRAC-${block()}-${block()}"
    }

    companion object {
        private const val SECURE_PREFS_NAME = "tracmio_applock_secure_prefs"
        private const val LEGACY_PREFS_NAME = "tracmio_applock_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_SEC_QUESTION = "sec_question"
        private const val KEY_SEC_ANSWER_HASH = "sec_answer_hash"
        private const val KEY_RECOVERY_KEY_HASH = "recovery_key_hash"
        private const val KEY_LEGACY_RECOVERY_PLAIN = "recovery_key_plain"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_MS = "lockout_until_ms"
    }
}
