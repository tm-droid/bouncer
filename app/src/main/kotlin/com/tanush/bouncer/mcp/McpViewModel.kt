
package com.tanush.bouncer.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import io.sentry.Sentry

class McpViewModel : ViewModel() {
    private val config = McpConfig()
    private val client = McpClient(config)

    private val _echoResult = MutableStateFlow("")
    val echoResult: StateFlow<String> = _echoResult

    private val _searchResult = MutableStateFlow("")
    val searchResult: StateFlow<String> = _searchResult

    fun callEcho(message: String) {
        try {
            viewModelScope.launch {
                _echoResult.value = "Loading..."
                _echoResult.value = client.callEchoTool(message)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun callSearch(query: String) {
        try {
            viewModelScope.launch {
                _searchResult.value = "Loading..."
                _searchResult.value = client.callSearchTool(query)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private val _analyzeResult = MutableStateFlow("")
    val analyzeResult: StateFlow<String> = _analyzeResult

    fun callAnalyze(topic: String) {
        try {
            viewModelScope.launch {
                _analyzeResult.value = "Loading..."
                _analyzeResult.value = client.callAnalyzeTool(topic)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
