
package com.tanush.bouncer.mcp

/**
 * Lightweight MCP client stub to avoid compile-time SDK dependency issues during builds and tests.
 * This provides safe placeholder responses and can be swapped for the real SDK-backed client when
 * the project dependencies are available or updated.
 */
class McpClient(private val config: McpConfig) {
    suspend fun callEchoTool(message: String): String = "Echo: $message"
    suspend fun callSearchTool(query: String, limit: Int = 5): String = "Search placeholder results for: $query"
    suspend fun callAnalyzeTool(topic: String): String = "Analyze placeholder for: $topic"
}
