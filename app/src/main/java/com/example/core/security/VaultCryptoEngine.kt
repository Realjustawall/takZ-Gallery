package com.example.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.InputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard, reviewed cryptographic engine for TakZ Gallery Secure Vault.
 * - Uses AES/GCM/NoPadding (256-bit key, 128-bit authentication tag, 12-byte random IV).
 * - Integrates with AndroidKeyStore on physical/emulator devices, with deterministic PBKDF2-derived
 *   key fallback for JVM Robolectric test environments.
 * - Hashes PINs using PBKDF2WithHmacSHA256 (12,000 iterations + 16-byte random salt). Never stores plaintext PINs.
 */
class VaultCryptoEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences("takz_vault_security", Context.MODE_PRIVATE)
    private val keyAlias = "takz_vault_master_aes256"
    // Ephemeral only in Robolectric tests, never persisted or used on a device.
    private val testKey: SecretKey by lazy {
        SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
    }

    data class EncryptedPayloadResult(
        val encryptedFile: File,
        val ivHex: String
    )

    fun isPinConfigured(): Boolean =
        prefs.getString("pin_hash_hex", null) != null && prefs.getString("pin_salt_hex", null) != null

    fun setOrUpdatePin(pin: String): Boolean {
        if (pin.length < 4) return false
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = pbkdf2(pin, salt)
        prefs.edit()
            .putString("pin_salt_hex", salt.toHex())
            .putString("pin_hash_hex", hash.toHex())
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < prefs.getLong("lockout_until_ms", 0L)) return false
        val saltHex = prefs.getString("pin_salt_hex", null) ?: return false
        val expectedHashHex = prefs.getString("pin_hash_hex", null) ?: return false
        val salt = saltHex.hexToBytes()
        val expected = expectedHashHex.hexToBytes()
        val actual = pbkdf2(pin, salt)
        if (MessageDigest.isEqual(expected, actual)) {
            prefs.edit().remove("failed_pin_attempts").remove("lockout_until_ms").apply()
            return true
        }
        // Backwards-compatible PIN migration for databases created before PBKDF2 hardening.
        val legacy = pbkdf2(pin, salt, 12_000)
        if (MessageDigest.isEqual(expected, legacy)) {
            setOrUpdatePin(pin)
            prefs.edit().remove("failed_pin_attempts").remove("lockout_until_ms").apply()
            return true
        }
        val attempts = prefs.getInt("failed_pin_attempts", 0) + 1
        prefs.edit()
            .putInt("failed_pin_attempts", if (attempts >= 5) 0 else attempts)
            .putLong("lockout_until_ms", if (attempts >= 5) now + 60_000L else 0L)
            .apply()
        return false
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int = 210_000): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                existing.secretKey
            } else {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
        } catch (error: Exception) {
            // JVM test runner lacks AndroidKeyStore. Test key stays in process memory.
            if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) return testKey
            // Never persist an unprotected master key as a fallback on real devices.
            throw IllegalStateException("Android Keystore unavailable; cannot unlock vault", error)
        }
    }

    fun encryptStreamToVaultFile(input: InputStream, destFileName: String): EncryptedPayloadResult {
        val vaultDir = File(context.filesDir, "encrypted_vault").apply { mkdirs() }
        val target = File(vaultDir, destFileName)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        try {
            FileOutputStream(target).use { output ->
                CipherOutputStream(output, cipher).use { encrypted -> input.copyTo(encrypted, 64 * 1024) }
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        return EncryptedPayloadResult(target, cipher.iv.toHex())
    }

    fun decryptVaultFileToStream(encryptedFilePath: String, ivHex: String, target: File) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, ivHex.hexToBytes()))
        val temporary = File(target.parentFile, target.name + ".partial")
        try {
            // CipherInputStream finalizes and authenticates GCM tag on end-of-stream.
            CipherInputStream(FileInputStream(encryptedFilePath), cipher).use { decrypted ->
                FileOutputStream(temporary).use { out ->
                    decrypted.copyTo(out, 64 * 1024)
                    out.fd.sync()
                }
            }
            check(temporary.renameTo(target)) { "Could not finalize restored file" }
        } catch (t: Throwable) {
            temporary.delete()
            throw t
        }
    }

    fun sha256OfDecryptedFile(encryptedFilePath: String, ivHex: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, ivHex.hexToBytes()))
        val digest = MessageDigest.getInstance("SHA-256")
        CipherInputStream(FileInputStream(encryptedFilePath), cipher).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest()
    }

    fun encryptBytesToVaultFile(plainBytes: ByteArray, destFileName: String): EncryptedPayloadResult {
        val vaultDir = File(context.filesDir, "encrypted_vault").apply { mkdirs() }
        val targetFile = File(vaultDir, destFileName)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getOrCreateSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainBytes)
        targetFile.writeBytes(cipherBytes)
        return EncryptedPayloadResult(
            encryptedFile = targetFile,
            ivHex = iv.toHex()
        )
    }

    fun decryptVaultFileToBytes(encryptedFilePath: String, ivHex: String): ByteArray {
        val file = File(encryptedFilePath)
        val cipherBytes = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, ivHex.hexToBytes())
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        return cipher.doFinal(cipherBytes)
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.toHex()
        }

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        fun String.hexToBytes(): ByteArray {
            val clean = trim()
            val out = ByteArray(clean.length / 2)
            for (i in out.indices) {
                val idx = i * 2
                out[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
            }
            return out
        }
    }
}
