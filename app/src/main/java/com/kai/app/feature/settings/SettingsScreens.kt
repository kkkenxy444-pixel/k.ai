@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.kai.app.feature.settings

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.app.ui.*
import com.kai.core.model.*

@Composable fun OnboardingScreen(vm: KaiViewModel) {
    Page("") {
        Spacer(Modifier.height(36.dp))
        Text("k.ai", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(28.dp))
        Text("Ruang untuk\ncerita kamu.", style = MaterialTheme.typography.headlineLarge)
        Hint("Buat pendamping dengan kepribadian dan suara pilihanmu. Setiap percakapan punya ruang dan ingatannya sendiri.")
        Section("Milikmu, di perangkatmu", "Karakter dan riwayat disimpan lokal. Tidak perlu akun k.ai.")
        Section("Gunakan key Gemini pribadi", "Teks percakapan, konteks, dan teks suara dikirim ke Google saat kamu meminta respons. Kuota dan ketentuan data mengikuti proyek Gemini milikmu.")
        Section("Kamu mengetik. AI bisa bersuara.", "Tidak ada mikrofon, rekaman suara pengguna, atau panggilan. Voice note AI dibuat sesuai permintaanmu.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = vm::completeOnboarding, modifier = Modifier.fillMaxWidth()) { Text("Mulai") }
    }
}

@Composable fun ApiSettingsScreen(vm: KaiViewModel) {
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val recovery by vm.keyRecovery.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") } // Never saved into activity state.
    var delete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Page("Gemini API", vm::back) {
        Section(if (hasKey) "Key pribadi tersimpan" else "Hubungkan Gemini", "Key dienkripsi dengan Android Keystore. Mengganti key tidak menghapus percakapan.")
        if (recovery) Hint("Kredensial lama tidak dapat dibuka oleh Keystore. Masukkan key lagi; data percakapan tetap utuh.", true)
        OutlinedTextField(key, { key = it.take(512) }, label = { Text(if (hasKey) "API key pengganti" else "API key pribadi") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        Hint("Tempel key lengkap dari AI Studio. Format baru AQ. dan format lama AIza didukung. Jika penyimpanan gagal, teks tetap ada untuk diperbaiki.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.saveKey(key) }, enabled = key.isNotBlank() && !operation.busy) { Text("Simpan key") }
            OutlinedButton(onClick = { vm.testKey(key) }, enabled = (key.isNotBlank() || hasKey) && !operation.busy) { Text("Uji koneksi") }
        }
        OperationBanner(vm)
        Button(onClick = { vm.navigate("models") }, enabled = hasKey, modifier = Modifier.fillMaxWidth()) { Text("Pilih model Gemini untuk Chat, Roleplay & VN") }
        OutlinedButton(onClick = vm::refreshModels, enabled = hasKey && !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("Segarkan model") }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://aistudio.google.com/apikey".toUri())) }) { Text("Buat key di Google AI Studio") }
        Hint("k.ai tidak mengaktifkan billing. Katalog model tidak menyatakan hak Free Tier atau sisa kuota. Jika proyekmu sudah memakai billing, permintaan dapat dikenai biaya sesuai pengaturan Google.")
        Hint("Mengganti key dari proyek Google yang sama tidak mereset kuota. Untuk membagikan APK, setiap orang memasukkan key miliknya sendiri.")
        if (hasKey || recovery) TextButton(onClick = { delete = true }) { Text("Hapus key dari perangkat", color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { vm.navigate("home") }) { Text("Ke percakapan") }
    }
    if (delete) ConfirmDialog("Hapus API key?", "Chat online berhenti sampai kamu menyimpan key baru. Karakter, memori, dan percakapan tetap tersimpan.", { delete = false }) { vm.deleteKey() }
}

@Composable fun SettingsScreen(vm: KaiViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    var clear by remember { mutableStateOf(false) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    Page("Pengaturan") {
        Section("Koneksi", if (hasKey) "Gemini terhubung dengan key tersimpan" else "API key belum ditambahkan")
        OutlinedButton(onClick = { vm.navigate("api") }, modifier = Modifier.fillMaxWidth()) { Text("Kelola Gemini API") }
        OutlinedButton(onClick = { vm.navigate("models") }, modifier = Modifier.fillMaxWidth()) { Text("Model chat, roleplay, dan TTS") }
        Section("Tampilan")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark" to "Gelap", "light" to "Terang", "system" to "Sistem").forEach { (id, label) -> FilterChip(prefs.theme == id, { vm.preference("theme", id) }, label = { Text(label) }) }
        }
        Section("Percakapan")
        ToggleRow("Respons bertahap", "Tampilkan teks saat dibuat. Matikan jika model tidak mendukung streaming.", prefs.stream) { vm.preference("stream", it) }
        ToggleRow("Ringkas konteks otomatis", "Setelah percakapan panjang, gunakan permintaan tambahan ke model yang sama untuk merangkum. Memakai kuota API.", prefs.autoSummarize) { vm.preference("autoSummarize", it) }
        Section("Data lokal", "Ekspor per sesi tersedia di menu percakapan. Berkas berisi teks pribadi; simpan di tempat aman. API key dan audio tidak diekspor.")
        OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Impor sesi (.json)") }
        TextButton(onClick = { clear = true }) { Text("Bersihkan semua cache audio") }
        Section("Tentang k.ai", "Android native · Kotlin & Compose\nVersi ${com.kai.app.BuildConfig.VERSION_NAME}")
        Hint("Tidak ada mikrofon atau akun k.ai. Data aplikasi tidak ikut backup Android otomatis. Ekspor sesi penting sebelum menghapus atau mengganti perangkat.")
    }
    if (clear) ConfirmDialog("Hapus cache audio?", "Semua VN perlu dibuat ulang. Percakapan dan profil suara tetap tersimpan.", { clear = false }) { vm.clearAudio() }
}

@Composable fun ModelsScreen(vm: KaiViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    LaunchedEffect(hasKey) {
        if (hasKey && vm.models.value.isEmpty()) vm.refreshModels()
    }
    Page("Pilihan model", vm::back) {
        Hint("Pilih dari daftar model Gemini yang tersedia untuk API key milikmu. Tidak perlu mengetik identifier model.")
        if (!hasKey) OutlinedButton(onClick = { vm.navigate("api") }) { Text("Masukkan API key terlebih dahulu") }
        if (hasKey && models.isEmpty()) Hint("Katalog masih kosong. Model akan dimuat otomatis, atau tekan Segarkan katalog.")
        Hint("Pilihan ini menjadi default untuk sesi baru. Sesi lama mempertahankan modelnya.")
        ModelField(vm, "Normal Chat", prefs.normalModel, "text") { vm.preference("normalModel", it.name) }
        ModelField(vm, "Roleplay", prefs.roleplayModel, "text") { vm.preference("roleplayModel", it.name) }
        ModelField(vm, "Voice / TTS", prefs.ttsModel, "tts") { vm.preference("ttsModel", it.name) }
        OperationBanner(vm)
        Button(onClick = vm::refreshModels) { Text("Segarkan katalog Gemini") }
        Hint("Gratis / harga / entitlement: belum dapat dipastikan. Models API tidak menyediakan informasi billing atau jaminan akses akun. k.ai tidak mengganti model secara diam-diam.")
    }
}

@Composable fun ModelField(vm: KaiViewModel, label: String, selected: String, kind: String, onSelect: (GeminiModel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.removePrefix("models/").ifBlank { "Pilih model" }) }
    }
    if (open) ModelPicker(vm, kind, { open = false }) { onSelect(it); open = false }
}

@Composable fun ModelPicker(vm: KaiViewModel, kind: String, onDismiss: () -> Unit, onSelect: (GeminiModel) -> Unit) {
    val models by vm.models.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var all by rememberSaveable { mutableStateOf(false) }
    val filtered = models.filter { (all || if (kind == "tts") it.ttsHint else it.textCandidate) && "${it.name} ${it.displayName} ${it.description}".contains(query, true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Katalog model", style = MaterialTheme.typography.titleLarge)
            Field("Cari nama atau identifier", query, { query = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FilterChip(all, { all = !all }, label = { Text("Tampilkan semua") })
                TextButton(onClick = vm::refreshModels, enabled = !operation.busy) { Text("Segarkan") }
            }
            Hint(if (kind == "tts") "Filter TTS memakai petunjuk metadata, bukan jaminan dukungan. Uji lewat pratinjau suara." else "Filter menggunakan metode generasi dan petunjuk metadata. Dukungan aktual divalidasi saat permintaan.")
            if (operation.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (operation.message.isNotBlank()) Hint(operation.message, operation.error)
            if (filtered.isEmpty()) Hint("Belum ada model yang sesuai. Tambahkan key, segarkan katalog, atau tampilkan semua.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.name }) { model ->
                    OutlinedCard(onClick = { onSelect(model) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(model.displayName.ifBlank { model.id }, style = MaterialTheme.typography.titleMedium)
                            Text(model.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (model.description.isNotBlank()) Text(model.description, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                            Hint(model.supportedGenerationMethods.joinToString(" · ").ifBlank { "Kemampuan belum dilaporkan" })
                            Text("Harga / Free Tier: belum terkonfirmasi", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
