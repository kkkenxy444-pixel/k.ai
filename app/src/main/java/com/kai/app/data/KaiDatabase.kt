package com.kai.app.data

import androidx.room.*
import com.kai.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

@Entity(tableName = "characters")
data class CharacterEntity(@PrimaryKey val id: String, val specJson: String)

@Entity(tableName = "sessions", foreignKeys = [ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["characterId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("characterId")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mode: String,
    val characterId: String,
    val model: String,
    val scenarioJson: String,
    val autoVoice: Boolean = false,
    val autoPlay: Boolean = false,
    val summary: String = "",
    val summaryUntil: Long = -1,
    val eventsJson: String = "[]",
    val draft: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SessionRecord(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "characterId", entityColumn = "id") val character: CharacterEntity,
) {
    fun spec() = SessionSpec(session.id, session.title, ChatMode.valueOf(session.mode), KaiJson.decodeFromString(character.specJson), session.model, KaiJson.decodeFromString(session.scenarioJson), session.autoVoice, session.autoPlay)
    fun memory() = MemoryState(session.summary, session.summaryUntil, KaiJson.decodeFromString(session.eventsJson))
}

@Entity(tableName = "messages", foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId", "ordinal"], unique = true)])
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val ordinal: Long,
    val role: String,
    val text: String,
    val status: String,
    val error: String = "",
    val audioJson: String = "[]",
    val audioError: String = "",
    val audioGenerating: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun domain() = ChatMessage(id, sessionId, ordinal, MessageRole.valueOf(role), text, MessageStatus.valueOf(status), error, KaiJson.decodeFromString(audioJson), audioError, audioGenerating, createdAt)
}
fun ChatMessage.entity() = MessageEntity(id, sessionId, ordinal, role.name, text, status.name, error, KaiJson.encodeToString(audio), audioError, audioGenerating, createdAt)

@Entity(tableName = "templates") data class TemplateEntity(@PrimaryKey val id: String, val name: String, val specJson: String)
@Entity(tableName = "voice_profiles") data class VoiceEntity(@PrimaryKey val id: String, val name: String, val specJson: String) {
    fun profile(): VoiceProfile = KaiJson.decodeFromString(specJson)
}
@Entity(tableName = "personality_presets") data class PersonalityEntity(@PrimaryKey val id: String, val name: String, val specJson: String)
@Entity(tableName = "models") data class ModelEntity(@PrimaryKey val name: String, val specJson: String, val fetchedAt: Long)

@Dao
interface KaiDao {
    @Transaction @Query("SELECT * FROM sessions ORDER BY updatedAt DESC") fun watchSessions(): Flow<List<SessionRecord>>
    @Transaction @Query("SELECT * FROM sessions WHERE id = :id") fun watchSession(id: String): Flow<SessionRecord?>
    @Transaction @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: String): SessionRecord?
    @Query("SELECT * FROM messages WHERE sessionId = :id ORDER BY ordinal") fun watchMessages(id: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE sessionId = :id ORDER BY ordinal DESC LIMIT :limit") fun watchRecentMessages(id: String, limit: Int): Flow<List<MessageEntity>>
    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :id") fun watchMessageCount(id: String): Flow<Int>
    @Query("SELECT * FROM messages WHERE sessionId = :id ORDER BY ordinal") suspend fun messages(id: String): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE sessionId = :id AND ordinal > :after ORDER BY ordinal") suspend fun messagesAfter(id: String, after: Long): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE sessionId = :id AND ordinal < :before AND role = 'USER' ORDER BY ordinal DESC LIMIT 1") suspend fun previousUser(id: String, before: Long): MessageEntity?
    @Query("SELECT * FROM messages WHERE id = :id") suspend fun message(id: String): MessageEntity?
    @Query("SELECT COALESCE(MAX(ordinal), -1) + 1 FROM messages WHERE sessionId = :id") suspend fun nextOrdinal(id: String): Long
    @Upsert suspend fun upsertSession(value: SessionEntity)
    @Upsert suspend fun upsertCharacter(value: CharacterEntity)
    @Upsert suspend fun upsertMessage(value: MessageEntity)
    @Upsert suspend fun upsertTemplate(value: TemplateEntity)
    @Upsert suspend fun upsertVoice(value: VoiceEntity)
    @Upsert suspend fun upsertPersonality(value: PersonalityEntity)
    @Upsert suspend fun upsertModels(value: List<ModelEntity>)
    @Query("SELECT * FROM models ORDER BY name") fun watchModels(): Flow<List<ModelEntity>>
    @Query("DELETE FROM models") suspend fun clearModels()
    @Query("SELECT * FROM templates ORDER BY name") fun watchTemplates(): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM voice_profiles ORDER BY name") fun watchVoices(): Flow<List<VoiceEntity>>
    @Query("SELECT * FROM personality_presets ORDER BY name") fun watchPersonalities(): Flow<List<PersonalityEntity>>
    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: String)
    @Query("DELETE FROM voice_profiles WHERE id = :id") suspend fun deleteVoice(id: String)
    @Query("DELETE FROM personality_presets WHERE id = :id") suspend fun deletePersonality(id: String)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: String)
    @Query("DELETE FROM characters WHERE id = :id") suspend fun deleteCharacter(id: String)
    @Query("DELETE FROM messages WHERE id = :id") suspend fun deleteMessage(id: String)
    @Query("DELETE FROM messages WHERE sessionId = :id AND ordinal >= :ordinal") suspend fun truncate(id: String, ordinal: Long)
    @Query("DELETE FROM messages WHERE sessionId = :id") suspend fun clearMessages(id: String)
    @Query("UPDATE sessions SET summary = :summary, summaryUntil = :until WHERE id = :id") suspend fun updateSummary(id: String, summary: String, until: Long)
    @Query("UPDATE sessions SET eventsJson = :events WHERE id = :id") suspend fun updateEvents(id: String, events: String)
    @Query("UPDATE sessions SET draft = :draft WHERE id = :id") suspend fun updateDraft(id: String, draft: String)
    @Query("UPDATE sessions SET updatedAt = :time WHERE id = :id") suspend fun touch(id: String, time: Long = System.currentTimeMillis())
    @Query("UPDATE messages SET status = 'INTERRUPTED', error = 'Permintaan terhenti. Ketuk Coba lagi untuk melanjutkan.' WHERE status = 'GENERATING'") suspend fun recoverText()
    @Query("UPDATE messages SET audioGenerating = 0, audioError = 'Pembuatan VN terhenti. Coba lagi.' WHERE audioGenerating = 1") suspend fun recoverAudio()
    @Query("SELECT audioJson FROM messages WHERE audioJson != '[]'") suspend fun audioReferences(): List<String>
    @Query("UPDATE messages SET audioJson = '[]', audioError = '', audioGenerating = 0") suspend fun clearAudio()
}

@Database(entities = [CharacterEntity::class, SessionEntity::class, MessageEntity::class, TemplateEntity::class, VoiceEntity::class, PersonalityEntity::class, ModelEntity::class], version = 1, exportSchema = true)
abstract class KaiDatabase : RoomDatabase() {
    abstract fun dao(): KaiDao
    // No destructive fallback. Add explicit tested Migration objects for every future schema version.
}
