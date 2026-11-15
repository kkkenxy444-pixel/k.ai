package com.kai.core.ai

import com.kai.core.model.*

enum class SegmentKind { NARRATION, ACTION, DIALOGUE }
data class StorySegment(val kind: SegmentKind, val speaker: String, val text: String)
data class SpeechTurn(val text: String, val speaker: String, val profile: VoiceProfile)

object RoleplayParser {
    private val marker = Regex("(?m)^\\[([^]\\n]+)]\\s*")
    fun parse(text: String, actors: List<CharacterSpec>): List<StorySegment> {
        val matches = marker.findAll(text).toList()
        if (matches.isEmpty()) return listOf(StorySegment(SegmentKind.NARRATION, "", text))
        val result = mutableListOf<StorySegment>()
        val preamble = text.take(matches.first().range.first).trim()
        if (preamble.isNotEmpty()) result += StorySegment(SegmentKind.NARRATION, "", preamble)
        matches.forEachIndexed { i, m ->
            val label = m.groupValues[1].trim()
            val body = text.substring(m.range.last + 1, matches.getOrNull(i + 1)?.range?.first ?: text.length).trim()
            if (body.isEmpty()) return@forEachIndexed
            val actor = actors.firstOrNull { it.name.equals(label, ignoreCase = true) }
            result += when {
                label.equals("Narasi", true) -> StorySegment(SegmentKind.NARRATION, "", body)
                label.startsWith("Aksi:", true) -> StorySegment(SegmentKind.ACTION, label.substringAfter(':').trim(), body)
                actor != null -> StorySegment(SegmentKind.DIALOGUE, actor.name, body)
                else -> StorySegment(SegmentKind.NARRATION, "", "[$label] $body")
            }
        }
        return result
    }

    fun speech(session: SessionSpec, text: String): List<SpeechTurn> {
        if (session.mode == ChatMode.NORMAL) return listOf(SpeechTurn(plainText(text), session.character.name, session.character.voice))
        val actors = session.scenario.actors
        return parse(text, actors).mapNotNull { segment ->
            val actor = actors.firstOrNull { it.name == segment.speaker }
            when (session.scenario.voiceScope) {
                VoiceScope.DIALOGUE -> if (segment.kind != SegmentKind.DIALOGUE) return@mapNotNull null
                VoiceScope.CHARACTER -> if (segment.kind != SegmentKind.DIALOGUE || actor?.id != session.scenario.selectedSpeakerId) return@mapNotNull null
                VoiceScope.ALL -> Unit
            }
            val voice = if (segment.kind == SegmentKind.DIALOGUE) actor?.voice
                ?: throw IllegalArgumentException("Suara karakter ${segment.speaker} belum dipetakan.") else session.character.voice
            SpeechTurn(plainText(segment.text), if (segment.kind == SegmentKind.DIALOGUE) segment.speaker else "Narator", voice)
        }.filter { it.text.isNotBlank() }
    }

    fun plainText(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), "[blok kode]")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("[*_`#]"), "")
        .trim()
}
