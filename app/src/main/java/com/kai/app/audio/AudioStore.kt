package com.kai.app.audio

import android.content.Context
import com.kai.app.data.KaiDao
import com.kai.core.audio.AudioEncoding
import com.kai.core.model.*
import com.kai.core.network.AudioPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioStore(context: Context, private val dao: KaiDao) {
    private val storage = context.getSystemService(android.os.storage.StorageManager::class.java)
    private val root = File(context.noBackupFilesDir, "audio").apply { mkdirs() }
    private val previews = File(context.cacheDir, "voice-previews").apply { mkdirs() }
    fun file(name: String): File {
        require(Regex("[a-f0-9-]+\\.wav").matches(name)) { "Nama berkas audio tidak valid." }
        return File(root, name)
    }
    suspend fun save(payload: AudioPayload): String = withContext(Dispatchers.IO) {
        val name = "${newId()}.wav"
        writeAtomic(file(name), AudioEncoding.wav(payload))
        name
    }
    suspend fun preview(payload: AudioPayload): File = withContext(Dispatchers.IO) {
        val out = File(previews, "${newId()}.wav")
        writeAtomic(out, AudioEncoding.wav(payload))
        out
    }
    suspend fun delete(names: List<String>) = withContext(Dispatchers.IO) { names.forEach { runCatching { file(it).delete() } }; Unit }
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        val references = dao.audioReferences().flatMap { KaiJson.decodeFromString<List<AudioClip>>(it) }.map { it.fileName }.toSet()
        // Grace period protects in-flight generation before metadata commits.
        val cutoff = System.currentTimeMillis() - 10 * 60 * 1000
        root.listFiles()?.filter { it.name !in references && it.lastModified() < cutoff }?.forEach { it.delete() }
        previews.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000 }?.forEach { it.delete() }
        Unit
    }
    suspend fun clearAll() = withContext(Dispatchers.IO) { dao.clearAudio(); root.listFiles()?.forEach { it.delete() }; previews.listFiles()?.forEach { it.delete() }; Unit }
    suspend fun size(): Long = withContext(Dispatchers.IO) { root.listFiles()?.sumOf { it.length() } ?: 0 }
    private fun writeAtomic(target: File, bytes: ByteArray) {
        val volume = storage.getUuidForPath(requireNotNull(target.parentFile))
        require(storage.getAllocatableBytes(volume) > bytes.size.toLong() + 2_000_000) { "Ruang perangkat tidak cukup untuk menyimpan audio." }
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.outputStream().use { storage.allocateBytes(it.fd, bytes.size.toLong()); it.write(bytes); it.fd.sync() }
            check(tmp.renameTo(target)) { "Audio tidak dapat disimpan." }
        } finally { tmp.delete() }
    }
}
