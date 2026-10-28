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

    /**
     * Creates a P-256 signing key that:
     * - can only be used right after a strong biometric scan (timeout 0 = every signature),
     * - is destroyed if a new fingerprint is enrolled on the phone,
     * - lives in the StrongBox security chip when the phone has one.
     */
    fun createKey(): String {
        val alias = PREFIX + UUID.randomUUID().toString().replace("-", "")
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        try {
            gen.initialize(spec(true))
            gen.generateKeyPair()
        } catch (_: Exception) {
            delete(alias)
            gen.initialize(spec(false))
            gen.generateKeyPair()
        }
        return alias
    }

    /** Deletes every fingerprint key this app created. */
    fun deleteAll() {
        try {
            aliases().forEach { delete(it) }
        } catch (_: Exception) {
        }
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
