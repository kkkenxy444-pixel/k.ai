package com.kai.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kai.app.ui.KaiRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Chat content and credentials never appear in screenshots or the recent-app preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { KaiRoot(viewModel()) }
    }
    override fun onStart() { super.onStart(); (application as KaiApplication).graph.player.setForeground(true) }
    override fun onStop() { (application as KaiApplication).graph.player.setForeground(false); super.onStop() }
}
