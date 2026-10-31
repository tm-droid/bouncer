# POST /match-detail

Call `bouncer-api` with execution path `/match-detail`, method POST and JSON
`{"matchId":"12345"}`. IDs must be numeric strings of 1–12 digits. Native clients
must use a request-scoped 20-second timeout. The router caps this route at 12 seconds;
the handler budgets dependencies against 10.5 seconds, aborts upstream HTTP and makes
one attempt per parallel live/scorecard page. SDK v15 awaits are time-bounded but its
transport cannot be cancelled; late SDK completion can occur after a response.

The checked-in `test/fixtures/match-detail.json` is the schemaVersion=1 wire contract.
`summary` is the stored list item; `detail`, `scorecard` and `meta` retain the canonical
Cricbuzz parser shapes (detail may also retain canonical server prediction fields).
`commentary` is up to 48 raw recent parser items, each with `source:"cricbuzz"` and
`aiGenerated:false`; this endpoint never queries commentary rows or creates AI prose.
Missing sections are null. `availability` has an entry for every section:
`{available:boolean,fetchedAt:epochMillis|null,stale:boolean}`. Empty commentary is
unavailable, not evidence that a historical feed is complete. Root `fetchedAt` is the
last successful page-section refresh, null before one; it does not retimestamp a
preserved scorecard. Root `stale` means at least one available section is stale.
`retryAfterSeconds` is the current failure/budget cooldown. `state` uses the stored
state (recent-list membership normalizes it to `Complete`). Clients should continue
using summary/status to distinguish upcoming and live states.

400 means invalid JSON/ID, 404 means absent from successfully downloaded current
lists and cache, 503 means unavailable with no usable section, 405 means wrong method.
Usable summary/stale/partial responses are 200. Failures preserve last-good sections
with their original section timestamps. A failed list download is 503, not 404, unless
cached usable data can be returned. IDs from the private cache can be served after
leaving current lists; cold upstream fetches always require current list membership.

The private `MATCH_DETAIL_CACHE_TABLE_ID` defaults to `match-detail-cache` in
`APPWRITE_AI_CACHE_DB_ID` (`bouncer-db`). The Free plan's single Storage bucket is
already occupied by public lists, so the response cache uses a separate table with
no permissions and `rowSecurity:false`; upserts also supply empty row permissions.
Server credentials need rows read/write; canonical row access remains read-only.
The required `payload` mediumtext column stores JSON capped at 1 MiB
of UTF-8 before writing (mediumtext supports up to 4,194,303 characters).
Deterministic `md-<matchId>` row IDs use one get/upsert, preserving last-good data
without delete/create gaps. Cleanup filters/orders the built-in `$updatedAt` field,
so no custom column/index is needed. Create and verify this private table/column
before deployment; keep its ID out of public shared/backend.json.
Appwrite column encryption requires a paid plan and is disabled here; the payload
contains public cricket responses, and table/row permissions prevent client access.

TTL: live 60s, upcoming and unsettled completed 600s, completed 24h only when the
archive `m/<id>.json` proves `final:1`. Failed/partial refreshes back off 90s. At most
40 refresh attempts per rolling 10min, 100 memo entries and 40 single-flight entries
per warm container. These are not distributed locks or global rate limits. Cache
replacement is a best-effort atomic row upsert, with last-good data retained in memory;
containers can race. Sharing the route spends Appwrite row reads/writes, bandwidth and
executions. Scheduled maintenance reads/deletes at most 20 private rows older than 14 days
per tick, before the commentary billing-window early return, with a 3-second bound.

Run `node appwrite/tools/sync-api-parsers.mjs` after canonical parser edits;
`--check` verifies byte-for-byte archive-local copies. The pure outcome helper stays
re-exported from llm.js for old callers; API never imports provider code. Generated
copies are checked in for VCS/source-only deployments.
