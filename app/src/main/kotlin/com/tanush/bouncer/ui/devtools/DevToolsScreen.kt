package com.tanush.bouncer.ui.devtools

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import com.tanush.bouncer.ui.components.SectionDivider

/**
 * Debug-only diagnostics: offline stub, canned latency, simulated matches, log buffer, FPS/memory
 * sampling, crash dump. Registered in the nav graph **only in debug builds** — see BouncerNavHost.
 *
 * It used to be reachable from the More tab of a release build, and its first control ran an "Echo"
 * against `mcp/McpClient`, a 13-line stub that returned the literal string `"Echo: <input>"` and
 * pointed at `http://localhost:8080/mcp` — which on a phone is the phone. That package is deleted;
 * the rest of this screen is real and stays, behind the gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("devtools_prefs", Context.MODE_PRIVATE) }
    var offlineMode by remember { mutableStateOf(prefs.getBoolean("offline_mode", false)) }
    var cannedDelayMs by remember { mutableStateOf(prefs.getInt("canned_delay_ms", 0)) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopAppBar(
            title = { Text("Dev Tools") },
            navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Filled.Close, contentDescription = "Close") } }
        )

        SectionDivider(title = "Simulation & Diagnostics")

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Offline mode")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = offlineMode, onCheckedChange = {
                offlineMode = it
                prefs.edit().putBoolean("offline_mode", it).apply()
                com.tanush.bouncer.ui.devtools.NetworkStub.offline = it
                com.tanush.bouncer.ui.devtools.LogCollector.log("NetworkStub.offline=$it")
            })
        }

        OutlinedTextField(
            value = cannedDelayMs.toString(),
            onValueChange = { v -> cannedDelayMs = v.toIntOrNull() ?: 0; prefs.edit().putInt("canned_delay_ms", cannedDelayMs).apply(); com.tanush.bouncer.ui.devtools.NetworkStub.delayMs = cannedDelayMs },
            label = { Text("Response delay (ms)") },
            singleLine = true
        )

        val coroutineScope = rememberCoroutineScope()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { com.tanush.bouncer.ui.devtools.LogCollector.log("Simulate: create sample matches"); com.tanush.bouncer.ui.devtools.SimulatedMatchManager.createSampleMatches() }) { Text("Create Matches") }
            OutlinedButton(onClick = { com.tanush.bouncer.ui.devtools.LogCollector.log("Simulate: run lifecycle"); com.tanush.bouncer.ui.devtools.SimulatedMatchManager.runLifecycle(coroutineScope) }) { Text("Run Lifecycle") }
        }
        Text("Simulated Matches: ${com.tanush.bouncer.ui.devtools.SimulatedMatchManager.matchesCount}")

        // Logs
        val logs = com.tanush.bouncer.ui.devtools.LogCollector.logs
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Logs (${logs.size})", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { /* copy placeholder */ }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy logs") }
                Button(onClick = { val path = com.tanush.bouncer.ui.devtools.DevToolsExporter.exportDiagnostics(context); com.tanush.bouncer.ui.devtools.LogCollector.log("Diagnostics exported to: $path") }) { Icon(Icons.Filled.UploadFile, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Export") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                val recent = logs.takeLast(200)
                items(recent, key = { it.hashCode() }) { l -> Text(l, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { val fps = com.tanush.bouncer.ui.devtools.PerfMonitor.currentFps; val mem = com.tanush.bouncer.ui.devtools.PerfMonitor.currentMemorySnapshot(); com.tanush.bouncer.ui.devtools.LogCollector.log("Sampled FPS: $fps, mem: $mem") }) { Text("Sample FPS & Memory") }
            FilledTonalButton(onClick = { val dump = com.tanush.bouncer.ui.devtools.CrashDumper.triggerAndCapture(); com.tanush.bouncer.ui.devtools.LogCollector.log("Crash dump captured: ${'$'}dump") }) { Text("Trigger Crash") }
        }
    }
}
