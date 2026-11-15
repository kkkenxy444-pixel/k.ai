package com.kai.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kai.app.KaiApplication
import com.kai.app.data.UserPreferences
import com.kai.core.ai.SpeechTurn
import com.kai.core.model.*
import com.kai.core.network.Failures
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.io.File

data class OperationState(val busy: Boolean = false, val message: String = "", val error: Boolean = false)
data class VoicePreview(val label: String, val profile: VoiceProfile, val sentence: String, val file: File)

class KaiViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val graph = (application as KaiApplication).graph
    val repo = graph.repository
    val prefs = graph.preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())
    val sessions = repo.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val models = repo.models.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val voices = repo.voices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val templates = repo.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val personalities = repo.personalities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val route = saved.getStateFlow("route", "home")
    val selectedId = saved.getStateFlow("selectedId", "")
    val editorJson = saved.getStateFlow("editor", "")
    val voiceJson = saved.getStateFlow("voice", "")
    val ready = MutableStateFlow(false)
    val hasKey = MutableStateFlow(false)
    val keyRecovery = MutableStateFlow(false)
    val operation = MutableStateFlow(OperationState())
    val catalogVoices = MutableStateFlow<List<CatalogVoice>>(emptyList())
    val voiceCatalogChecked = MutableStateFlow(false)
    val previews = MutableStateFlow<List<VoicePreview>>(emptyList())
    val voiceBusy = MutableStateFlow(false)
    val voiceError = MutableStateFlow("")
    val voiceDesignIds = MutableStateFlow<List<String>>(emptyList())
    val snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var previewJob: Job? = null
    private var draftJob: Job? = null

    init {
        viewModelScope.launch {
            try { graph.initialized.await(); graph.preferences.flow.first(); ready.value = true; updateKeyState() }
            catch (e: Exception) { operation.value = OperationState(message = "Penyimpanan gagal dibuka. Mulai ulang aplikasi; data tidak dihapus.", error = true) }
        }
        viewModelScope.launch { graph.chat.notices.collect { snackbar.emit(it) } }
    }

    fun navigate(route: String) { saved["route"] = route; operation.value = OperationState() }
    fun openSession(id: String) { saved["selectedId"] = id; navigate("chat") }
    fun back() {
        when (route.value) {
            "personality" -> navigate("editor")
            "voiceEditor" -> { cancelPreview(); navigate(saved.get<String>("voiceReturn") ?: "voiceLibrary") }
            "memory" -> navigate("chat")
            "editor" -> navigate(if (saved.get<Boolean>("editingExisting") == true) "chat" else "home")
            "api", "models" -> navigate("settings")
            else -> navigate("home")
        }
    }
    fun completeOnboarding() = launch { graph.preferences.flag("onboarded", true); navigate("api") }
    fun preference(key: String, value: String) = launch { graph.preferences.text(key, value) }
    fun preference(key: String, value: Boolean) = launch { graph.preferences.flag(key, value) }
    fun notify(text: String) { snackbar.tryEmit(text) }

    fun testKey(draft: String) = operation {
        val found = graph.api.listModels(draft.trim().ifBlank { null })
        if (draft.isBlank() || draft.trim() == graph.vault.current()) repo.cacheModels(found)
        "Autentikasi berhasil. ${found.size} model ditemukan. Harga dan kuota tidak dapat dipastikan dari katalog."
    }
    fun saveKey(draft: String) = operation {
        cancelPreview()
        graph.chat.cancelAll()
        withContext(Dispatchers.IO) { graph.vault.save(draft) }
        repo.dao.clearModels(); catalogVoices.value = emptyList(); voiceCatalogChecked.value = false
        updateKeyState()
        "API key tersimpan. Sesi dan karakter tetap utuh. Uji koneksi atau segarkan model."
    }
    fun deleteKey() = operation {
        cancelPreview()
        graph.chat.cancelAll()
        withContext(Dispatchers.IO) { graph.vault.delete() }
        repo.dao.clearModels(); catalogVoices.value = emptyList(); voiceCatalogChecked.value = false; updateKeyState()
        "API key dihapus. Semua data percakapan tetap tersimpan."
    }
    fun refreshModels() = operation {
        val found = graph.api.listModels(); repo.cacheModels(found)
        "Katalog diperbarui: ${found.size} model. Pilih model secara manual."
    }
    private fun updateKeyState() { hasKey.value = !graph.vault.current().isNullOrBlank(); keyRecovery.value = graph.vault.recoveryRequired }

    fun newSession(mode: ChatMode, template: CharacterSpec? = null) {
        val character = template?.independentCopy() ?: CharacterSpec(name = if (mode == ChatMode.ROLEPLAY) "Narator" else "", voice = VoiceProfile(model = prefs.value.ttsModel))
        setEditor(SessionSpec(title = "", mode = mode, character = character,
            model = if (mode == ChatMode.NORMAL) prefs.value.normalModel else prefs.value.roleplayModel))
        saved["editingExisting"] = false; saved["actor"] = -1; navigate("editor")
    }
    fun editSession(id: String) = launch {
        setEditor(requireNotNull(repo.dao.session(id)).spec()); saved["editingExisting"] = true; saved["actor"] = -1; navigate("editor")
    }
    fun editor(): SessionSpec? = editorJson.value.takeIf { it.isNotBlank() }?.let { KaiJson.decodeFromString(it) }
    fun setEditor(value: SessionSpec) { saved["editor"] = KaiJson.encodeToString(value) }
    fun saveEditor() = operation {
        val spec = requireNotNull(editor())
        require(graph.chat.busy.value.contains(spec.id).not()) { "Hentikan respons yang sedang dibuat sebelum mengubah sesi." }
        graph.chat.cancelVoiceForSession(spec.id)
        repo.saveSession(spec); saved["editor"] = ""; openSession(spec.id)
        "Sesi tersimpan."
    }
    fun saveTemplate() = launch { editor()?.let { repo.template(it); snackbar.emit("Karakter disimpan sebagai template independen.") } }
    fun duplicate(id: String) = launch { openSession(repo.duplicate(id)) }
    fun deleteSession(id: String) = launch { graph.chat.removeSession(id); if (selectedId.value == id) navigate("home") }

    fun openPersonality(actorIndex: Int = -1) { saved["actor"] = actorIndex; navigate("personality") }
    fun activeCharacter(): CharacterSpec? {
        val spec = editor() ?: return null
        val index = saved.get<Int>("actor") ?: -1
        return if (index < 0) spec.character else spec.scenario.actors.getOrNull(index)
    }
    fun updateActiveCharacter(value: CharacterSpec) {
        val spec = editor() ?: return
        val index = saved.get<Int>("actor") ?: -1
        setEditor(if (index < 0) spec.copy(character = value) else spec.copy(scenario = spec.scenario.copy(actors = spec.scenario.actors.mapIndexed { i, a -> if (i == index) value else a })))
    }
    fun savePersonality(name: String) = launch { activeCharacter()?.let { repo.savePersonality(name, it.personality); snackbar.emit("Preset kepribadian disimpan.") } }

    fun openVoice(profile: VoiceProfile, actorIndex: Int? = null) {
        saved["voiceReturn"] = route.value
        saved["voiceApply"] = actorIndex != null
        if (actorIndex != null) saved["actor"] = actorIndex
        saved["voice"] = KaiJson.encodeToString(profile)
        voiceError.value = ""; navigate("voiceEditor")
    }
    fun voice(): VoiceProfile = voiceJson.value.takeIf { it.isNotBlank() }?.let { KaiJson.decodeFromString(it) } ?: VoiceProfile(model = prefs.value.ttsModel)
    fun setVoice(value: VoiceProfile) { saved["voice"] = KaiJson.encodeToString(value) }
    fun saveVoice() = launch {
        val profile = voice()
        repo.saveVoice(profile)
        if (saved.get<Boolean>("voiceApply") == true) activeCharacter()?.let { updateActiveCharacter(it.copy(voice = profile.independentCopy())) }
        snackbar.emit("Profil suara tersimpan. Sesi lain tidak berubah."); back()
    }
    fun duplicateVoice(profile: VoiceProfile) = launch { repo.saveVoice(profile.copy(id = newId(), name = "${profile.name} • salinan")) }
    fun deleteVoice(id: String) = launch { repo.dao.deleteVoice(id) }
    fun refreshVoices() = operation {
        catalogVoices.value = graph.api.listVoices(); voiceCatalogChecked.value = true
        "Katalog suara tersedia untuk key ini. Akses pembuatan suara tetap divalidasi server."
    }
    fun designVoice(description: String) = operation {
        val current = voice()
        require(voiceCatalogChecked.value && current.transport == TtsTransport.INTERACTIONS) { "Periksa akses katalog dan pilih protokol Interactions terlebih dahulu." }
        val designed = graph.api.designVoice(current.model, current.name, description)
        voiceDesignIds.value = voiceDesignIds.value + designed.voice.id
        setVoice(current.copy(voiceId = designed.voice.id))
        catalogVoices.value = listOf(designed.voice) + catalogVoices.value
        designed.sample?.let { addPreview("Desain", current.copy(voiceId = designed.voice.id), "Sampel bawaan penyedia", graph.audio.preview(it)) }
        "Suara dibuat di proyek Google milikmu. Simpan profil untuk memakai ID-nya."
    }
    fun preview(sentence: String) {
        if (previewJob?.isActive == true) return
        val profile = voice()
        voiceBusy.value = true; voiceError.value = ""
        previewJob = viewModelScope.launch {
            try {
                val payload = graph.api.tts(SpeechTurn(sentence, "Preview", profile))
                addPreview(if (previews.value.isEmpty()) "A" else "B", profile, sentence, graph.audio.preview(payload))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { voiceError.value = Failures.readable(e) }
            finally { voiceBusy.value = false }
        }
    }
    private fun addPreview(label: String, profile: VoiceProfile, sentence: String, file: File) {
        val old = previews.value
        val next = (old + VoicePreview(label, profile, sentence, file)).takeLast(2)
        old.filter { it !in next }.forEach { it.file.delete() }
        previews.value = next.mapIndexed { i, it -> it.copy(label = if (i == 0) "A" else "B") }
    }
    fun cancelPreview() { previewJob?.cancel(); graph.player.stop() }
    fun send(id: String, text: String) { draftJob?.cancel(); graph.chat.send(id, text) }
    fun draft(id: String, text: String) { draftJob?.cancel(); draftJob = viewModelScope.launch { delay(300); repo.dao.updateDraft(id, text) } }
    fun pinMemory(id: String, text: String) = launch {
        require(text.isNotBlank()) { "Catatan tidak boleh kosong." }
        val old = requireNotNull(repo.dao.session(id)).memory()
        repo.dao.updateEvents(id, KaiJson.encodeToString(old.events + StoryEvent(text = text.trim())))
    }
    fun removeMemory(id: String, eventId: String) = launch {
        val old = requireNotNull(repo.dao.session(id)).memory()
        repo.dao.updateEvents(id, KaiJson.encodeToString(old.events.filterNot { it.id == eventId }))
    }
    fun clearSummary(id: String) = launch { require(!graph.chat.busy.value.contains(id)); repo.dao.updateSummary(id, "", -1) }
    fun clearChat(id: String) = launch {
        graph.chat.cancelSession(id)
        val files = repo.dao.messages(id).flatMap { it.domain().audio }.map { it.fileName }
        repo.clearConversation(id); graph.audio.delete(files)
    }
    fun clearAudio() = launch { graph.chat.cancelAll(); graph.audio.clearAll(); snackbar.emit("Cache audio dihapus; teks tetap tersedia.") }

    fun export(uri: Uri, id: String) = launch {
        val json = repo.export(id)
        withContext(Dispatchers.IO) { requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(json) } }
        snackbar.emit("Sesi diekspor tanpa API key dan berkas audio.")
    }
    fun import(uri: Uri) = launch {
        val raw = withContext(Dispatchers.IO) {
            requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use {
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (buffer.size() <= 5_000_000) {
                    val count = it.read(chunk, 0, minOf(chunk.size, 5_000_001 - buffer.size()))
                    if (count < 0) break
                    buffer.write(chunk, 0, count)
                }
                val bytes = buffer.toByteArray()
                require(bytes.size <= 5_000_000) { "Berkas impor maksimal 5 MB." }
                bytes.toString(Charsets.UTF_8)
            }
        }
        openSession(repo.import(raw)); snackbar.emit("Sesi diimpor sebagai salinan independen.")
    }
    fun launch(block: suspend () -> Unit) { viewModelScope.launch { try { graph.initialized.await(); block() } catch (e: CancellationException) { throw e } catch (e: Exception) { snackbar.emit(Failures.readable(e)) } } }
    private fun operation(block: suspend () -> String) {
        if (operation.value.busy) return
        operation.value = OperationState(busy = true)
        viewModelScope.launch {
            try { graph.initialized.await(); operation.value = OperationState(message = block()) }
            catch (e: CancellationException) { operation.value = OperationState(); throw e }
            catch (e: Exception) { operation.value = OperationState(message = Failures.readable(e), error = true) }
        }
    }
    override fun onCleared() { graph.player.stop(); previews.value.forEach { it.file.delete() } }
}
