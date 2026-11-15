package com.kai.core.network

import com.kai.core.ai.PromptCompiler
import com.kai.core.ai.SpeechTurn
import com.kai.core.ai.TtsCapabilities
import com.kai.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
internal fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
internal fun JsonObject.arr(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
internal fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
internal fun s(value: String) = JsonPrimitive(value)
internal fun textPart(text: String) = obj("text" to s(text))

data class AudioPayload(val bytes: ByteArray, val mimeType: String)
data class DesignedVoice(val voice: CatalogVoice, val sample: AudioPayload?)

/** Key comes from the Keystore repository at request time. Never placed in URLs/logs. */
class GeminiApi(
    private val keyProvider: () -> String?,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build(),
) {
    private fun request(path: String, body: JsonObject? = null, key: String? = null): Request {
        val secret = key ?: keyProvider()
        if (secret.isNullOrBlank()) throw ApiFailure(FailureKind.MISSING_KEY, "Tambahkan API key pribadimu di Pengaturan Gemini.")
        if (!Regex("[A-Za-z0-9._-]{10,512}").matches(secret)) {
            throw ApiFailure(FailureKind.INVALID_KEY, "Format API key tidak valid. Salin ulang key dari Google AI Studio.")
        }
        return Request.Builder().url(baseUrl + path)
            .header("x-goog-api-key", secret).header("Content-Type", "application/json")
            .apply { if (body != null) post(body.toString().toRequestBody(JSON_TYPE)) }.build()
    }

    private suspend fun execute(request: Request): JsonObject = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val source = response.body ?: throw ApiFailure(FailureKind.PROTOCOL, "Respons server kosong.")
                        if (source.contentLength() > 32L * 1024 * 1024) throw ApiFailure(FailureKind.PROTOCOL, "Respons audio terlalu besar. Gunakan teks lebih pendek.")
                        val raw = source.source().readUtf8Limited(32L * 1024 * 1024)
                        if (!response.isSuccessful) throw Failures.fromHttp(response.code, raw, response.header("Retry-After"))
                        val parsed = KaiJson.parseToJsonElement(raw).jsonObject
                        if (continuation.isActive) continuation.resume(parsed)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            }
        })
    }

    private suspend fun rejectedRetry(block: suspend () -> JsonObject): JsonObject {
        repeat(3) { attempt ->
            try { return block() } catch (e: ApiFailure) {
                if (!e.retryable || attempt == 2 || (e.retryAfterMs ?: 0) > 30_000) throw e
                delay(maxOf(e.retryAfterMs ?: 0, 1000L shl attempt))
            }
        }
        error("Unreachable")
    }

    suspend fun listModels(key: String? = null): List<GeminiModel> {
        val models = mutableListOf<GeminiModel>()
        var token = ""
        val seen = mutableSetOf<String>()
        do {
            val url = (baseUrl + "models").toHttpUrl().newBuilder().addQueryParameter("pageSize", "1000")
                .apply { if (token.isNotEmpty()) addQueryParameter("pageToken", token) }.build()
            val page = rejectedRetry { execute(request(url.toString().removePrefix(baseUrl), key = key)) }
            models += page.arr("models").map { KaiJson.decodeFromJsonElement<GeminiModel>(it) }
            token = page.str("nextPageToken")
            if (token.isNotEmpty() && !seen.add(token)) throw ApiFailure(FailureKind.PROTOCOL, "Katalog mengulang halaman. Coba segarkan kembali.")
        } while (token.isNotEmpty())
        return models.distinctBy { it.name }.sortedBy { it.displayName.ifBlank { it.name } }
    }

    private fun modelPath(model: String): String {
        val id = model.removePrefix("models/")
        require(Regex("[a-zA-Z0-9._-]+").matches(id)) { "Pilih model dari katalog terlebih dahulu." }
        return "models/$id"
    }

    fun chatBody(system: String, history: List<ChatMessage>): JsonObject {
        val merged = mutableListOf<Pair<String, String>>()
        history.filter { it.text.isNotBlank() }.forEach { m ->
            val role = if (m.role == MessageRole.USER) "user" else "model"
            if (merged.lastOrNull()?.first == role) {
                val old = merged.removeAt(merged.lastIndex)
                merged += role to "${old.second}\n\n${m.text}"
            } else merged += role to m.text
        }
        // API conversation content must start with a user turn after a summarized prefix.
        if (merged.firstOrNull()?.first == "model") merged.add(0, "user" to "Lanjutkan percakapan berikut berdasarkan memori.")
        return obj(
            "systemInstruction" to obj("parts" to JsonArray(listOf(textPart(system)))),
            "contents" to JsonArray(merged.map { (role, text) -> obj("role" to s(role), "parts" to JsonArray(listOf(textPart(text)))) }),
        )
    }

    suspend fun generate(model: String, system: String, messages: List<ChatMessage>): String {
        val response = rejectedRetry { execute(request("${modelPath(model)}:generateContent", chatBody(system, messages))) }
        val result = responseText(response)
        if (result.isBlank()) throw ApiFailure(FailureKind.CONTENT, "Model tidak menghasilkan teks. Periksa dukungan model atau ubah permintaan.")
        return result
    }

    /** Retries only rejected requests before any text. Broken streams preserve partial output. */
    fun stream(model: String, system: String, messages: List<ChatMessage>): Flow<String> = flow {
        var emitted = false
        emitAll(streamOnce(model, system, messages).onEach { emitted = true }.retryWhen { cause, attempt ->
            if (!emitted && cause is ApiFailure && cause.retryable && attempt < 2 && (cause.retryAfterMs ?: 0) <= 30_000) {
                delay(maxOf(cause.retryAfterMs ?: 0, 1000L shl attempt.toInt())); true
            } else false
        })
    }

    private fun streamOnce(model: String, system: String, messages: List<ChatMessage>): Flow<String> = callbackFlow {
        val req = request("${modelPath(model)}:streamGenerateContent?alt=sse", chatBody(system, messages))
        val call = client.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { close(e) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = response.body ?: throw IOException("Empty response")
                        if (!response.isSuccessful) throw Failures.fromHttp(response.code, body.source().readUtf8Limited(128_000), response.header("Retry-After"))
                        val source = body.source()
                        val event = StringBuilder()
                        var emitted = 0
                        fun dispatch() {
                            val data = event.toString().trim(); event.clear()
                            if (data.isBlank() || data == "[DONE]") return
                            val delta = responseText(KaiJson.parseToJsonElement(data).jsonObject)
                            emitted += delta.length
                            if (emitted > 200_000) throw ApiFailure(FailureKind.PROTOCOL, "Respons terlalu panjang; keluaran parsial disimpan.")
                            if (delta.isNotEmpty()) trySend(delta)
                        }
                        while (!source.exhausted() && !call.isCanceled()) {
                            val line = source.readUtf8LineStrict(1_000_000)
                            when {
                                line.isEmpty() -> dispatch()
                                line.startsWith("data:") -> { if (event.isNotEmpty()) event.append('\n'); event.append(line.removePrefix("data:").trimStart()) }
                            }
                        }
                        if (event.isNotEmpty()) dispatch()
                        close()
                    } catch (e: Exception) { close(e) }
                }
            }
        })
        awaitClose { call.cancel() }
    }.buffer(Channel.UNLIMITED)

    suspend fun tts(turn: SpeechTurn): AudioPayload {
        require(turn.text.isNotBlank()) { "Tidak ada teks yang dapat dibacakan." }
        require(turn.text.length <= 5000) { "Teks suara terlalu panjang. Pecah menjadi bagian di bawah 5.000 karakter." }
        val profile = turn.profile
        require(profile.model.isNotBlank()) { "Pilih model TTS pada profil suara terlebih dahulu." }
        require(profile.voiceId.isNotBlank()) { "Pilih identitas suara terlebih dahulu." }
        val response = if (profile.transport == TtsTransport.INTERACTIONS) {
            rejectedRetry { execute(request("interactions", interactionTtsBody(listOf(turn)))) }
        } else {
            require(profile.voiceId in StudioTraits.builtInVoices) { "Suara katalog/custom membutuhkan protokol Interactions. Ubah protokol di Voice Studio." }
            val body = obj(
                "contents" to JsonArray(listOf(obj("parts" to JsonArray(listOf(textPart("${PromptCompiler.voice(profile)}\n\nTRANSCRIPT:\n${turn.text}")))))),
                "generationConfig" to obj("responseModalities" to JsonArray(listOf(s("AUDIO"))),
                    "speechConfig" to obj("voiceConfig" to obj("prebuiltVoiceConfig" to obj("voiceName" to s(profile.voiceId)))))
            )
            rejectedRetry { execute(request("${modelPath(profile.model)}:generateContent", body)) }
        }
        return audio(response)
    }

    suspend fun ttsMultiSpeaker(turns: List<SpeechTurn>): AudioPayload {
        require(TtsCapabilities.canCombine(turns)) { "Konfigurasi dua pembicara belum didukung. Gunakan VN per karakter." }
        return audio(rejectedRetry { execute(request("interactions", interactionTtsBody(turns))) })
    }

    fun interactionTtsBody(turns: List<SpeechTurn>): JsonObject {
        require(turns.isNotEmpty())
        val distinct = turns.distinctBy { it.speaker }
        require(distinct.size <= 2) { "TTS multi-speaker mendukung paling banyak dua pembicara per permintaan." }
        val config = if (distinct.size == 1) JsonArray(listOf(obj("voice" to s(distinct.first().profile.voiceId)))) else obj(
            "speakers" to JsonArray(distinct.map { obj("speaker" to s(it.speaker), "voice" to s(it.profile.voiceId)) }))
        return obj(
            "model" to s(turns.first().profile.model.removePrefix("models/")),
            "store" to JsonPrimitive(false),
            "input" to JsonArray(listOf(obj("type" to s("user_input"), "content" to JsonArray(turns.map {
                obj("type" to s("text"), "text" to s(it.text), "annotations" to JsonArray(listOf(
                    obj("type" to s("speech_metadata"), "speaker" to s(it.speaker), "style" to s(PromptCompiler.voice(it.profile)))
                )))
            })))),
            "response_format" to obj("type" to s("audio")),
            "generation_config" to obj("speech_config" to config),
        )
    }

    suspend fun listVoices(): List<CatalogVoice> {
        val result = mutableListOf<CatalogVoice>()
        var token = ""
        val seen = mutableSetOf<String>()
        do {
            val url = (baseUrl + "voices").toHttpUrl().newBuilder().addQueryParameter("page_size", "100")
                .apply { if (token.isNotBlank()) addQueryParameter("page_token", token) }.build()
            val page = rejectedRetry { execute(request(url.toString().removePrefix(baseUrl))) }
            result += page.arr("voices").mapNotNull { value ->
                val v = value.jsonObject
                v.str("id").takeIf { it.isNotBlank() }?.let { CatalogVoice(it, v.str("display_name").ifBlank { it }, v.str("description"), v.str("type")) }
            }
            token = page.str("next_page_token")
            if (token.isNotEmpty() && !seen.add(token)) throw ApiFailure(FailureKind.PROTOCOL, "Halaman katalog suara berulang.")
        } while (token.isNotEmpty())
        return result.distinctBy { it.id }
    }

    suspend fun designVoice(model: String, name: String, description: String): DesignedVoice {
        require(model.isNotBlank() && name.isNotBlank() && description.isNotBlank()) { "Lengkapi model, nama, dan deskripsi suara." }
        // Creation is never automatically retried: it creates a persistent project resource.
        val data = execute(request("voices", obj("store" to JsonPrimitive(true), "voice" to obj(
            "model" to s(model.removePrefix("models/")), "type" to s("prompted"),
            "display_name" to s(name), "language_code" to s("id-ID"), "prompted" to obj("input" to s(description))
        ))))
        val id = data.str("id")
        if (!id.startsWith("voice_")) throw ApiFailure(FailureKind.PROTOCOL, "Server tidak mengembalikan ID suara. Periksa hasil di AI Studio sebelum mencoba lagi.")
        val sample = (data["sample_audio"] as? JsonObject)?.let { decodeAudio(it.str("data"), it.str("mime_type")) }
        return DesignedVoice(CatalogVoice(id, name, description, "prompted"), sample)
    }

    companion object {
        fun responseText(response: JsonObject): String {
            (response["error"] as? JsonObject)?.let { throw Failures.fromHttp(it["code"]?.jsonPrimitive?.intOrNull ?: 400, response.toString()) }
            val feedback = response["promptFeedback"] as? JsonObject
            if (!feedback?.str("blockReason").isNullOrBlank()) throw ApiFailure(FailureKind.CONTENT, "Permintaan dibatasi oleh kebijakan penyedia. Ubah isi permintaan.")
            val candidate = response.arr("candidates").firstOrNull()?.jsonObject ?: return ""
            val reason = candidate.str("finishReason")
            if (reason in listOf("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "RECITATION")) throw ApiFailure(FailureKind.CONTENT, "Respons dibatasi oleh kebijakan penyedia ($reason).")
            return (candidate["content"] as? JsonObject)?.arr("parts")?.mapNotNull {
                val part = it.jsonObject
                if (part["thought"]?.jsonPrimitive?.booleanOrNull == true) null else part["text"]?.jsonPrimitive?.contentOrNull
            }?.joinToString("").orEmpty()
        }

        fun audio(response: JsonObject): AudioPayload {
            val parts = response.arr("candidates").firstOrNull()?.jsonObject?.get("content")?.jsonObject?.arr("parts").orEmpty()
            parts.forEach { part ->
                (part.jsonObject["inlineData"] as? JsonObject)?.let { return decodeAudio(it.str("data"), it.str("mimeType")) }
            }
            response.arr("steps").filter { it.jsonObject.str("type") == "model_output" }.asReversed().forEach { step ->
                step.jsonObject.arr("content").asReversed().forEach { value ->
                    val v = value.jsonObject
                    if (v.str("type") == "audio") return decodeAudio(v.str("data"), v.str("mime_type").ifBlank { "audio/wav" })
                }
            }
            responseText(response) // Surface provider safety blocks when present.
            throw ApiFailure(FailureKind.MODEL, "Model tidak mengembalikan audio. Periksa model TTS, protokol, dan akses proyek.")
        }

        private fun decodeAudio(data: String, mime: String): AudioPayload {
            if (data.isBlank()) throw ApiFailure(FailureKind.PROTOCOL, "Audio dari server kosong.")
            val bytes = try { Base64.getDecoder().decode(data) } catch (_: IllegalArgumentException) { throw ApiFailure(FailureKind.PROTOCOL, "Format audio server tidak valid.") }
            return AudioPayload(bytes, mime)
        }
    }
}

private fun okio.BufferedSource.readUtf8Limited(limit: Long): String {
    request(limit + 1)
    if (buffer.size > limit) throw ApiFailure(FailureKind.PROTOCOL, "Respons server terlalu besar.")
    return readUtf8()
}
