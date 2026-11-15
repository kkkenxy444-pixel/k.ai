package com.kai.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM key material never leaves Android Keystore. Ciphertext is excluded from backups. */
class KeyVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "gemini.credential"))
    private val alias = "${context.packageName}.gemini.aes.v1"
    @Volatile private var cached: String? = null
    @Volatile var recoveryRequired: Boolean = false
        private set
    fun current(): String? = cached
    fun load() {
        if (!file.baseFile.exists()) return
        try {
            val bytes = file.readFully()
            require(bytes.size > 29 && bytes[0].toInt() == 1)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cached = cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8)
            recoveryRequired = false
        } catch (_: Exception) { cached = null; recoveryRequired = true }
    }
    @Synchronized fun save(value: String) {
        val cleaned = value.trim()
        require(Regex("[A-Za-z0-9._-]{10,512}").matches(cleaned)) { "Format API key tidak valid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(cleaned.toByteArray())
        val out = file.startWrite()
        try { out.write(encrypted); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
        cached = cleaned; recoveryRequired = false
    }
    @Synchronized fun delete() { file.delete(); cached = null; recoveryRequired = false }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}
