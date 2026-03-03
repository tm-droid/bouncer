package com.tanush.bouncer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.tanush.bouncer.mcp.McpViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import io.sentry.Sentry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpDemoScreen(
    onConfigureClick: () -> Unit = {}
) {
    val vm: McpViewModel = viewModel()
    var echoInput by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var analyzeInput by remember { mutableStateOf("") }
    val echoResult by vm.echoResult.collectAsState()
    val searchResult by vm.searchResult.collectAsState()
    val analyzeResult by vm.analyzeResult.collectAsState()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun handleLoading(start: Boolean) {
        try {
            loading = start
            if (start) error = ""
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            TopAppBar(
                title = { Text("MCP Server Demo") },
                actions = {
                    IconButton(onClick = { onConfigureClick() }) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Configure")
                    }
                }
            )

            if (error.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, color = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(8.dp))
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = echoInput,
                        onValueChange = { echoInput = it },
                        label = { Text("Echo message") },
                        singleLine = true
                    )
                    FilledTonalButton(onClick = {
                        handleLoading(true)
                        vm.callEcho(echoInput)
                        handleLoading(false)
                        if (echoResult.startsWith("Error") || echoResult.startsWith("Echo tool error")) error = echoResult else error = ""
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call Echo")
                    }
                    Text("Echo Result: $echoResult", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        label = { Text("Search query") },
                        singleLine = true
                    )
                    FilledTonalButton(onClick = {
                        handleLoading(true)
                        vm.callSearch(searchInput)
                        handleLoading(false)
                        if (searchResult.startsWith("Error") || searchResult.startsWith("Search tool error")) error = searchResult else error = ""
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call Search")
                    }
                    Text("Search Result: $searchResult", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = analyzeInput,
                        onValueChange = { analyzeInput = it },
                        label = { Text("Analyze topic") },
                        singleLine = true
                    )
                    FilledTonalButton(onClick = {
                        handleLoading(true)
                        vm.callAnalyze(analyzeInput)
                        handleLoading(false)
                        if (analyzeResult.startsWith("Error") || analyzeResult.startsWith("Analyze tool error")) error = analyzeResult else error = ""
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(imageVector = Icons.Filled.Analytics, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call Analyze")
                    }
                    Text("Analyze Result: $analyzeResult", style = MaterialTheme.typography.bodySmall)
                }
            }

        }
    }
}