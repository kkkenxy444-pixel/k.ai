package com.kai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kai.app.ui.KaiRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Allow screen recording for UI testing; the API key field remains password-masked.
        enableEdgeToEdge()
        setContent { KaiRoot(viewModel()) }
    }
    override fun onStart() { super.onStart(); (application as KaiApplication).graph.player.setForeground(true) }
    override fun onStop() { (application as KaiApplication).graph.player.setForeground(false); super.onStop() }
}
