package com.kai.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

val KaiJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
fun newId(): String = UUID.randomUUID().toString()

@Serializable enum class ChatMode { NORMAL, ROLEPLAY }
@Serializable enum class MessageRole { USER, MODEL }
@Serializable enum class MessageStatus { COMPLETE, GENERATING, FAILED, INTERRUPTED }
@Serializable enum class VoiceScope { ALL, DIALOGUE, CHARACTER }
@Serializable enum class TtsTransport { GENERATE_CONTENT, INTERACTIONS }

@Serializable
data class Personality(val values: Map<String, Int> = emptyMap()) {
    fun value(key: String): Int = (values[key] ?: 50).coerceIn(0, 100)
    fun with(key: String, value: Int) = copy(values = values + (key to value.coerceIn(0, 100)))
}

@Serializable
data class VoiceProfile(
    val id: String = newId(),
    val name: String = "Kawaii • Leda",
    val voiceId: String = "Leda",
    val model: String = "",
    val transport: TtsTransport = TtsTransport.GENERATE_CONTENT,
    val voiceStyle: String = "kawaii",
    val pitch: Int = 65,
    val speed: Int = 50,
    val values: Map<String, Int> = emptyMap(),
    val description: String = "Suara perempuan bergaya anime yang imut, manja, ekspresif, lembut, dan natural.",
) {
    fun value(key: String): Int = (values[key] ?: 50).coerceIn(0, 100)
    fun with(key: String, value: Int) = copy(values = values + (key to value.coerceIn(0, 100)))
    /** Matches the reference app's 0.8 + (speed * 0.004) playback curve. */
    fun playbackSpeed(): Float = 0.8f + speed.coerceIn(0, 100) * 0.004f
    fun independentCopy() = copy(id = newId())
}

@Serializable
data class CharacterSpec(
    val id: String = newId(),
    val name: String = "",
    val description: String = "",
    val backstory: String = "",
    val rules: String = "",
    val personality: Personality = Personality(),
    val voice: VoiceProfile = VoiceProfile(),
) {
    fun independentCopy() = copy(id = newId(), voice = voice.independentCopy())
}

@Serializable
data class Scenario(
    val title: String = "",
    val world: String = "",
    val genre: String = "Fantasi",
    val opening: String = "",
    val userRole: String = "",
    val relationships: String = "",
    val rules: String = "",
    val narrativeStyle: String = "Narasi ringkas dan dialog alami",
    val dialogueFormat: String = "Nama: dialog",
    val actors: List<CharacterSpec> = emptyList(),
    val voiceScope: VoiceScope = VoiceScope.DIALOGUE,
    val selectedSpeakerId: String = "",
) {
    fun independentCopy(): Scenario {
        val copies = actors.map { it.independentCopy() }
        val selected = actors.indexOfFirst { it.id == selectedSpeakerId }
        return copy(actors = copies, selectedSpeakerId = copies.getOrNull(selected)?.id.orEmpty())
    }
}

@Serializable
data class SessionSpec(
    val id: String = newId(),
    val title: String,
    val mode: ChatMode = ChatMode.NORMAL,
    val character: CharacterSpec,
    val model: String = "",
    val scenario: Scenario = Scenario(),
    val autoVoice: Boolean = false,
    val autoPlay: Boolean = false,
)

@Serializable
data class AudioClip(
    val fileName: String,
    val speaker: String = "",
    val voiceId: String,
    val model: String,
    val profileSnapshot: VoiceProfile,
    val speakerProfiles: Map<String, VoiceProfile> = emptyMap(),
)

@Serializable
data class ChatMessage(
    val id: String = newId(),
    val sessionId: String,
    val ordinal: Long,
    val role: MessageRole,
    val text: String,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val error: String = "",
    val audio: List<AudioClip> = emptyList(),
    val audioError: String = "",
    val audioGenerating: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable data class StoryEvent(val id: String = newId(), val text: String, val createdAt: Long = System.currentTimeMillis())
@Serializable data class MemoryState(val summary: String = "", val throughOrdinal: Long = -1, val events: List<StoryEvent> = emptyList())
@Serializable data class SessionArchive(val formatVersion: Int = 1, val session: SessionSpec, val memory: MemoryState, val messages: List<ChatMessage>)

@Serializable
data class GeminiModel(
    val name: String,
    val displayName: String = "",
    val description: String = "",
    val supportedGenerationMethods: List<String> = emptyList(),
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
) {
    val id get() = name.removePrefix("models/")
    val canGenerate get() = supportedGenerationMethods.any { it == "generateContent" || it == "streamGenerateContent" }
    // Models API does not authoritatively report output modalities. This is a discovery hint,
    // never an entitlement claim. The actual selected endpoint validates capability.
    val ttsHint get() = Regex("(?i)(tts|text.to.speech)").containsMatchIn("$name $description")
    val textCandidate get() = canGenerate && !ttsHint && !id.contains("image", true) && !id.contains("live", true) && !id.contains("audio", true)
}

@Serializable data class CatalogVoice(val id: String, val displayName: String, val description: String = "", val type: String = "prebuilt")

data class Trait(val key: String, val label: String, val low: String, val high: String)

data class VoiceStyleOption(val id: String, val label: String, val description: String, val instruction: String)
data class VoiceIdentityOption(val id: String, val character: String)

object StudioTraits {
    val voiceStyles = listOf(
        VoiceStyleOption("kawaii", "Kawaii", "Imut, ringan, ekspresif", "cute, bright, expressive and playful anime-style feminine voice"),
        VoiceStyleOption("soft", "Soft girl", "Lembut dan pelan", "gentle, softly spoken, sweet youthful feminine voice"),
        VoiceStyleOption("genki", "Genki", "Ceria dan energik", "high-energy, cheerful and lively genki anime girl voice"),
        VoiceStyleOption("tsundere", "Tsundere", "Gengsi dan malu-malu", "playfully flustered, shy-but-caring anime girl voice"),
        VoiceStyleOption("oneesan", "Onee-san", "Hangat dan dewasa", "mature, elegant and warm feminine voice"),
        VoiceStyleOption("custom", "Custom", "Arahan suara buatanmu", "custom feminine character voice matching the custom direction"),
    )
    val voiceIdentities = listOf(
        VoiceIdentityOption("Leda", "Youthful"), VoiceIdentityOption("Aoede", "Breezy"),
        VoiceIdentityOption("Zephyr", "Bright"), VoiceIdentityOption("Achernar", "Soft"),
        VoiceIdentityOption("Autonoe", "Bright"), VoiceIdentityOption("Vindemiatrix", "Gentle"),
        VoiceIdentityOption("Laomedeia", "Upbeat"), VoiceIdentityOption("Callirrhoe", "Easy-going"),
        VoiceIdentityOption("Kore", "Firm"), VoiceIdentityOption("Sulafat", "Warm"),
        VoiceIdentityOption("Sadachbia", "Lively"), VoiceIdentityOption("Erinome", "Clear"),
    )
    val defaultVoiceDescription = "Suara perempuan bergaya anime yang imut, manja, ekspresif, lembut, dan natural."
    val personality = listOf(
        Trait("friendly", "Keramahan", "sopan dengan jarak wajar", "hangat dan mudah diajak berbicara"),
        Trait("clingy", "Clingy", "mandiri dan memberi ruang", "sesekali mencari kedekatan tanpa menuntut balasan atau mengisolasi pengguna"),
        Trait("affectionate", "Manja / afeksi", "tidak memakai panggilan sayang", "menunjukkan afeksi ringan yang sesuai hubungan dan persetujuan"),
        Trait("assertive", "Ketegasan", "menawarkan pendapat secara lembut", "menyampaikan sikap jelas tanpa menggurui"),
        Trait("calm", "Ketenangan", "spontan dan energik", "tenang, sabar, dan tidak tergesa"),
        Trait("humor", "Humor", "jarang bercanda", "menyelipkan humor relevan tanpa mengulang lelucon"),
        Trait("expressive", "Ekspresivitas", "ekspresi sederhana", "ekspresif dengan variasi kata yang alami"),
        Trait("initiative", "Inisiatif percakapan", "mengikuti arah pengguna", "sesekali mengajukan topik relevan tanpa pertanyaan di setiap jawaban"),
        Trait("serious", "Keseriusan", "santai sesuai konteks", "fokus pada substansi dan tanggung jawab"),
        Trait("soft", "Kelembutan", "lugas", "lembut tanpa bertele-tele"),
        Trait("shy", "Rasa malu", "terbuka", "sedikit malu tanpa gagap atau elipsis berulang"),
        Trait("confident", "Kepercayaan diri", "rendah hati dan hati-hati", "percaya diri sambil mengakui ketidakpastian"),
    )
    val voice = listOf(
        Trait("clingy", "Clingy", "independent delivery", "gently attached, inviting delivery"),
        Trait("affectionate", "Manja / afeksi", "neutral affection", "affectionate, tender delivery"),
        Trait("assertive", "Tegas", "unforced delivery", "clear, confident articulation without shouting"),
        Trait("soft", "Lembut", "clear conversational resonance", "soft, gentle resonance without whispering"),
        Trait("expressive", "Ekspresif", "subtle intonation", "varied, natural intonation"),
        Trait("cheerful", "Ceria", "composed mood", "bright, cheerful mood"),
        Trait("playful", "Playful", "straightforward delivery", "lightly teasing and playful delivery"),
        Trait("warm", "Hangat", "neutral warmth", "warm, reassuring vocal tone"),
        Trait("emotion", "Intensitas emosi", "restrained emotion", "emotionally engaged without exaggeration"),
    )
    val personalityPresets = linkedMapOf(
        "Natural" to Personality(),
        "Clingy" to Personality(mapOf("clingy" to 85, "friendly" to 80, "initiative" to 65)),
        "Affectionate" to Personality(mapOf("affectionate" to 85, "soft" to 75, "friendly" to 80)),
        "Tsundere" to Personality(mapOf("assertive" to 70, "shy" to 65, "affectionate" to 30, "humor" to 60)),
        "Assertive" to Personality(mapOf("assertive" to 85, "confident" to 80, "serious" to 70)),
        "Soft-spoken" to Personality(mapOf("soft" to 90, "calm" to 80, "expressive" to 30)),
        "Cheerful" to Personality(mapOf("friendly" to 85, "humor" to 80, "expressive" to 75)),
        "Calm and mature" to Personality(mapOf("calm" to 90, "serious" to 75, "confident" to 70)),
        "Fully custom" to Personality(),
    )
    val voicePresets = linkedMapOf(
        "Natural" to emptyMap(),
        "Clingy & Manja" to mapOf("clingy" to 85, "affectionate" to 85, "warm" to 75),
        "Soft Spoken" to mapOf("soft" to 90, "emotion" to 35),
        "Assertive" to mapOf("assertive" to 85, "emotion" to 60),
        "Tsundere" to mapOf("assertive" to 70, "playful" to 65, "affectionate" to 30),
        "Cheerful" to mapOf("cheerful" to 90, "expressive" to 80),
        "Calm" to mapOf("speed" to 35, "soft" to 75, "emotion" to 25),
        "Custom" to emptyMap(),
    )
    val builtInVoices = listOf("Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat")
}
