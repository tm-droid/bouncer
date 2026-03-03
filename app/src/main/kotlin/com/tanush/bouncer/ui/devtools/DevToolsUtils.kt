package com.tanush.bouncer.ui.devtools

import android.content.Context
import android.view.Choreographer
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.*
import java.io.File
import io.sentry.Sentry

object LogCollector {
    private val _logs = mutableStateListOf<String>()
    val logs: List<String> get() = _logs

    fun log(msg: String) {
        try {
            val ts = System.currentTimeMillis()
            _logs.add("[$ts] $msg")
            if (_logs.size > 5000) _logs.removeAt(0)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object NetworkStub {
    var offline: Boolean = false
    var delayMs: Int = 0
}

object SimulatedMatchManager {
    private val _matches = mutableStateListOf<String>()
    val matches: List<String> get() = _matches
    val matchesCount: Int get() = _matches.size

    fun createSampleMatches(count: Int = 3) {
        try {
            _matches.clear()
            repeat(count) { i -> _matches.add("SampleMatch-${System.currentTimeMillis()}-$i") }
            LogCollector.log("Created $count sample matches")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun runLifecycle() {
        try {
            GlobalScope.launch(Dispatchers.Default) {
                for (i in _matches.indices) {
                    LogCollector.log("Match ${_matches[i]} -> started")
                    delay(500)
                    LogCollector.log("Match ${_matches[i]} -> halftime")
                    delay(500)
                    LogCollector.log("Match ${_matches[i]} -> finished")
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object PerfMonitor {
    private var callback: Choreographer.FrameCallback? = null
    private var lastTimeMs: Long = 0
    private var frameCount = 0
    var currentFps: Float = 0f
        private set

    fun start() {
        try {
            if (callback != null) return
            lastTimeMs = 0
            frameCount = 0
            callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val t = frameTimeNanos / 1_000_000
                    if (lastTimeMs == 0L) lastTimeMs = t
                    frameCount++
                    val elapsed = t - lastTimeMs
                    if (elapsed >= 1000) {
                        currentFps = frameCount * 1000f / elapsed
                        frameCount = 0
                        lastTimeMs = t
                    }
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            Choreographer.getInstance().postFrameCallback(callback)
            LogCollector.log("PerfMonitor started")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun stop() {
        try {
            callback?.let { Choreographer.getInstance().removeFrameCallback(it) }
            callback = null
            LogCollector.log("PerfMonitor stopped")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun currentMemorySnapshot(): Long {
        try {
            val rt = Runtime.getRuntime()
            return rt.totalMemory() - rt.freeMemory()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object CrashDumper {
    fun triggerAndCapture(): String {
        try {
            return try {
                throw RuntimeException("DevTools induced crash (captured)")
            } catch (t: Throwable) {
                val sw = java.io.StringWriter()
                t.printStackTrace(java.io.PrintWriter(sw))
                val trace = sw.toString()
                LogCollector.log("Captured crash: ${t.message}")
                trace
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object DevToolsExporter {
    fun exportDiagnostics(context: Context): String {
        try {
            val file = File(context.cacheDir, "devtools_diagnostics_${System.currentTimeMillis()}.json")
            val json = buildString {
                append("{\n")
                append("  \"logs\": [\n")
                LogCollector.logs.forEachIndexed { i, l ->
                    append("    \"")
                    append(l.replace("\"", "\\\""))
                    append("\"")
                    if (i < LogCollector.logs.size - 1) append(",")
                    append("\n")
                }
                append("  ],\n")
                append("  \"network\": { \"offline\": ${NetworkStub.offline}, \"delayMs\": ${NetworkStub.delayMs} },\n")
                append("  \"matches\": [\n")
                SimulatedMatchManager.matches.forEachIndexed { i, m ->
                    append("    \"")
                    append(m.replace("\"", "\\\""))
                    append("\"")
                    if (i < SimulatedMatchManager.matches.size - 1) append(",")
                    append("\n")
                }
                append("  ]\n")
                append("}\n")
            }
            file.writeText(json)
            return file.absolutePath
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
