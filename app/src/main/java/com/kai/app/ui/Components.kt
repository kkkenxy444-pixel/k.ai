@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kai.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kai.core.model.Trait

@Composable fun Page(title: String, back: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } }, actions = action)
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}

@Composable fun Section(title: String, detail: String = "") {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun Field(label: String, value: String, onChange: (String) -> Unit, lines: Int = 1, hint: String = "", max: Int = 8000) {
    OutlinedTextField(value, { onChange(it.take(max)) }, Modifier.fillMaxWidth(), label = { Text(label) },
        minLines = lines, maxLines = if (lines > 1) 8 else 1, singleLine = lines == 1,
        supportingText = if (hint.isNotBlank()) ({ Text(hint) }) else null)
}

@Composable fun Hint(text: String, error: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun OperationBanner(vm: KaiViewModel) {
    val state by vm.operation.collectAsState()
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.message.isNotBlank()) Hint(state.message, state.error)
}

@Composable fun ToggleRow(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotBlank()) Hint(detail)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable fun TraitSlider(trait: Trait, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(trait.label, style = MaterialTheme.typography.bodyLarge)
            Text(value.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = trait.label; stateDescription = "$value dari 100" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Rendah", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tinggi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, confirmLabel: String = "Hapus", onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable fun TextInputDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Field("Teks", value, { value = it }, lines = 3) },
        confirmButton = { TextButton(onClick = { onSave(value); onDismiss() }, enabled = value.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}
