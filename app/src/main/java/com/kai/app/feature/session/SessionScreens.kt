@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.kai.app.feature.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.app.feature.settings.ModelField
import com.kai.app.ui.*
import com.kai.core.model.*

@Composable fun HomeScreen(vm: KaiViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Semua") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var templateSheet by remember { mutableStateOf(false) }
    val visible = sessions.filter { row ->
        val spec = row.spec()
        "${row.session.title} ${spec.character.name} ${spec.scenario.title}".contains(query, true) &&
            (filter == "Semua" || (filter == "Chat" && spec.mode == ChatMode.NORMAL) || (filter == "Roleplay" && spec.mode == ChatMode.ROLEPLAY))
    }
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primary) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Text("k", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Text("k.ai", style = MaterialTheme.typography.titleLarge)
                }
            },
            actions = {
                TextButton(onClick = { vm.navigate("models") }, enabled = hasKey) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp)); Text("Model")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("RUANG PERSONAL", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = 1.7.sp)
                    Text("Setiap karakter,\npunya ceritanya.", style = MaterialTheme.typography.headlineLarge)
                    Text("Buat teman ngobrol atau mulai dunia cerita baru. Semua tersimpan di perangkatmu.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(27.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("MULAI DARI SINI", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .76f))
                            Text("Siapa yang ingin kamu temui?", style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Atur nama, sifat, dan suaranya sendiri.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f))
                        }
                        Icon(Icons.Default.Home, contentDescription = null,
                            modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KaiStartTile("Chat baru", "Karakter personal", false, Modifier.weight(1f)) { vm.newSession(ChatMode.NORMAL) }
                    KaiStartTile("Roleplay", "Buat alur cerita", true, Modifier.weight(1f)) { vm.newSession(ChatMode.ROLEPLAY) }
                }
            }
            if (!hasKey) item {
                KaiPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Hubungkan Gemini", style = MaterialTheme.typography.titleMedium)
                            Hint("Tambahkan API key gratis milikmu untuk mulai berkirim pesan.")
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Button(onClick = { vm.navigate("api") }, modifier = Modifier.fillMaxWidth()) { Text("Atur API key") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Percakapan", style = MaterialTheme.typography.titleLarge)
                        Text("${sessions.size} sesi tersimpan", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (templates.isNotEmpty()) TextButton(onClick = { templateSheet = true }) { Text("Template") }
                }
            }
            if (sessions.isNotEmpty()) item {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("Cari karakter atau cerita") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) })
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Semua", "Chat", "Roleplay").forEach { label ->
                        FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) },
                            shape = RoundedCornerShape(50))
                    }
                }
            }
            items(visible, key = { it.session.id }) { row ->
                val spec = row.spec()
                var menu by remember { mutableStateOf(false) }
                Surface(onClick = { vm.openSession(spec.id) }, shape = RoundedCornerShape(23.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        KaiMonogram(spec.character.name.ifBlank { spec.title }, Modifier.size(55.dp), spec.mode == ChatMode.ROLEPLAY)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(spec.title.ifBlank { spec.character.name.ifBlank { "Percakapan baru" } },
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (spec.mode == ChatMode.ROLEPLAY) "CERITA  ·  ${spec.scenario.genre.ifBlank { "Roleplay" }}" else "CHAT  ·  ${spec.character.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu sesi") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Edit karakter & model") }, onClick = { menu = false; vm.editSession(spec.id) })
                                DropdownMenuItem(text = { Text("Duplikat sesi") }, onClick = { menu = false; vm.duplicate(spec.id) })
                                DropdownMenuItem(text = { Text("Jadikan template") }, onClick = { menu = false; vm.launch { vm.repo.template(spec); vm.notify("Template tersimpan.") } })
                                DropdownMenuItem(text = { Text("Hapus sesi") }, onClick = { menu = false; deleting = spec.id })
                            }
                        }
                    }
                }
            }
            if (visible.isEmpty()) item {
                KaiPanel(Modifier.fillMaxWidth()) {
                    Text(if (sessions.isEmpty()) "Belum ada cerita di sini" else "Tidak ada yang cocok", style = MaterialTheme.typography.titleMedium)
                    Hint(if (sessions.isEmpty()) "Mulai dari Chat baru atau Roleplay. Karaktermu nanti akan muncul di halaman ini." else "Coba gunakan nama atau filter lain.")
                    if (sessions.isEmpty()) TextButton(onClick = { vm.newSession(ChatMode.NORMAL) }) { Text("Buat karakter pertama") }
                }
            }
        }
    }
    deleting?.let { id -> ConfirmDialog("Hapus sesi?", "Percakapan, karakter, memori, dan VN sesi ini akan dihapus.", { deleting = null }) { vm.deleteSession(id) } }
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

@Composable private fun KaiStartTile(title: String, subtitle: String, roleplay: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp),
        color = if (roleplay) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(if (roleplay) Icons.Default.PlayArrow else Icons.Default.Home,
                contentDescription = null, modifier = Modifier.size(25.dp),
                tint = if (roleplay) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (roleplay) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = if (roleplay) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f) else MaterialTheme.colorScheme.onSurfaceVariant)
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
