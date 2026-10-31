# Moved from CLAUDE.md

## Environment & Secrets
- Before assuming an API/env var is configured, verify it explicitly (check `.env`, deployment config, or ask). Do not assume RAPIDAPI_KEY, Appwrite keys, etc. are set.
- For Appwrite changes, prefer the MCP server over CLI push (CLI has caused domain rule conflicts and column schema deletion from config drift).
- Appwrite queries: `Query.search` needs a fulltext index and a search string of 3+ chars. `Query.select` takes a list: Kotlin `Query.select(listOf("a","b"))`, use `"*"` / `"rel.*"` for relations. Use only valid methods (equal, limit, orderDesc, ...).
