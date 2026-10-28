package com.posfingerprint.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/**
 * Fingerprint keys live in the Android Keystore (hardware backed where available).
 * Each key can only sign after a successful biometric prompt, every single time.
 */
object KeyStoreHelper {
    private const val PREFIX = "posfp_"

    private fun ks(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun aliases(): List<String> = ks().aliases().toList().filter { it.startsWith(PREFIX) }

    fun createKey(): String {
        val alias = PREFIX + UUID.randomUUID().toString().replace("-", "")
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            .apply { initialize(spec) }
            .generateKeyPair()
        return alias
    }

    fun publicKeyB64(alias: String): String =
        b64url(ks().getCertificate(alias).publicKey.encoded) // X.509 SubjectPublicKeyInfo (DER)

    fun signatureFor(alias: String): Signature {
        val key = ks().getKey(alias, null) as PrivateKey
        return Signature.getInstance("SHA256withECDSA").apply { initSign(key) }
    }

    fun delete(alias: String) {
        try {
            ks().deleteEntry(alias)
        } catch (_: Exception) {
        }
    }

    fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
