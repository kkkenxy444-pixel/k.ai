package com.kai.core.network

import com.kai.core.model.KaiJson
import kotlinx.serialization.json.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

enum class FailureKind { MISSING_KEY, INVALID_KEY, AUTH, MODEL, RESTRICTED, RATE_LIMIT, DAILY_QUOTA, QUOTA, OFFLINE, TIMEOUT, SERVICE, CONTENT, INVALID_REQUEST, PROTOCOL }

class ApiFailure(val kind: FailureKind, override val message: String, val retryAfterMs: Long? = null, val retryable: Boolean = false) : Exception(message)

object Failures {
    fun fromHttp(code: Int, body: String, retryAfter: String? = null): ApiFailure {
        val error = runCatching { KaiJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
        val message = error?.get("message")?.jsonPrimitive?.content.orEmpty().lowercase()
        val details = error?.get("details")?.toString().orEmpty().lowercase()
        val text = "$message $details"
        val retrySeconds = Regex("\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\"", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.toDoubleOrNull()
        val headerDelay = retryAfter?.toDoubleOrNull()?.times(1000)?.toLong()
            ?: retryAfter?.let { runCatching { max(0, ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()) }.getOrNull() }
        val delay = max(headerDelay ?: 0L, retrySeconds?.times(1000)?.toLong() ?: 0L).takeIf { it > 0 }
        return when {
            text.contains("api_key_invalid") || text.contains("api key not valid") || text.contains("api_key_expired") || text.contains("key was reported as leaked") || text.contains("revoked") -> ApiFailure(FailureKind.INVALID_KEY, "API key tidak valid, dicabut, atau diblokir. Periksa key di Google AI Studio.")
            code == 401 -> ApiFailure(FailureKind.AUTH, "Autentikasi gagal. Periksa atau ganti API key.")
            code == 403 -> ApiFailure(FailureKind.RESTRICTED, "Akses model atau layanan dibatasi untuk key/proyek ini. Periksa izin, wilayah, dan akses di AI Studio.")
            code == 404 -> ApiFailure(FailureKind.MODEL, "Model atau endpoint tidak tersedia. Segarkan katalog dan pilih model lain; riwayat tetap tersimpan.")
            code == 429 && (text.contains("perday") || text.contains("per_day") || text.contains("daily")) -> ApiFailure(FailureKind.DAILY_QUOTA, "Kuota harian habis. Tunggu pemulihan kuota proyek; mengganti key pada proyek yang sama tidak mereset kuota.", delay)
            code == 429 && (text.contains("limit: 0") || text.contains("limit:0") || Regex("\"quota_?value\"\\s*:\\s*\"?0").containsMatchIn(text)) -> ApiFailure(FailureKind.QUOTA, "Kuota model ini tidak tersedia untuk proyekmu. Periksa kuota dan akses model di AI Studio.", delay)
            code == 429 -> ApiFailure(FailureKind.RATE_LIMIT, "Batas permintaan atau kuota tercapai. Coba lagi setelah jeda yang ditentukan server. Jenis kuota tidak selalu dilaporkan.", delay, true)
            code in 500..599 -> ApiFailure(FailureKind.SERVICE, "Layanan Gemini sementara tidak tersedia. Coba lagi nanti.", delay, code == 503)
            code == 400 && (text.contains("not supported") || text.contains("unsupported")) -> ApiFailure(FailureKind.MODEL, "Model tidak mendukung konfigurasi ini. Pilih model atau protokol TTS yang sesuai.")
            else -> ApiFailure(FailureKind.INVALID_REQUEST, "Permintaan ditolak (HTTP $code). Periksa model, panjang pesan, dan konfigurasi suara.")
        }
    }

    fun readable(t: Throwable): String = when (t) {
        is ApiFailure -> t.message + (t.retryAfterMs?.let { " Jeda server: ${(it + 999) / 1000} detik." } ?: "")
        is UnknownHostException -> "Tidak dapat terhubung ke internet. Periksa koneksimu."
        is SocketTimeoutException -> "Permintaan melewati batas waktu. Coba lagi saat koneksi stabil."
        is java.io.IOException -> "Koneksi terputus atau penyimpanan tidak dapat diakses. Periksa internet dan ruang perangkat."
        is IllegalArgumentException -> t.message ?: "Konfigurasi belum lengkap."
        else -> "Operasi tidak berhasil. Data tersimpan; coba lagi."
    }
}
