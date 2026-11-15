@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.kai.app.feature.voice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.app.feature.settings.ModelField
import com.kai.app.ui.*
import com.kai.core.model.*
import com.kai.core.ai.TtsCapabilities
import java.io.File

@Composable fun VoiceLibraryScreen(vm: KaiViewModel) {
    val profiles by vm.voices.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<String?>(null) }
    Page("Voice Studio", action = { TextButton(onClick = { vm.openVoice(VoiceProfile(name = "Suara baru", model = vm.prefs.value.ttsModel)) }) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text("Baru")
    } }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SUARA MILIKMU", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
                    Text("Bukan sekadar suara.", style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Atur karakter, intonasi, dan cara AI berbicara.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f))
                }
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Section("Coba karakter suara", "Pilih preset sebagai titik awal, lalu geser tingkat ekspresinya sendiri.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            StudioTraits.voicePresets.forEach { (name, values) ->
                SuggestionChip(onClick = { vm.openVoice(VoiceProfile(name = name, model = vm.prefs.value.ttsModel, values = values)) }, label = { Text(name) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Profil tersimpan", style = MaterialTheme.typography.titleLarge)
            Text("${profiles.size} suara", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (profiles.isEmpty()) KaiPanel(Modifier.fillMaxWidth()) {
            Text("Belum ada profil", style = MaterialTheme.typography.titleMedium)
            Hint("Buat suara pertama dari preset di atas, atau mulai dari profil kosong.")
            TextButton(onClick = { vm.openVoice(VoiceProfile(name = "Suara baru", model = vm.prefs.value.ttsModel)) }) { Text("Buat suara") }
        }
        profiles.forEach { profile ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text("${profile.voiceId}  ·  ${profile.model.removePrefix("models/").ifBlank { "Model belum dipilih" }}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FilledTonalButton(onClick = { vm.openVoice(profile) }) { Text("Edit suara") }
                        TextButton(onClick = { vm.duplicateVoice(profile) }) { Text("Salin") }
                        TextButton(onClick = { deleting = profile.id }) { Text("Hapus") }
                    }
                }
            }
        }
        Hint("Setiap sesi memakai salinan profil. Mengubah preset tidak mengubah suara sesi yang sudah ada.")
    }

    deleting?.let { id -> ConfirmDialog("Hapus preset suara?", "Salinan suara dalam sesi tetap tersedia. Suara custom yang dibuat di proyek Google tidak dihapus oleh tindakan ini.", { deleting = null }) { vm.deleteVoice(id) } }
}

@Composable fun VoiceStudioScreen(vm: KaiViewModel) {
    val raw by vm.voiceJson.collectAsStateWithLifecycle()
    val profile = remember(raw) { vm.voice() }
    val catalog by vm.catalogVoices.collectAsStateWithLifecycle()
    val checked by vm.voiceCatalogChecked.collectAsStateWithLifecycle()
    val busy by vm.voiceBusy.collectAsStateWithLifecycle()
    val error by vm.voiceError.collectAsStateWithLifecycle()
    val previews by vm.previews.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var testSentence by rememberSaveable { mutableStateOf("Aku di sini. Ceritakan bagaimana harimu, pelan-pelan saja, aku mendengarkan.") }
    var designDescription by rememberSaveable { mutableStateOf("") }
    var createConfirm by remember { mutableStateOf(false) }
    // Protocol capability registry, not a model-selection list. Only officially documented design families.
    val designSupported = TtsCapabilities.supportsVoiceDesign(profile.model)
    Page("Rancang suara", vm::back, action = { TextButton(onClick = vm::saveVoice) { Text("Simpan") } }) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Voice Personality", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Dari lembut dan manja sampai tegas dan ceria. Atur, dengarkan, simpan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .82f))
                }
            }
        }
        Field("Nama profil", profile.name, { vm.setVoice(profile.copy(name = it)) }, max = 100)
        ModelField(vm, "Model Voice / TTS", profile.model, "tts") { model ->
            vm.setVoice(profile.copy(model = model.name,
                transport = if (model.id.startsWith("gemini-3.8-") && model.ttsHint) TtsTransport.INTERACTIONS else profile.transport))
        }
        Hint("Harga dan akses Free Tier belum terkonfirmasi. Pratinjau dan VN memakai kuota model yang dipilih.")
        Section("Protokol TTS", "Gunakan endpoint yang didukung model. Pilihan ini tidak mengubah model atau mengaktifkan layanan berbayar.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(profile.transport == TtsTransport.GENERATE_CONTENT, { vm.setVoice(profile.copy(transport = TtsTransport.GENERATE_CONTENT)) }, label = { Text("GenerateContent") })
            FilterChip(profile.transport == TtsTransport.INTERACTIONS, { vm.setVoice(profile.copy(transport = TtsTransport.INTERACTIONS)) }, label = { Text("Interactions") })
        }
        Section("Identitas vokal", "Identitas suara berbeda dari kepribadian percakapan dan arahan delivery.")
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text("Suara: ${profile.voiceId}") }
        if (profile.transport == TtsTransport.INTERACTIONS) {
            Field("ID suara tersimpan", profile.voiceId, { vm.setVoice(profile.copy(voiceId = it.trim())) }, hint = "Gunakan ID asli dari katalog atau AI Studio. Dukungan akan divalidasi saat preview.", max = 500)
            OutlinedButton(onClick = vm::refreshVoices, modifier = Modifier.fillMaxWidth()) { Text("Periksa akses & muat katalog suara") }
        }
        OperationBanner(vm)
        Section("Preset delivery")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioTraits.voicePresets.forEach { (name, values) -> AssistChip(onClick = { vm.setVoice(profile.copy(values = values)) }, label = { Text(name) }) }
        }
        Section("Karakter saat berbicara", "Geser untuk menyesuaikan gaya pengucapan. Kamu bisa membuatnya manja, clingy, atau tegas.")
        KaiPanel(Modifier.fillMaxWidth()) {
            StudioTraits.voice.forEach { trait ->
                TraitSlider(trait, profile.value(trait.key)) { vm.setVoice(profile.with(trait.key, it)) }
            }
        }
        Hint("Skala 0–100 diterjemahkan menjadi arahan TTS. Ini bukan pengendali akustik numerik yang dijamin Gemini.")
        Field("Arahan delivery tambahan", profile.description, { vm.setVoice(profile.copy(description = it)) }, 3,
            "Contoh: Bahasa Indonesia natural, nada lembut dan jelas. Jangan meminta peniruan orang nyata tanpa izin.", max = 700)
        Section("Dengarkan hasilnya", "Coba kalimat yang sama untuk membandingkan dua konfigurasi terakhir. Pembuatan audio memakai kuota Gemini.")
        Field("Kalimat uji", testSentence, { testSentence = it }, 3, max = 3000)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.preview(testSentence) }, enabled = !busy && testSentence.isNotBlank() && profile.model.isNotBlank()) { Text(if (busy) "Membuat audio…" else "Buat pratinjau") }
            if (busy) TextButton(onClick = vm::cancelPreview) { Text("Batalkan") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Hint(error, true)
        previews.forEach { preview ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pratinjau ${preview.label} · ${preview.profile.voiceId}", style = MaterialTheme.typography.titleMedium)
                    Hint(preview.profile.model.removePrefix("models/"))
                    Text(preview.sentence, style = MaterialTheme.typography.bodySmall)
                    Hint(preview.profile.values.entries.joinToString(" · ") { "${StudioTraits.voice.firstOrNull { t -> t.key == it.key }?.label ?: it.key} ${it.value}" }.ifBlank { "Delivery natural" })
                    AudioPlayerBar(vm, "preview-${preview.file.name}", listOf(preview.file))
                    TextButton(onClick = { vm.setVoice(preview.profile.copy(id = profile.id, name = profile.name)) }) { Text("Pakai konfigurasi ${preview.label}") }
                }
            }
        }
        Hint("Kontrol kecepatan pada pemutar adalah perubahan saat playback. Kontrol itu terpisah dari identitas dan delivery yang dikirim ke Gemini.")
        if (profile.transport == TtsTransport.INTERACTIONS && designSupported) {
            Section("Buat identitas dari deskripsi", "Tersedia hanya setelah katalog berhasil diakses. Server tetap memeriksa entitlement; dukungan katalog tidak menjamin kuota pembuatan suara.")
            Field("Deskripsi identitas suara", designDescription, { designDescription = it }, 4,
                "Deskripsikan timbre, aksen, dan karakter suara fiktif. Suara akan disimpan pada proyek Google kamu.", max = 2000)
            OutlinedButton(onClick = { createConfirm = true }, enabled = checked && designDescription.isNotBlank() && profile.name.isNotBlank()) { Text("Buat suara custom di Gemini") }
        } else Hint("Pembuatan identitas suara dari deskripsi belum dikonfirmasi untuk model/protokol ini. Preset delivery tetap dapat dipakai dengan suara bawaan yang didukung.")
        Button(onClick = vm::saveVoice, modifier = Modifier.fillMaxWidth()) { Text("Simpan profil suara") }
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }) {
        var search by remember { mutableStateOf("") }
        val all = (StudioTraits.builtInVoices.map { CatalogVoice(it, it, "Suara bawaan terdokumentasi; akses divalidasi saat preview.") } +
            if (profile.transport == TtsTransport.INTERACTIONS) catalog else emptyList()).distinctBy { it.id }
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pilih identitas suara", style = MaterialTheme.typography.titleLarge)
            Field("Cari suara", search, { search = it })
            LazyColumn(Modifier.weight(1f)) {
                items(all.filter { "${it.id} ${it.displayName} ${it.description}".contains(search, true) }, key = { it.id }) { voice ->
                    TextButton(onClick = { vm.setVoice(profile.copy(voiceId = voice.id)); picker = false }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(voice.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(voice.id, style = MaterialTheme.typography.bodySmall)
                            if (voice.description.isNotBlank()) Text(voice.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
    if (createConfirm) ConfirmDialog("Buat suara di proyek Google?", "Deskripsi dikirim ke Gemini dan menghasilkan resource suara tersimpan. Permintaan memakai kuota akunmu. Billing tidak diubah. Hapus resource Google melalui AI Studio jika tidak diperlukan.", { createConfirm = false }, "Buat suara") { vm.designVoice(designDescription) }
}

@Composable fun AudioPlayerBar(vm: KaiViewModel, owner: String, files: List<File>, speakers: List<String> = emptyList()) {
    val state by vm.graph.player.state.collectAsStateWithLifecycle()
    val selected = state.owner == owner
    var speedMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            FilledTonalButton(onClick = { vm.graph.player.play(owner, files) }) { Text(if (selected && state.playing) "Jeda" else "Putar") }
            if (selected) {
                TextButton(onClick = vm.graph.player::stop) { Text("Stop") }
                TextButton(onClick = vm.graph.player::replay) { Text("Ulang") }
            }
            Box {
                TextButton(onClick = { speedMenu = true }) { Text("${state.speed}×") }
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value -> DropdownMenuItem(text = { Text("$value×") }, onClick = { vm.graph.player.speed(value); speedMenu = false }) }
                }
            }
        }
        if (selected) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Slider(state.position.toFloat().coerceAtMost(state.duration.toFloat().coerceAtLeast(1f)), { vm.graph.player.seek(it.toLong()) }, valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f), enabled = state.duration > 0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${time(state.position)} / ${time(state.duration)}", style = MaterialTheme.typography.labelSmall)
                if (files.size > 1) Text("${state.index + 1}/${files.size} · ${speakers.getOrNull(state.index).orEmpty()}", style = MaterialTheme.typography.labelSmall)
            }
            if (files.size > 1) Row {
                TextButton(onClick = vm.graph.player::previous) { Text("Sebelumnya") }
                TextButton(onClick = vm.graph.player::next, enabled = state.index < files.lastIndex) { Text("Berikutnya") }
            }
            if (state.error.isNotBlank()) Hint(state.error, true)
        } else if (files.size > 1) Hint("${files.size} potongan audio · suara karakter diputar berurutan")
    }
}

private fun time(milliseconds: Long): String = "%d:%02d".format(milliseconds / 60_000, (milliseconds / 1000) % 60)
