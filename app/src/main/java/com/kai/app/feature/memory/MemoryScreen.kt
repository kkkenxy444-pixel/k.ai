@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.kai.app.feature.memory

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.app.ui.*

@Composable fun MemoryScreen(vm: KaiViewModel) {
    val id by vm.selectedId.collectAsStateWithLifecycle()
    val record by remember(id) { vm.repo.dao.watchSession(id) }.collectAsStateWithLifecycle(null)
    val busy by vm.graph.chat.busy.collectAsStateWithLifecycle()
    var note by rememberSaveable { mutableStateOf("") }
    var clear by remember { mutableStateOf(false) }
    val memory = record?.memory() ?: return
    Page("Memori percakapan", vm::back) {
        Hint("Memori ini hanya dipakai oleh sesi ini. Ringkasan membantu menghemat konteks, tetapi tidak menjamin ingatan sempurna. Percakapan asli tetap disimpan.")
        Section("Ringkasan", if (memory.throughOrdinal >= 0) "Mencakup pesan sampai urutan ${memory.throughOrdinal + 1}." else "Belum ada ringkasan.")
        if (memory.summary.isNotBlank()) Text(memory.summary, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.graph.chat.summarize(id) }, enabled = id !in busy) { Text("Ringkas sekarang") }
            if (memory.summary.isNotBlank()) TextButton(onClick = { vm.clearSummary(id) }, enabled = id !in busy) { Text("Hapus ringkasan") }
        }
        if (id in busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Hint("Meringkas memakai model sesi dan kuota API. Pesan terbaru selalu dipertahankan sebagai konteks langsung.")
        Section("Catatan penting", "Simpan hubungan, keputusan, peristiwa, atau detail adegan yang tidak boleh terlupakan.")
        memory.events.forEach { event ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(event.text, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { vm.removeMemory(id, event.id) }) { Text("Hapus catatan") }
                }
            }
        }
        Field("Catatan baru", note, { note = it }, 3, max = 4000)
        Button(onClick = { vm.pinMemory(id, note); note = "" }, enabled = note.isNotBlank()) { Text("Simpan catatan") }
        Section("Hapus data sesi")
        TextButton(onClick = { clear = true }) { Text("Hapus semua pesan & memori", color = MaterialTheme.colorScheme.error) }
    }
    if (clear) ConfirmDialog("Kosongkan percakapan?", "Semua pesan dan memori sesi ini akan dihapus. Konfigurasi karakter tetap tersedia.", { clear = false }) { vm.clearChat(id) }
}
