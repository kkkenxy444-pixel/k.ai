@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.kai.app.feature.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.app.feature.settings.ModelField
import com.kai.app.ui.*
import com.kai.core.model.*

@Composable fun HomeScreen(vm: KaiViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var templateSheet by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("k.ai", style = MaterialTheme.typography.headlineLarge) }) }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text("Percakapanmu.", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Hint("Satu ruang untuk setiap karakter dan cerita.")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.newSession(ChatMode.NORMAL) }, modifier = Modifier.weight(1f)) { Text("Chat baru") }
                    OutlinedButton(onClick = { vm.newSession(ChatMode.ROLEPLAY) }, modifier = Modifier.weight(1f)) { Text("Cerita baru") }
                }
                if (templates.isNotEmpty()) TextButton(onClick = { templateSheet = true }) { Text("Mulai dari template karakter") }
            }
            if (!hasKey) item {
                OutlinedCard(onClick = { vm.navigate("api") }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tambahkan key Gemini", style = MaterialTheme.typography.titleMedium)
                        Hint("Kamu tetap bisa menyiapkan karakter sebelum menghubungkan API.")
                    }
                }
            }
            if (sessions.isNotEmpty()) item { Field("Cari percakapan", query, { query = it }) }
            val visible = sessions.filter { "${it.session.title} ${it.spec().character.name}".contains(query, true) }
            items(visible, key = { it.session.id }) { record ->
                val spec = record.spec()
                var menu by remember { mutableStateOf(false) }
                Column {
                    Row(Modifier.fillMaxWidth().clickable { vm.openSession(spec.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text(spec.character.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge) }
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(spec.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${if (spec.mode == ChatMode.ROLEPLAY) "ROLEPLAY" else "CHAT"}  ·  ${spec.character.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu sesi") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Edit / ganti nama") }, onClick = { menu = false; vm.openSession(spec.id); vm.editSession(spec.id) })
                                DropdownMenuItem(text = { Text("Duplikat sesi") }, onClick = { menu = false; vm.duplicate(spec.id) })
                                DropdownMenuItem(text = { Text("Simpan karakter sebagai template") }, onClick = { menu = false; vm.launch { vm.repo.template(spec); vm.notify("Template tersimpan.") } })
                                DropdownMenuItem(text = { Text("Hapus sesi") }, onClick = { menu = false; deleting = spec.id })
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (visible.isEmpty()) item {
                Column(Modifier.padding(vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (query.isBlank()) "Belum ada percakapan." else "Tidak ada yang cocok.", style = MaterialTheme.typography.titleLarge)
                    Hint(if (query.isBlank()) "Mulai dengan nama, kepribadian, dan suara yang kamu pilih sendiri." else "Coba kata pencarian lain.")
                }
            }
        }
    }
    deleting?.let { id -> ConfirmDialog("Hapus sesi?", "Percakapan, karakter sesi ini, memori, dan VN akan dihapus. Sesi lain tidak berubah.", { deleting = null }) { vm.deleteSession(id) } }
    if (templateSheet) ModalBottomSheet(onDismissRequest = { templateSheet = false }) {
        LazyColumn(Modifier.fillMaxWidth().padding(24.dp)) {
            item { Section("Template karakter", "Setiap sesi menerima salinan independen.") }
            items(templates, key = { it.id }) { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { templateSheet = false; vm.newSession(ChatMode.NORMAL, KaiJson.decodeFromString(row.specJson)) }, modifier = Modifier.weight(1f)) { Text(row.name) }
                    TextButton(onClick = { vm.launch { vm.repo.dao.deleteTemplate(row.id) } }) { Text("Hapus") }
                }
            }
        }
    }
}

@Composable fun SessionEditorScreen(vm: KaiViewModel) {
    val raw by vm.editorJson.collectAsStateWithLifecycle()
    val spec = remember(raw) { raw.takeIf { it.isNotBlank() }?.let { KaiJson.decodeFromString<SessionSpec>(it) } } ?: return
    val voices by vm.voices.collectAsStateWithLifecycle()
    val busy by vm.graph.chat.busy.collectAsStateWithLifecycle()
    val updating = busy.contains(spec.id)
    fun updateCharacter(value: CharacterSpec) = vm.setEditor(spec.copy(character = value))
    fun updateScenario(value: Scenario) = vm.setEditor(spec.copy(scenario = value))
    Page(if (spec.mode == ChatMode.ROLEPLAY) "Ruang cerita" else "Karakter & sesi", vm::back,
        action = { TextButton(onClick = vm::saveEditor, enabled = !updating) { Text("Simpan") } }) {
        Field("Nama sesi", spec.title, { vm.setEditor(spec.copy(title = it)) }, max = 200)
        ModelField(vm, if (spec.mode == ChatMode.ROLEPLAY) "Model roleplay" else "Model chat", spec.model, "text") { vm.setEditor(spec.copy(model = it.name)) }
        if (updating) Hint("Respons sedang dibuat. Hentikan dari percakapan sebelum menyimpan perubahan.", true)
        Section(if (spec.mode == ChatMode.ROLEPLAY) "Narator" else "Identitas karakter", "Setelan karakter dan suara ini milik sesi ini. Template dan sesi lain tidak ikut berubah.")
        Field("Nama", spec.character.name, { updateCharacter(spec.character.copy(name = it)) }, max = 80)
        Field("Deskripsi karakter", spec.character.description, { updateCharacter(spec.character.copy(description = it)) }, 3)
        Field("Latar belakang", spec.character.backstory, { updateCharacter(spec.character.copy(backstory = it)) }, 3)
        Field("Aturan perilaku", spec.character.rules, { updateCharacter(spec.character.copy(rules = it)) }, 3)
        OutlinedButton(onClick = { vm.openPersonality() }, modifier = Modifier.fillMaxWidth()) { Text("Buka Personality Studio") }
        VoiceAssignment(spec.character.voice, voices, { updateCharacter(spec.character.copy(voice = it.independentCopy())) }, { vm.openVoice(spec.character.voice, -1) })
        if (spec.mode == ChatMode.ROLEPLAY) {
            val scenario = spec.scenario
            Section("Dunia & skenario")
            Field("Judul roleplay", scenario.title, { updateScenario(scenario.copy(title = it)) })
            Field("Dunia dan latar", scenario.world, { updateScenario(scenario.copy(world = it)) }, 3)
            Field("Genre", scenario.genre, { updateScenario(scenario.copy(genre = it)) })
            Field("Skenario awal", scenario.opening, { updateScenario(scenario.copy(opening = it)) }, 4)
            Field("Peran kamu", scenario.userRole, { updateScenario(scenario.copy(userRole = it)) }, 2)
            Field("Hubungan karakter", scenario.relationships, { updateScenario(scenario.copy(relationships = it)) }, 3)
            Field("Aturan cerita", scenario.rules, { updateScenario(scenario.copy(rules = it)) }, 3)
            Field("Gaya narasi", scenario.narrativeStyle, { updateScenario(scenario.copy(narrativeStyle = it)) }, 2)
            Field("Format dialog", scenario.dialogueFormat, { updateScenario(scenario.copy(dialogueFormat = it)) })
            Section("Karakter dalam cerita", "Nama unik menjaga identitas dialog dan pemetaan suara. Kamu tetap mengendalikan peranmu sendiri.")
            scenario.actors.forEachIndexed { index, actor ->
                key(actor.id) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            fun change(value: CharacterSpec) = updateScenario(scenario.copy(actors = scenario.actors.mapIndexed { i, a -> if (i == index) value else a }))
                            Field("Nama karakter ${index + 1}", actor.name, { change(actor.copy(name = it)) }, max = 80)
                            Field("Deskripsi & motivasi", actor.description, { change(actor.copy(description = it)) }, 2)
                            Field("Latar belakang", actor.backstory, { change(actor.copy(backstory = it)) }, 2)
                            Field("Aturan perilaku", actor.rules, { change(actor.copy(rules = it)) }, 2)
                            TextButton(onClick = { vm.openPersonality(index) }) { Text("Kepribadian ${actor.name.ifBlank { "karakter" }}") }
                            VoiceAssignment(actor.voice, voices, { change(actor.copy(voice = it.independentCopy())) }, { vm.openVoice(actor.voice, index) })
                            TextButton(onClick = { updateScenario(scenario.copy(actors = scenario.actors.filterNot { it.id == actor.id }, selectedSpeakerId = scenario.selectedSpeakerId.takeUnless { it == actor.id }.orEmpty())) }) { Text("Hapus karakter", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { updateScenario(scenario.copy(actors = scenario.actors + CharacterSpec(voice = VoiceProfile(model = vm.prefs.value.ttsModel)))) }, modifier = Modifier.fillMaxWidth()) { Text("Tambah karakter cerita") }
            Section("Bagian cerita yang dibacakan")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(VoiceScope.DIALOGUE to "Dialog saja", VoiceScope.ALL to "Narasi + dialog", VoiceScope.CHARACTER to "Satu karakter").forEach { (value, label) ->
                    FilterChip(scenario.voiceScope == value, { updateScenario(scenario.copy(voiceScope = value)) }, label = { Text(label) })
                }
            }
            if (scenario.voiceScope == VoiceScope.CHARACTER) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scenario.actors.forEach { actor -> FilterChip(scenario.selectedSpeakerId == actor.id, { updateScenario(scenario.copy(selectedSpeakerId = actor.id)) }, label = { Text(actor.name) }) }
            }
            Hint("Dua pembicara dengan model Interactions yang sama dan didukung dapat dibuat dalam satu audio. Kombinasi lain dibuat per potongan dengan suara masing-masing, lalu diputar berurutan.")
        }
        Section("Voice note")
        ToggleRow("VN otomatis", "Setelah respons teks selesai, buat audio memakai kuota TTS.", spec.autoVoice) { vm.setEditor(spec.copy(autoVoice = it)) }
        ToggleRow("Putar VN otomatis", "Pemutaran otomatis terpisah dari pembuatan audio.", spec.autoPlay) { vm.setEditor(spec.copy(autoPlay = it)) }
        OperationBanner(vm)
        Button(onClick = vm::saveEditor, enabled = !updating, modifier = Modifier.fillMaxWidth()) { Text("Simpan sesi") }
        TextButton(onClick = vm::saveTemplate) { Text("Simpan karakter utama sebagai template") }
    }
}

@Composable fun VoiceAssignment(current: VoiceProfile, profiles: List<VoiceProfile>, onSelect: (VoiceProfile) -> Unit, onEdit: () -> Unit) {
    var choose by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Suara · ${current.name} / ${current.voiceId}", style = MaterialTheme.typography.bodyMedium)
        Hint(current.model.removePrefix("models/").ifBlank { "Model TTS belum dipilih" })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) { Text("Voice Studio") }
            if (profiles.isNotEmpty()) TextButton(onClick = { choose = true }) { Text("Pakai profil") }
        }
    }
    if (choose) ModalBottomSheet(onDismissRequest = { choose = false }) {
        LazyColumn(Modifier.padding(24.dp)) {
            item { Section("Salin profil suara", "Perubahan pada profil asli tidak mengubah salinan sesi.") }
            items(profiles, key = { it.id }) { profile -> TextButton(onClick = { onSelect(profile); choose = false }, modifier = Modifier.fillMaxWidth()) { Text("${profile.name} · ${profile.voiceId}") } }
        }
    }
}

@Composable fun PersonalityScreen(vm: KaiViewModel) {
    val raw by vm.editorJson.collectAsStateWithLifecycle()
    val character = remember(raw) { vm.activeCharacter() } ?: return
    val saved by vm.personalities.collectAsStateWithLifecycle()
    var saveDialog by remember { mutableStateOf(false) }
    Page("Personality Studio", vm::back, action = { TextButton(onClick = vm::back) { Text("Selesai") } }) {
        Text(character.name.ifBlank { "Karakter baru" }, style = MaterialTheme.typography.headlineLarge)
        Hint("Mengatur cara karakter berpikir dan berbicara melalui teks. Delivery vokal diatur terpisah di Voice Studio.")
        Section("Mulai dari preset")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioTraits.personalityPresets.forEach { (name, preset) -> AssistChip(onClick = { vm.updateActiveCharacter(character.copy(personality = preset)) }, label = { Text(name) }) }
        }
        if (saved.isNotEmpty()) {
            Section("Preset tersimpan")
            saved.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.updateActiveCharacter(character.copy(personality = KaiJson.decodeFromString(item.specJson))) }, modifier = Modifier.weight(1f)) { Text(item.name) }
                TextButton(onClick = { vm.launch { vm.repo.dao.deletePersonality(item.id) } }) { Text("Hapus") }
            } }
        }
        Section("Karakter pilihanmu", "Angka adalah kontrol aplikasi. Setelan diterjemahkan menjadi instruksi perilaku yang ringkas dan sesuai konteks.")
        StudioTraits.personality.forEach { trait -> TraitSlider(trait, character.personality.value(trait.key)) { vm.updateActiveCharacter(character.copy(personality = character.personality.with(trait.key, it))) } }
        Field("Deskripsi tambahan", character.description, { vm.updateActiveCharacter(character.copy(description = it)) }, 3)
        Field("Aturan perilaku", character.rules, { vm.updateActiveCharacter(character.copy(rules = it)) }, 3)
        OutlinedButton(onClick = { saveDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Simpan sebagai preset baru") }
        Hint("Setelan persona tidak mengubah kebijakan keselamatan penyedia atau aturan keamanan aplikasi.")
    }
    if (saveDialog) TextInputDialog("Nama preset", onDismiss = { saveDialog = false }) { vm.savePersonality(it) }
}
