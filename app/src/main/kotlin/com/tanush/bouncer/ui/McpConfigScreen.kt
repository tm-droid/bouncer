package com.tanush.bouncer.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun McpConfigScreen(onSave: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mcp_prefs", Context.MODE_PRIVATE) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "http://localhost:8080/mcp") ?: "") }
    var showSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("MCP Server Endpoint", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Endpoint URL") },
            singleLine = true
        )
        Button(onClick = {
            prefs.edit().putString("endpoint", endpoint).apply()
            showSaved = true
            onSave()
        }) {
            Text("Save Endpoint")
        }
        if (showSaved) {
            Text("Saved!", color = MaterialTheme.colorScheme.primary)
        }
    }
}
