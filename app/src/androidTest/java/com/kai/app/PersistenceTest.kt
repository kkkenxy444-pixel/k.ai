package com.kai.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kai.app.data.*
import com.kai.app.security.KeyVault
import com.kai.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private lateinit var context: Context
    private lateinit var db: KaiDatabase
    private lateinit var repo: SessionRepository
    private val fileName = "test-${newId()}.db"
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        open()
    }
    private fun open() { db = Room.databaseBuilder(context, KaiDatabase::class.java, fileName).build(); repo = SessionRepository(db) }
    @After fun close() { db.close(); context.deleteDatabase(fileName) }
    private fun session(name: String) = SessionSpec(title = name, character = CharacterSpec(name = name), model = "models/fixture-text")

    @Test fun independentCharactersAndHistorySurviveReopen() = runBlocking {
        val anya = session("Anya").let { it.copy(character = it.character.copy(personality = StudioTraits.personalityPresets.getValue("Affectionate"))) }
        val reina = session("Reina")
        repo.saveSession(anya); repo.saveSession(reina)
        repo.appendUser(anya.id, "Pesan untuk Anya")
        repo.appendUser(reina.id, "Pesan untuk Reina")
        repo.saveSession(reina.copy(character = reina.character.copy(personality = StudioTraits.personalityPresets.getValue("Assertive"))))
        assertEquals(85, repo.dao.session(anya.id)!!.spec().character.personality.value("affectionate"))
        assertEquals("Pesan untuk Anya", repo.dao.messages(anya.id).single().text)
        db.close(); open()
        assertEquals("Pesan untuk Reina", repo.dao.messages(reina.id).single().text)
        assertEquals(anya.character.id, repo.dao.session(anya.id)!!.character.id)
    }

    @Test fun replacingModelKeepsMessagesAndMemory() = runBlocking {
        val s = session("Anya"); repo.saveSession(s); repo.appendUser(s.id, "Halo")
        repo.dao.updateSummary(s.id, "Kenalan dengan Anya", 0)
        repo.saveSession(s.copy(model = "models/fixture-other"))
        assertEquals(1, repo.dao.messages(s.id).size)
        assertEquals("Kenalan dengan Anya", repo.dao.session(s.id)!!.memory().summary)
    }

    @Test fun deleteOnlyRemovesTargetSessionAndMessages() = runBlocking {
        val a = session("Anya"); val b = session("Reina")
        repo.saveSession(a); repo.saveSession(b); repo.appendUser(a.id, "A"); repo.appendUser(b.id, "B")
        repo.delete(a.id)
        assertNull(repo.dao.session(a.id)); assertTrue(repo.dao.messages(a.id).isEmpty())
        assertNotNull(repo.dao.session(b.id)); assertEquals("B", repo.dao.messages(b.id).single().text)
    }

    @Test fun duplicateCopiesCharacterHistoryAndScenarioWithoutSharingIds() = runBlocking {
        val actor = CharacterSpec(name = "Elyra")
        val s = session("Forest").copy(mode = ChatMode.ROLEPLAY, scenario = Scenario(world = "Hutan", opening = "Pintu terbuka", actors = listOf(actor), selectedSpeakerId = actor.id))
        repo.saveSession(s); repo.appendUser(s.id, "Aku masuk")
        repo.dao.updateEvents(s.id, KaiJson.encodeToString(listOf(StoryEvent(text = "Pintu utara"))))
        val id = repo.duplicate(s.id); val copy = repo.dao.session(id)!!
        assertNotEquals(s.character.id, copy.spec().character.id)
        assertNotEquals(actor.id, copy.spec().scenario.actors.single().id)
        assertEquals("Aku masuk", repo.dao.messages(id).single().text)
        assertEquals("Pintu utara", copy.memory().events.single().text)
    }

    @Test fun interruptedTextAndAudioAreRecoveredWithoutDiscardingText() = runBlocking {
        val s = session("Anya"); repo.saveSession(s)
        val m = ChatMessage(sessionId = s.id, ordinal = 0, role = MessageRole.MODEL, text = "Sebagian respons", status = MessageStatus.GENERATING, audioGenerating = true)
        repo.dao.upsertMessage(m.entity()); repo.dao.recoverText(); repo.dao.recoverAudio()
        val restored = repo.dao.message(m.id)!!.domain()
        assertEquals(MessageStatus.INTERRUPTED, restored.status)
        assertEquals("Sebagian respons", restored.text); assertFalse(restored.audioGenerating)
    }

    @Test fun exportImportContainsNoCredentialsAndUsesFreshIds() = runBlocking {
        val s = session("Anya"); repo.saveSession(s); repo.appendUser(s.id, "Halo")
        val raw = repo.export(s.id)
        assertFalse(raw.contains("apiKey", true))
        val imported = repo.import(raw)
        assertNotEquals(s.id, imported)
        assertNotEquals(s.character.id, repo.dao.session(imported)!!.character.id)
        assertEquals("Halo", repo.dao.messages(imported).single().text)
    }

    @Test fun credentialReplacementDoesNotTouchTheDatabase() = runBlocking {
        val s = session("Anya"); repo.saveSession(s); repo.appendUser(s.id, "Tetap ada")
        val vaultDirectory = java.io.File(context.cacheDir, "vault-test-${newId()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getNoBackupFilesDir() = vaultDirectory
        }
        val vault = KeyVault(isolated)
        try {
            vault.save("fixture-first-not-a-real-api-key")
            vault.save("fixture-second-not-a-real-api-key")
            val reopened = KeyVault(isolated); reopened.load()
            assertEquals("fixture-second-not-a-real-api-key", reopened.current())
            val bytes = java.io.File(vaultDirectory, "gemini.credential").readBytes()
            assertFalse(bytes.toString(Charsets.UTF_8).contains("fixture-second"))
            assertEquals("Tetap ada", repo.dao.messages(s.id).single().text)
            vault.delete(); assertEquals("Tetap ada", repo.dao.messages(s.id).single().text)
        } finally { vault.delete(); vaultDirectory.deleteRecursively() }
    }

    @Test fun manifestHasNoMicrophonePermission() {
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.RECORD_AUDIO"))
    }
}
