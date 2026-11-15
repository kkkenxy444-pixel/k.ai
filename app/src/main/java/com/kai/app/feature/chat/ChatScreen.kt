@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.kai.app.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.app.feature.voice.AudioPlayerBar
import com.kai.app.ui.*
import com.kai.core.ai.*
import com.kai.core.model.*
import kotlinx.coroutines.launch

@Composable fun ChatScreen(vm: KaiViewModel) {
    val id by vm.selectedId.collectAsStateWithLifecycle()
    val record by remember(id) { vm.repo.dao.watchSession(id) }.collectAsStateWithLifecycle(null)
    var visibleCount by rememberSaveable(id) { mutableIntStateOf(100) }
    val messages by remember(id, visibleCount) { vm.repo.recentMessages(id, visibleCount) }.collectAsStateWithLifecycle(emptyList())
    val messageCount by remember(id) { vm.repo.dao.watchMessageCount(id) }.collectAsStateWithLifecycle(0)
    val active by vm.graph.chat.busy.collectAsStateWithLifecycle()
    val busy = id in active
    val current = record ?: return
    val spec = current.spec()
    var draft by rememberSaveable(id) { mutableStateOf(current.session.draft) }
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<ChatMessage?>(null) }
    var regenerate by remember { mutableStateOf<ChatMessage?>(null) }
    var deleting by remember { mutableStateOf<ChatMessage?>(null) }
    var deleteSession by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scroll = rememberLazyListState()
    val coroutine = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.export(uri, id) } }
    val nearBottom by remember { derivedStateOf { !scroll.canScrollForward || (scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= scroll.layoutInfo.totalItemsCount - 2 } }
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty() && (nearBottom || messages.last().role == MessageRole.USER)) scroll.scrollToItem(messages.lastIndex + if (messageCount > messages.size) 1 else 0)
    }
    Scaffold(topBar = {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                KaiMonogram(spec.character.name.ifBlank { "K" }, Modifier.size(39.dp), spec.mode == ChatMode.ROLEPLAY)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(spec.title.ifBlank { spec.character.name }, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (spec.mode == ChatMode.ROLEPLAY) "CERITA  ·  ${spec.scenario.genre.ifBlank { "Roleplay" }}" else "CHAT  ·  ${spec.character.name}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface), navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } }, actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu percakapan") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Karakter, model & suara") }, onClick = { menu = false; vm.editSession(id) }, enabled = !busy)
                    DropdownMenuItem(text = { Text("Memori percakapan") }, onClick = { menu = false; vm.navigate("memory") })
                    DropdownMenuItem(text = { Text("Duplikat sesi") }, onClick = { menu = false; vm.duplicate(id) }, enabled = !busy)
                    DropdownMenuItem(text = { Text("Ekspor sesi") }, onClick = { menu = false; export.launch("kai-session-$id.json") })
                    DropdownMenuItem(text = { Text("Hapus sesi") }, onClick = { menu = false; deleteSession = true })
                }
            }
        })
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
             .navigationBarsPadding().imePadding().padding(horizontal = 15.dp, vertical = 10.dp)) {
            if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sedang menulis…", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { vm.graph.chat.cancel(id) }) { Text("Hentikan") }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it.take(32_000); vm.draft(id, draft) }, Modifier.weight(1f),
                    shape = RoundedCornerShape(23.dp),
                    placeholder = { Text(if (spec.mode == ChatMode.ROLEPLAY) "Tulis tindakan atau dialogmu…" else "Pesan untuk ${spec.character.name}…") }, maxLines = 6)
                FilledIconButton(onClick = {
                    if (draft.isNotBlank() && !busy) { val text = draft; draft = ""; vm.send(id, text) }
                }, enabled = draft.isNotBlank() && !busy, modifier = Modifier.padding(bottom = 3.dp)) { Icon(Icons.AutoMirrored.Filled.Send, "Kirim pesan teks") }
            }
        }
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { inset ->
        Box(Modifier.fillMaxSize().padding(inset)) {
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (messageCount > messages.size) item(key = "older") {
                    TextButton(onClick = { visibleCount += 100 }, modifier = Modifier.fillMaxWidth()) { Text("Muat pesan sebelumnya (${messageCount - messages.size})") }
                }
                if (messages.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        KaiMonogram(spec.character.name, Modifier.size(80.dp), spec.mode == ChatMode.ROLEPLAY)
                        Text(if (spec.mode == ChatMode.ROLEPLAY) spec.scenario.title.ifBlank { "Cerita dimulai di sini." } else "Kenalan dengan ${spec.character.name}",
                            style = MaterialTheme.typography.headlineLarge)
                        Text(if (spec.mode == ChatMode.ROLEPLAY) spec.scenario.opening.ifBlank { "Tulis tindakan pertamamu untuk memulai cerita." }
                             else "Tulis pesan pertama. ${spec.character.name} akan merespons sesuai karakter yang kamu buat.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (spec.model.isBlank()) Button(onClick = { vm.editSession(id) }) { Text("Pilih model Gemini") }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageRow(vm, spec, message, busy,
                        onCopy = { coroutine.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("Pesan k.ai", message.text))); vm.notify("Pesan disalin.") } },
                        onEdit = { edit = message }, onRegenerate = { regenerate = message }, onDelete = { deleting = message })
                }
            }
            if (!nearBottom) FilledTonalButton(onClick = { coroutine.launch { if (messages.isNotEmpty()) scroll.animateScrollToItem(messages.lastIndex + if (messageCount > messages.size) 1 else 0) } }, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) { Text("Ke pesan terbaru") }
        }
    }
    edit?.let { message ->
        var text by remember(message.id) { mutableStateOf(message.text) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Edit pesan") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint("Pesan setelah ini akan dihapus agar alur tidak bertentangan. Jika ini pesanmu, AI akan membuat balasan baru.")
                Field("Pesan", text, { text = it }, lines = 5, max = 32_000)
            }
        }, confirmButton = { TextButton(onClick = { vm.graph.chat.editAndContinue(message, text); edit = null }, enabled = text.isNotBlank()) { Text("Simpan & lanjutkan") } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Batal") } })
    }
    regenerate?.let { m -> ConfirmDialog("Buat ulang balasan?", "Balasan AI dan pesan setelah titik ini akan diganti. Pesan pengguna tetap tersimpan. Menggunakan kuota API.", { regenerate = null }, "Buat ulang") { vm.graph.chat.regenerate(m) } }
    deleting?.let { m -> ConfirmDialog("Hapus pesan?", "Pesan dan audio terkait akan dihapus. Ringkasan konteks direset agar fakta yang dihapus tidak tersisa di ringkasan.", { deleting = null }) { vm.graph.chat.deleteMessage(m) } }
    if (deleteSession) ConfirmDialog("Hapus sesi ini?", "Semua pesan, memori, dan VN sesi ini akan dihapus.", { deleteSession = false }) { vm.deleteSession(id) }
}

@Composable private fun MessageRow(vm: KaiViewModel, spec: SessionSpec, message: ChatMessage, busy: Boolean, onCopy: () -> Unit, onEdit: () -> Unit, onRegenerate: () -> Unit, onDelete: () -> Unit) {
    val ai = message.role == MessageRole.MODEL
    var actions by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (ai) Alignment.Start else Alignment.End) {
        Text(if (ai) spec.character.name.ifBlank { "Asisten" } else "Kamu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(7.dp))
        Box {
            Surface(color = if (ai) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(21.dp),
                border = if (ai) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f)) else null,
                modifier = Modifier.fillMaxWidth(if (ai) .96f else .90f).combinedClickable(onClick = {}, onLongClick = { actions = true })) {
                Column(Modifier.padding(15.dp)) {
                    if (message.text.isNotBlank()) {
                        if (ai && spec.mode == ChatMode.ROLEPLAY) StoryText(message.text, spec.scenario.actors)
                        else Text(formatText(message.text), style = MaterialTheme.typography.bodyLarge)
                    } else if (message.status == MessageStatus.GENERATING) LinearProgressIndicator(Modifier.width(96.dp))
                }
            }
            DropdownMenu(actions, { actions = false }) {
                DropdownMenuItem(text = { Text("Salin") }, onClick = { actions = false; onCopy() })
                DropdownMenuItem(text = { Text("Edit") }, onClick = { actions = false; onEdit() }, enabled = !busy)
                if (ai) DropdownMenuItem(text = { Text("Buat ulang respons teks") }, onClick = { actions = false; onRegenerate() }, enabled = !busy)
                DropdownMenuItem(text = { Text("Hapus") }, onClick = { actions = false; onDelete() }, enabled = !busy)
            }
        }
        if (message.error.isNotBlank()) {
            Spacer(Modifier.height(8.dp)); Hint(message.error, true)
            if (!busy) TextButton(onClick = onRegenerate) { Text("Coba lagi") }
        }
        if (ai && message.status != MessageStatus.GENERATING && message.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            if (message.audio.isNotEmpty()) AudioPlayerBar(
                vm, message.id, message.audio.map { vm.graph.audio.file(it.fileName) },
                message.audio.map { it.speaker },
                message.audio.map { it.profileSnapshot.playbackSpeed() },
                message.audio.firstOrNull()?.profileSnapshot?.playbackSpeed() ?: 1f
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.audioGenerating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  Membuat VN…", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { vm.graph.chat.cancelVoice(message.id) }) { Text("Batal") }
                } else FilledTonalButton(onClick = { vm.graph.chat.generateVoice(message.id, spec.autoPlay) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (message.audio.isEmpty()) "Ubah ke VN" else "Buat ulang VN")
                }
                TextButton(onClick = { actions = true }) { Text("Opsi") }
            }
            if (message.audioError.isNotBlank()) Hint(message.audioError, true)
        }
    }
}

@Composable private fun StoryText(text: String, actors: List<CharacterSpec>) {
    val segments = remember(text, actors) { RoleplayParser.parse(text, actors) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        segments.forEach { segment ->
            when (segment.kind) {
                SegmentKind.NARRATION -> Text(formatText(segment.text), style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentKind.ACTION -> Text("${segment.speaker}: ${segment.text}", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentKind.DIALOGUE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(segment.speaker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(formatText(segment.text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun formatText(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\*([^*\\n]+)\\*")
    var at = 0
    regex.findAll(text).forEach { match ->
        append(text.substring(at, match.range.first))
        when {
            match.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[1]) }
            match.groups[2] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(match.groupValues[2]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[3]) }
        }
        at = match.range.last + 1
    }
    append(text.substring(at))
}
