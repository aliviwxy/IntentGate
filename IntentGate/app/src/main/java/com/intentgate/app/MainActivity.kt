package com.intentgate.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var telegram by remember { mutableStateOf(getPreferences(MODE_PRIVATE).getBoolean("telegram", true)) }
                var instagram by remember { mutableStateOf(getPreferences(MODE_PRIVATE).getBoolean("instagram", false)) }
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("IntentGate", style = MaterialTheme.typography.headlineLarge)
                    Text("Choose which apps require a purpose and time limit before opening.")
                    Row { Checkbox(telegram, { telegram = it; getPreferences(MODE_PRIVATE).edit().putBoolean("telegram", it).apply() }); Text("Telegram") }
                    Row { Checkbox(instagram, { instagram = it; getPreferences(MODE_PRIVATE).edit().putBoolean("instagram", it).apply() }); Text("Instagram") }
                    Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Enable IntentGate in Accessibility settings") }
                    Text("On opening a selected app, fill in the mandatory prompt. At the end of the time allowance, the prompt returns while the app is foregrounded.")
                    Text("Accessibility is used only to identify the foreground app. No message content is accessed or uploaded.")
                }
            }
        }
    }
}
