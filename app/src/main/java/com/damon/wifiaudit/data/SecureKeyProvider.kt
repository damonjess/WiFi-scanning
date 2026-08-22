package com.damon.wifiaudit.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates a random 256-bit SQLCipher passphrase once, then seals it inside
 * EncryptedSharedPreferences, which itself is backed by an AES key held in the
 * Android Keystore (StrongBox/TEE-backed where available). The raw passphrase
 * never touches disk in plaintext and never lives in source/APK.
 */
object SecureKeyProvider {
    private const val PREFS_NAME = "wifi_audit_secure_prefs"
    private const val KEY_ALIAS = "db_passphrase"

    fun getOrCreateDbPassphrase(context: Context): ByteArray {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val existing = prefs.getString(KEY_ALIAS, null)
            if (existing != null) {
                return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
            }

            val newKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
            prefs.edit()
                .putString(KEY_ALIAS, android.util.Base64.encodeToString(newKey, android.util.Base64.NO_WRAP))
                .apply()
            return newKey
        } catch (e: Exception) {
            android.util.Log.e("SecureKeyProvider", "EncryptedSharedPreferences corruption detected, resetting", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            
            // If we reset the key, the existing encrypted database is toast.
            try {
                context.deleteDatabase("wifi_audit_encrypted.db")
            } catch (ex: Exception) {
                // Ignore failure to delete
            }

            // Recurse once to regenerate
            return getOrCreateDbPassphrase(context)
        }
    }
}
