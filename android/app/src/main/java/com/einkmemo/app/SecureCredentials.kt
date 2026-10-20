package com.einkmemo.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores WebDAV credentials encrypted with an AES-256-GCM key held in the
 * Android Keystore. The key never leaves the keystore; only a Base64 blob
 * (IV + ciphertext) is kept in private SharedPreferences.
 */
class SecureCredentials(context: Context) {
    private val prefs: SharedPreferences =
        // 存储标识与 Keystore alias 保持旧名，升级后才能解开已保存的 WebDAV 凭据
        context.getSharedPreferences("elinkmemo_secure", Context.MODE_PRIVATE)

    fun save(user: String, pass: String) {
        try {
            val payload = JSONObject().put("user", user).put("pass", pass).toString()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            val blob = ByteArray(1 + iv.size + encrypted.size)
            blob[0] = iv.size.toByte()
            System.arraycopy(iv, 0, blob, 1, iv.size)
            System.arraycopy(encrypted, 0, blob, 1 + iv.size, encrypted.size)
            prefs.edit()
                .putString(KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            // Never fall back to plaintext: drop the blob and let the user re-enter.
            prefs.edit().remove(KEY_BLOB).apply()
        }
    }

    fun load(): Pair<String, String>? {
        val encoded = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            if (blob.size < 2) return null
            val ivSize = blob[0].toInt() and 0xFF
            if (ivSize <= 0 || blob.size <= 1 + ivSize) return null
            val iv = blob.copyOfRange(1, 1 + ivSize)
            val encrypted = blob.copyOfRange(1 + ivSize, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = String(cipher.doFinal(encrypted), Charsets.UTF_8)
            val obj = JSONObject(json)
            obj.optString("user") to obj.optString("pass")
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "elinkmemo_sync_credentials_v1"
        private const val KEY_BLOB = "sync_credentials_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
