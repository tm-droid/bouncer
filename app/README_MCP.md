# MCP Integration in Bouncer Android App

## Features
- Connects to any Model Context Protocol (MCP) server
- Demo UI for calling MCP tools (echo, search, analyze)
- Configurable endpoint from the app
- Structured logging and error handling
- Unit tests for all tool calls

## Setup
1. Run or deploy an MCP server (see server/README for details)
2. Build and run the Android app
3. Open the MCP Demo from the Home screen
4. Configure the MCP endpoint if needed
5. Try the demo tools

## Adding More Tools
- Add new tool methods to `McpClient` and `McpViewModel`
- Expose them in `McpDemoScreen`

## Troubleshooting
- Ensure the MCP server is reachable from your device
- Check logs for errors (tag: MCP)
- Use the config screen to update the endpoint

## Example MCP Server
See the `kotlin-mcp-server-generator.prompt.md` and generated server for a ready-to-use backend.

---

For advanced usage, see the official [MCP Kotlin SDK documentation](https://github.com/modelcontextprotocol/kotlin-sdk).
