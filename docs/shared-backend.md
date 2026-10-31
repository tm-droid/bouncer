# Shared Android and iOS backend

Bouncer has one Appwrite project and two native clients. Public client identifiers live in `shared/backend.json`; Android emits its existing BuildConfig fields from that file and iOS bundles the same file. Endpoint, project, table and bucket identifiers are public configuration. They do not grant server access. Provider credentials and the Appwrite server API key stay in the function environment. Do not put them in either app, generated build constants, Xcode settings, or source control.

## Data ownership and budgets

The scheduled `bouncer-match-sync` owns match lists, live-detail/scorecard rows, predictions and generated commentary. Both apps consume those results. The `bouncer-api` on-demand `/match-detail` route fills missing detail using the same Cricbuzz parser, without AI calls, and stores its separate response cache in a private bucket. It never overwrites the cron's enriched rows. Raw commentary is labelled as raw; missing generated commentary or predictions is not synthesized.

The cache, matching freshness rules and request coalescing avoid repeating upstream work across the two apps. Extra users still consume Appwrite bandwidth, function executions and row reads. Per-install and warm-container limits are guardrails, not an enforced organization-wide monthly budget or global lock. Measure actual Console usage before expanding rollout. See [Appwrite's shared Free resource accounting](https://appwrite.io/docs/advanced/billing/free) and [database read/write accounting](https://appwrite.io/docs/advanced/billing/database-reads-and-writes).

Provider key rotation happens once, in function environment variables. Public client identifiers change once in shared configuration. Secrets and signing credentials stay in their current machine/CI stores; the repo migration does not migrate production resources, rewrite schemas or create a second Appwrite project.

## Production setup checklist (not performed by this branch)

1. Inspect the existing project's actual limits, bucket count, API key scopes and deployed permissions. Repository configuration is not evidence of live state. Ensure the existing function server credential can read the listed resources and access the new private bucket; do not grant any server credential to a client.
2. Register an Apple iOS platform in the existing project for the current app bundle identifier `Testing.Bouncer` (the live smoke test reports its rejected origin as `testing.bouncer`). Follow the [official Apple setup](https://appwrite.io/docs/quick-starts/apple). Keep signing and bundle identifiers unchanged for this development milestone.
3. Create only the new `match-detail-cache` Storage bucket with no public read/create/update/delete permissions and no client file permissions. It is a function-internal cache, not an additional client data API. Use the bucket definition and environment-variable names supplied with the route; do not reconcile all production schemas with a CLI push.
4. Set the API function's `MATCH_DETAIL_CACHE_BUCKET_ID=match-detail-cache` and verify required existing server credentials are present, without printing values. The deployment tool reads public endpoint/project defaults from shared configuration; it accepts `APPWRITE_API_KEY` through its environment and does not require an Android local.properties file. Environment changes must be followed by a new deployment to evict warm containers; reading the variable list alone does not prove the function is using it.
5. Run the vendored-parser/config guards, backend suites and native checks first. Deploy only the intended `bouncer-api` archive through the existing narrow deployment tool (or Appwrite MCP if connected). Include the canonical parser helper in any later match-sync deployment; no new function or schedule is needed.
6. Verify one valid live, upcoming and recent ID against `/match-detail`, including cold then warm requests, explicit missing sections, expired cache and an upstream-failure fallback. Verify unknown IDs and malformed IDs. Check function logs and usage: the request path must make zero provider/AI calls and zero writes to canonical detail/scorecard/commentary tables.
7. Verify both apps against the deployed contract before a store release. This task prepares local code and instructions; no production deployment, GitHub push, Play upload, TestFlight distribution or App Store release is implied.

## Operational behavior

Both apps render a list summary immediately and preserve last-good data offline. Lists use the same live/static TTLs; opening a detail screen does not cause list-wide detail fan-out. Live detail and commentary streams exist only while their screen is visible and the app is foregrounded. Presence uses the existing 90-second heartbeat; it does not promise background execution on iOS. Scorecard is loaded on demand, rather than keeping a second large realtime payload subscribed.

A quota-exhausted 402 blocks that operation type and preserves cached content; a transient 429 respects its retry window. Stale data is labelled with its actual age. Per-section timestamps prevent a fresh live score from making an old scorecard appear newly fetched. Upcoming fixtures can legitimately have no scorecard or commentary; recently finished matches may receive corrections. The complete wire contract and fixtures live with the backend route. Cold fetches are limited to IDs in the stored live/upcoming/recent indexes; arbitrary historical IDs are served only when already cached or present in the existing archive. This first milestone does not scrape the entire historical corpus.

The original parent and nested Android working trees are recovery copies and remain untouched. The unified branch retains Android commit history. Do not copy the parent's staged snapshot or embedded legacy credentials into the unified history.

## Verified activation blocker

The actual iOS smoke test currently fails with Appwrite Invalid Origin for `testing.bouncer`; the Apple platform registration is required before validating real lists or detail navigation. The new route and private bucket are also not deployed. This is a cloud activation boundary, not evidence that production data was loaded by the passing fixture tests. See [verification results](verification.md).
