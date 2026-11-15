package com.kai.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.app.feature.chat.ChatScreen
import com.kai.app.feature.memory.MemoryScreen
import com.kai.app.feature.session.*
import com.kai.app.feature.settings.*
import com.kai.app.feature.voice.*

@Composable fun KaiRoot(vm: KaiViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val route by vm.route.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.snackbar.collect { snackbar.showSnackbar(it) } }
    KaiTheme(prefs.theme) {
        Surface(Modifier.fillMaxSize()) {
            if (!ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("k.ai", style = MaterialTheme.typography.displaySmall); OperationBanner(vm); CircularProgressIndicator() } }
            } else if (!prefs.onboarded) OnboardingScreen(vm)
            else {
                BackHandler(enabled = route != "home") { vm.back() }
                val main = route in listOf("home", "voiceLibrary", "settings")
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                    if (main) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                        NavigationBarItem(route == "home", { vm.navigate("home") }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Chat") })
                        NavigationBarItem(route == "voiceLibrary", { vm.navigate("voiceLibrary") }, icon = { Icon(Icons.Default.PlayArrow, null) }, label = { Text("Suara") })
                        NavigationBarItem(route == "settings", { vm.navigate("settings") }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Setelan") })
                    }
                }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
                    Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                        when (route) {
                            "home" -> HomeScreen(vm)
                            "api" -> ApiSettingsScreen(vm)
                            "models" -> ModelsScreen(vm)
                            "editor" -> SessionEditorScreen(vm)
                            "personality" -> PersonalityScreen(vm)
                            "chat" -> ChatScreen(vm)
                            "memory" -> MemoryScreen(vm)
                            "voiceLibrary" -> VoiceLibraryScreen(vm)
                            "voiceEditor" -> VoiceStudioScreen(vm)
                            "settings" -> SettingsScreen(vm)
                            else -> HomeScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
