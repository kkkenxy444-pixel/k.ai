package com.kai.core.ai

import com.kai.core.model.TtsTransport

/** Endpoint capabilities verified in Google's TTS guide; never a model-selection list. */
object TtsCapabilities {
    private val documentedInteractionsModels = setOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts")
    fun supportsVoiceDesign(model: String) = model.removePrefix("models/") in documentedInteractionsModels

    fun canCombine(turns: List<SpeechTurn>): Boolean {
        if (turns.isEmpty() || turns.sumOf { it.text.length } > 4500) return false
        val model = turns.first().profile.model.removePrefix("models/")
        if (model !in documentedInteractionsModels) return false
        if (turns.any { it.profile.transport != TtsTransport.INTERACTIONS || it.profile.model.removePrefix("models/") != model }) return false
        val speakers = turns.groupBy { it.speaker }
        return speakers.size == 2 && speakers.values.all { lines ->
            lines.all { it.text.isNotBlank() && it.profile.voiceId.isNotBlank() } && lines.map { it.profile.voiceId }.distinct().size == 1
        }
    }

    fun batches(turns: List<SpeechTurn>): List<List<SpeechTurn>> =
        if (canCombine(turns)) listOf(turns) else turns.map { listOf(it) }
}
