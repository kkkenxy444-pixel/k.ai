package com.kai.app.data

import androidx.room.withTransaction
import com.kai.core.model.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

class SessionRepository(val db: KaiDatabase) {
    val dao = db.dao()
    val sessions = dao.watchSessions()
    val models = dao.watchModels().map { rows -> rows.map { KaiJson.decodeFromString<GeminiModel>(it.specJson) } }
    val voices = dao.watchVoices().map { rows -> rows.map { it.profile() } }
    val templates = dao.watchTemplates()
    val personalities = dao.watchPersonalities()
    fun messages(id: String) = dao.watchMessages(id).map { it.map(MessageEntity::domain) }
    fun recentMessages(id: String, limit: Int) = dao.watchRecentMessages(id, limit).map { it.asReversed().map(MessageEntity::domain) }

    suspend fun saveSession(spec: SessionSpec) = db.withTransaction {
        validate(spec)
        val old = dao.session(spec.id)?.session
        dao.upsertCharacter(CharacterEntity(spec.character.id, KaiJson.encodeToString(spec.character)))
        dao.upsertSession(SessionEntity(spec.id, spec.title.trim(), spec.mode.name, spec.character.id, spec.model,
            KaiJson.encodeToString(spec.scenario), spec.autoVoice, spec.autoPlay,
            old?.summary.orEmpty(), old?.summaryUntil ?: -1, old?.eventsJson ?: "[]", old?.draft.orEmpty()))
        if (old != null && old.characterId != spec.character.id) dao.deleteCharacter(old.characterId)
    }

    suspend fun duplicate(id: String): String = db.withTransaction {
        val record = requireNotNull(dao.session(id))
        val original = record.spec()
        val spec = original.copy(id = newId(), title = "${original.title} • salinan", character = original.character.independentCopy(), scenario = original.scenario.independentCopy())
        saveSession(spec)
        dao.updateSummary(spec.id, record.session.summary, record.session.summaryUntil)
        dao.updateEvents(spec.id, record.session.eventsJson)
        dao.messages(id).forEach { row -> dao.upsertMessage(row.copy(id = newId(), sessionId = spec.id, audioJson = "[]", audioGenerating = false, audioError = "", status = if (row.status == "GENERATING") "INTERRUPTED" else row.status)) }
        spec.id
    }

    suspend fun delete(id: String) = db.withTransaction {
        val record = dao.session(id) ?: return@withTransaction
        dao.deleteSession(id)
        dao.deleteCharacter(record.character.id)
    }

    suspend fun template(spec: SessionSpec) {
        dao.upsertTemplate(TemplateEntity(newId(), spec.character.name, KaiJson.encodeToString(spec.character.independentCopy())))
    }
    suspend fun saveVoice(profile: VoiceProfile) {
        require(profile.name.isNotBlank()) { "Nama profil suara wajib diisi." }
        dao.upsertVoice(VoiceEntity(profile.id, profile.name, KaiJson.encodeToString(profile)))
    }
    suspend fun savePersonality(name: String, personality: Personality) {
        require(name.isNotBlank()) { "Nama preset wajib diisi." }
        dao.upsertPersonality(PersonalityEntity(newId(), name, KaiJson.encodeToString(personality)))
    }
    suspend fun cacheModels(models: List<GeminiModel>) = db.withTransaction {
        dao.clearModels()
        dao.upsertModels(models.map { ModelEntity(it.name, KaiJson.encodeToString(it), System.currentTimeMillis()) })
    }

    suspend fun appendUser(id: String, text: String): ChatMessage = db.withTransaction {
        require(text.isNotBlank() && text.length <= 32_000) { "Pesan harus berisi 1–32.000 karakter." }
        val message = ChatMessage(sessionId = id, ordinal = dao.nextOrdinal(id), role = MessageRole.USER, text = text.trim())
        dao.upsertMessage(message.entity()); dao.updateDraft(id, ""); dao.touch(id)
        message
    }

    suspend fun truncate(id: String, ordinal: Long) = db.withTransaction {
        dao.truncate(id, ordinal)
        // Summaries may contain edited/deleted facts. Rebuild from source history, never keep stale facts.
        dao.updateSummary(id, "", -1)
    }
    suspend fun deleteMessage(message: ChatMessage) = db.withTransaction {
        dao.deleteMessage(message.id); dao.updateSummary(message.sessionId, "", -1)
    }
    suspend fun clearConversation(id: String) = db.withTransaction {
        dao.clearMessages(id); dao.updateSummary(id, "", -1); dao.updateEvents(id, "[]"); dao.updateDraft(id, "")
    }

    suspend fun export(id: String): String {
        val record = requireNotNull(dao.session(id))
        val archive = SessionArchive(session = record.spec(), memory = record.memory(), messages = dao.messages(id).map {
            it.domain().copy(audio = emptyList(), audioError = "", audioGenerating = false, status = if (it.status == "GENERATING") MessageStatus.INTERRUPTED else MessageStatus.valueOf(it.status))
        })
        return KaiJson.encodeToString(archive)
    }

    suspend fun import(raw: String): String = db.withTransaction {
        require(raw.toByteArray().size <= 5_000_000) { "Berkas impor maksimal 5 MB." }
        val archive = KaiJson.decodeFromString<SessionArchive>(raw)
        require(archive.formatVersion == 1) { "Versi berkas belum didukung." }
        require(archive.messages.size <= 20_000) { "Terlalu banyak pesan dalam satu berkas." }
        val spec = archive.session.copy(id = newId(), character = archive.session.character.independentCopy(), scenario = archive.session.scenario.independentCopy())
        saveSession(spec)
        archive.messages.sortedBy { it.ordinal }.forEachIndexed { i, m ->
            dao.upsertMessage(m.copy(id = newId(), sessionId = spec.id, ordinal = i.toLong(), audio = emptyList(), audioError = "", audioGenerating = false,
                status = if (m.status == MessageStatus.GENERATING) MessageStatus.INTERRUPTED else m.status).entity())
        }
        // Re-indexing invalidates summary coverage; keep only explicitly pinned notes.
        dao.updateEvents(spec.id, KaiJson.encodeToString(archive.memory.events))
        spec.id
    }

    private fun validate(spec: SessionSpec) {
        require(spec.title.isNotBlank() && spec.title.length <= 200) { "Judul sesi wajib diisi (maksimal 200 karakter)." }
        require(spec.character.name.isNotBlank()) { "Nama karakter wajib diisi." }
        if (spec.mode == ChatMode.ROLEPLAY) {
            require(spec.scenario.world.isNotBlank() && spec.scenario.opening.isNotBlank()) { "Isi dunia dan skenario awal roleplay." }
            require(spec.scenario.actors.isNotEmpty()) { "Tambahkan setidaknya satu karakter cerita." }
            val names = spec.scenario.actors.map { it.name.trim().lowercase() }
            require(names.all { it.isNotBlank() && !it.contains(']') && !it.contains('[') && !it.contains('\n') && it != "narasi" && !it.startsWith("aksi:") } && names.distinct().size == names.size) { "Nama aktor harus unik dan tidak memakai penanda [ ], Narasi, atau Aksi:." }
        }
    }
}
