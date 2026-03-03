package com.tanush.bouncer.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import io.sentry.Sentry

class StatsDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            val title = intent.getStringExtra("title") ?: "Detail"
            setContent {
                val colors = BouncerDesignSystem.colors
                Surface(modifier = Modifier.fillMaxSize(), color = colors.backgroundBase) {
                    StatsDetailScreen(title = title)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

@Composable
fun StatsDetailScreen(title: String) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Full breakdown and charts (demo)")
    }
}
