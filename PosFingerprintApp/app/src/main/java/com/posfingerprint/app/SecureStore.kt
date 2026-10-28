package com.posfingerprint.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts secrets (the Odoo API key) with an AES-256-GCM key that lives in the
 * Android Keystore. The key material never leaves the secure hardware, so a copy
 * of the app's files is useless on its own.
 *
 * The alias deliberately does NOT start with "posfp_": that prefix marks fingerprint keys.
 */
object SecureStore {
    private const val ALIAS = "pfstore_v1"
    private const val PREFIX = "enc1:"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        return try {
            gen.init(spec(true))
            gen.generateKey()
        } catch (_: Exception) {
            // No StrongBox chip on this phone: use the regular hardware keystore.
            gen.init(spec(false))
            gen.generateKey()
        }
    }

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + b64(c.iv) + ":" + b64(ct)
    }

    /** Returns null if the value can't be decrypted (tampered, or keystore was reset). */
    fun decrypt(stored: String): String? {
        if (!isEncrypted(stored)) return null
        val parts = stored.removePrefix(PREFIX).split(":")
        if (parts.size != 2) return null
        return try {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(parts[0])))
            String(c.doFinal(unb64(parts[1])), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteKey() {
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
