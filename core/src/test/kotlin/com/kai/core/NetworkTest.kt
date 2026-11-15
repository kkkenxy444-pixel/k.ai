package com.kai.core

import com.kai.core.ai.SpeechTurn
import com.kai.core.model.*
import com.kai.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class NetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GeminiApi
    @Before fun setup() { server = MockWebServer(); server.start(); api = GeminiApi({ "fixture-key-not-a-real-secret" }, server.url("/v1beta/").toString()) }
    @After fun teardown() { server.shutdown() }
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()
    private fun history() = listOf(ChatMessage(sessionId = "test", ordinal = 0, role = MessageRole.USER, text = "Halo"))

    @Test fun modelDiscoveryFollowsPaginationAndUsesHeaderAuthentication() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("models-page1.json")))
        server.enqueue(MockResponse().setBody(fixture("models-page2.json")))
        val result = api.listModels()
        assertEquals(3, result.size)
        val first = server.takeRequest(); val second = server.takeRequest()
        assertEquals("fixture-key-not-a-real-secret", first.getHeader("x-goog-api-key"))
        assertFalse(first.path!!.contains("key=")); assertTrue(second.path!!.contains("pageToken=page-two"))
    }
    @Test fun newAuthorizationKeyFormatIsAcceptedAndSentViaHeader() = runBlocking {
        val key = "AQ.fixture-auth-key-that-is-not-real"
        val authApi = GeminiApi({ key }, server.url("/v1beta/").toString())
        server.enqueue(MockResponse().setBody(fixture("models-page2.json")))
        val result = authApi.listModels()
        assertTrue(result.isNotEmpty())
        val sent = server.takeRequest()
        assertEquals(key, sent.getHeader("x-goog-api-key"))
        assertFalse(sent.path!!.contains("key="))
    }
    @Test fun oldStandardKeyFormatRemainsSupported() = runBlocking {
        val key = "AIzaFixtureNotARealApiKey123456789"
        val legacyApi = GeminiApi({ key }, server.url("/v1beta/").toString())
        server.enqueue(MockResponse().setBody(fixture("models-page2.json")))
        assertTrue(legacyApi.listModels().isNotEmpty())
        assertEquals(key, server.takeRequest().getHeader("x-goog-api-key"))
    }
    @Test fun streamingReturnsRealProtocolDeltas() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(fixture("stream.sse")))
        assertEquals("Halo, aku mendengarkan.", api.stream("models/example-flash-lite", "system", history()).toList().joinToString(""))
        assertTrue(server.takeRequest().path!!.contains(":streamGenerateContent?alt=sse"))
    }
    @Test fun safetyBlocksAreNotTreatedAsEmptySuccess() {
        val response = KaiJson.parseToJsonElement("""{"promptFeedback":{"blockReason":"SAFETY"}}""").jsonObject
        val error = runCatching { GeminiApi.responseText(response) }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.CONTENT, error.kind)
    }
    @Test fun thinkingPartsAreNotShownAsUserFacingText() {
        val response = KaiJson.parseToJsonElement("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"private reasoning"},{"text":"visible reply"}]}}]}""").jsonObject
        assertEquals("visible reply", GeminiApi.responseText(response))
    }
    @Test fun dailyQuotaIsDistinctAndNeverRetried() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody(fixture("quota-daily.json")))
        val error = runCatching { api.listModels() }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.DAILY_QUOTA, error.kind); assertEquals(42000L, error.retryAfterMs)
        assertEquals(1, server.requestCount); assertFalse(error.retryable)
    }
    @Test fun invalidKeyAndRestrictedModelAreDifferent() {
        val key = Failures.fromHttp(400, """{"error":{"message":"API key not valid","details":[{"reason":"API_KEY_INVALID"}]}}""")
        assertEquals(FailureKind.INVALID_KEY, key.kind)
        assertEquals(FailureKind.RESTRICTED, Failures.fromHttp(403, "{}").kind)
        assertEquals(FailureKind.MODEL, Failures.fromHttp(404, "{}").kind)
        assertEquals(FailureKind.AUTH, Failures.fromHttp(401, "{}").kind)
    }
    @Test fun longRetryAfterIsExposedWithoutBlockingOrRetrying() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "90").setBody("{}"))
        val error = runCatching { api.listModels() }.exceptionOrNull() as ApiFailure
        assertEquals(90000L, error.retryAfterMs); assertEquals(1, server.requestCount)
    }
    @Test fun transientServiceRejectionRetriesBoundedly() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503).setBody("{}")) }
        val error = runCatching { api.listModels() }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.SERVICE, error.kind); assertEquals(3, server.requestCount)
    }
    @Test fun legacyTtsRequestContainsActualVoiceAndCompiledStyle() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;rate=24000","data":"AAAAAA=="}}]}}]}"""))
        val audio = api.tts(SpeechTurn("Halo", "Anya", VoiceProfile(model = "models/example-tts", voiceId = "Kore").with("soft", 90)))
        assertEquals(4, audio.bytes.size)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("Kore")); assertTrue(body.contains("soft, gentle")); assertTrue(body.contains("Halo")); assertFalse(body.contains("\"pitch\":"))
    }
    @Test fun interactionsAudioResponseIsDecodedAndNotInvented() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"steps":[{"type":"model_output","content":[{"type":"audio","mime_type":"audio/wav","data":"UklGRg=="}]}]}"""))
        val audio = api.tts(SpeechTurn("Halo", "Anya", VoiceProfile(model = "models/example-tts", voiceId = "voice_fixture", transport = TtsTransport.INTERACTIONS)))
        assertEquals("RIFF", audio.bytes.toString(Charsets.US_ASCII))
        val request = server.takeRequest(); assertEquals("/v1beta/interactions", request.path)
        val body = request.body.readUtf8(); assertTrue(body.contains("speech_metadata")); assertTrue(body.contains("\"store\":false")); assertTrue(body.contains("voice_fixture"))
    }
    @Test fun unsupportedCustomVoiceIsNotSentToLegacyEndpoint() = runBlocking {
        val error = runCatching { api.tts(SpeechTurn("Halo", "Anya", VoiceProfile(model = "models/example-tts", voiceId = "voice_fixture"))) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException); assertEquals(0, server.requestCount)
    }
    @Test fun twoSpeakerPayloadPreservesExplicitVoiceMappings() {
        val body = api.interactionTtsBody(listOf(
            SpeechTurn("Halo", "Anya", VoiceProfile(model = "models/example-tts", voiceId = "Kore")),
            SpeechTurn("Selamat datang", "Reina", VoiceProfile(model = "models/example-tts", voiceId = "Puck")),
        ))
        val speakers = body["generation_config"]!!.jsonObject["speech_config"]!!.jsonObject["speakers"]!!.jsonArray
        assertEquals("Kore", speakers[0].jsonObject["voice"]!!.jsonPrimitive.content)
        assertEquals("Reina", speakers[1].jsonObject["speaker"]!!.jsonPrimitive.content)
        assertEquals("Puck", speakers[1].jsonObject["voice"]!!.jsonPrimitive.content)
    }
    @Test fun missingKeyFailsBeforeAnyNetworkRequest() = runBlocking {
        val empty = GeminiApi({ null }, server.url("/v1beta/").toString())
        assertEquals(FailureKind.MISSING_KEY, (runCatching { empty.listModels() }.exceptionOrNull() as ApiFailure).kind)
        assertEquals(0, server.requestCount)
    }
    @Test fun rawProviderMessageNeverLeaksKeyToDisplay() {
        val error = Failures.fromHttp(400, """{"error":{"message":"the key very-secret-value was invalid"}}""")
        assertFalse(Failures.readable(error).contains("very-secret-value"))
    }
    @Test fun invalidCredentialCharactersNeverReachHeadersOrEchoInput() = runBlocking {
        val secret = "private-invalid-key\nsecond-line"
        val invalid = GeminiApi({ secret }, server.url("/v1beta/").toString())
        val error = runCatching { invalid.listModels() }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.INVALID_KEY, error.kind)
        assertFalse(Failures.readable(error).contains("private-invalid-key"))
        assertEquals(0, server.requestCount)
    }
    @Test fun compatibleMultiSpeakerAudioUsesOneRealRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"steps":[{"type":"model_output","content":[{"type":"audio","mime_type":"audio/wav","data":"UklGRg=="}]}]}"""))
        val voice = VoiceProfile(model = "models/gemini-3.8-flash-tts", voiceId = "Kore", transport = TtsTransport.INTERACTIONS)
        val result = api.ttsMultiSpeaker(listOf(SpeechTurn("Halo", "Anya", voice), SpeechTurn("Hai", "Reina", voice.copy(voiceId = "Puck"))))
        assertEquals("RIFF", result.bytes.toString(Charsets.US_ASCII))
        assertEquals(1, server.requestCount)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"speaker\":\"Anya\",\"voice\":\"Kore\""))
        assertTrue(body.contains("\"speaker\":\"Reina\",\"voice\":\"Puck\""))
    }
}
