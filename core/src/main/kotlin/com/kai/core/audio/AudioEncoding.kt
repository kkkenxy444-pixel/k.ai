package com.kai.core.audio

import com.kai.core.network.AudioPayload
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioEncoding {
    fun wav(payload: AudioPayload): ByteArray {
        if (payload.bytes.size >= 12 && payload.bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF") return payload.bytes
        require(payload.mimeType.startsWith("audio/L16", true) || payload.mimeType.startsWith("audio/pcm", true)) { "Format audio belum didukung: ${payload.mimeType.take(80)}" }
        // Gemini generateContent returns little-endian signed 16-bit PCM, generally 24 kHz mono.
        val rate = Regex("rate=(\\d+)").find(payload.mimeType)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
        require(rate in 8000..96000 && payload.bytes.size % 2 == 0) { "Parameter PCM tidak valid." }
        val channels = Regex("channels=(\\d+)").find(payload.mimeType)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        require(channels in 1..2) { "Jumlah kanal audio tidak didukung." }
        val data = payload.bytes
        val b = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + data.size); b.put("WAVEfmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(channels.toShort()); b.putInt(rate)
        b.putInt(rate * channels * 2); b.putShort((channels * 2).toShort()); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(data.size); b.put(data)
        return b.array()
    }

    fun splitText(text: String, limit: Int = 4500): List<String> {
        require(limit > 0)
        val parts = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > limit) {
            val boundary = rest.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ' '), limit).takeIf { it >= limit / 2 }?.plus(1) ?: limit
            parts += rest.take(boundary).trim()
            rest = rest.drop(boundary).trimStart()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts
    }
}
