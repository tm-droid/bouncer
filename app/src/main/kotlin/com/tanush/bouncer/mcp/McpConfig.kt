package com.tanush.bouncer.mcp

/**
 * Configuration for MCP client.
 * Set the endpoint to your MCP server URL.
 */
data class McpConfig(
    val endpoint: String = "http://localhost:8080/mcp"
)
