---
paths:
  - "app/src/main/kotlin/**/ui/screens/matchdetail/**"
  - "app/src/main/kotlin/**/viewmodels/MatchDetail*"
  - "app/src/main/kotlin/**/utils/TimelineEventExtractor.kt"
  - "app/src/main/kotlin/**/data/remote/AppwriteMatch*"
---

# Match Detail and Commentary UI

**Every `match-sync` row carries an `updated_at`, and the device reads it.** `getForMatch` and
`observeMatch` return `LiveDetailSnapshot`/`ScorecardSnapshot` — the payload plus the row's own
write time — because a row that froze when its match left `live-matches.json` and one written this
minute are otherwise identical field for field, which is what made the staleness in `eb91dcd`
invisible. `updatedAtMs` is null when the column is missing or unparseable, and **null means
unknown, never old**: a caller falls back to the rule it had before rather than treating the row as
stale, or one malformed timestamp blanks a live score. `ROW_STALE_MS`/`DETAIL_STALE_MS` are both
3 minutes — an open match-detail screen pings presence every 90s, which earns a detail-tier pass on
every one-minute run, so a row still being written is about a minute old at worst.

**The matches list outranks a row that stopped being written.** `MatchDetailViewModel.onMatchUpdated`
is called from composition on every list emission; `listLeadsDetail`
(`viewmodels/MatchDetailListSeed.kt`) decides, and it says yes in exactly three cases: before any
payload has arrived, when the list entry has gone terminal, and when the row is older than
`DETAIL_STALE_MS`. This is `eb91dcd` pointed the other way — there a frozen row overwrote the list
on a finished match; here a **live** match that `match-sync` is not writing at all (position 13+
under `MAX_MATCHES`, or one nobody is viewing) sat on its open-time score while the list
advanced behind it, because the state is seeded from the list at construction and so is never
blank, and `MatchDetailViewV2`'s `ifBlank` fallback could therefore never fire. `seededFrom` touches
only the fields the list actually carries: the batting pair, the bowler, the recent balls and the
scorecard exist only in the payload, and blanking them because the list is silent about them would
be the same bug again.

**The live payload is oriented to the first-innings side, and it is aligned by team id.** Everything
`match-sync` publishes — both score slots and the win probability — belongs to the side that batted
first, while `match.teams` is Cricbuzz's listing order; when the side batting second was listed
first, every score and the probability bar sat against the wrong team. Do **not** align on the
names: `inningsScoreList.batTeamName` is a short code ("EZONE" for Central Zone, "RSAA" for South
Africa A) that matches no full name under `normalizeTeamName` or `codeMatchesName`, so a name-based
swap silently never fires. The payload and the `live-winprob` entries carry `team1Id`,
`CricketMatch.Team` carries `teamId`, and `viewmodels/MatchLiveDetailAlignment.kt` compares those.
`battingTeamIsTeam1` was not a substitute either: it compared `current.inningsId` with
`isl[0].inningsId`, so it was false for a side batting its *second* innings even though that side is
team1 — and `winProbFor` flips the probability on it, so for half of every Test the number was
published against the opposition. It is now `current.batTeamId === isl[0].batTeamId`, the same field
`firstInningsTeamId` already reads, with the innings-id form kept only as a fallback for a miniscore
carrying no `batTeamId`.

**A tapped match is an overlay above the NavHost, and the NavHost stays composed under it.**
`BouncerAppContent` used to *swap* `MainAppContainer` for `MatchDetailViewV2`, which disposed the
NavHost and its back stack, so closing a match opened from a series, a search or a team landed on
Home. The navController is now hoisted there, the list poll pauses while the overlay is up
(`isCovered`), and the match screen reaches navigation through `LocalOpenSeries` rather than a
parameter.

**Viewer gate:** `MatchDetailViewModel` pings `bouncer-match-presence` every 90s while a match is open and **not over** — gated on `isMatchOver(match, liveStatus)`, never on `match.isLive`, which is a frozen list snapshot (same staleness class as `5b86664`). A dead heartbeat means every sync logs "0 with viewers" and the tab falls back to raw Cricbuzz text — but it does **not** stop deliveries being stored: the slow tier gates the LLM rewrite budget alone (see below). `MatchPresenceReporter.ping` leaves a breadcrumb, not an event: every cause of a failed ping is already reported once by `AppwriteClient.executeFunctionDetailed` (uninitialised SDK, guard, FAILED, HTTP status), and the one it deliberately skips — the device being offline — was most of what the old capture raised (ANDROID-3M).

**The commentary feed is chronological, and chronological is not cricket order.** Rows are ordered
by `sort_key` (the Cricbuzz timestamp), so a Test's third-innings over 60 is genuinely newer than
its second-innings over 80. `CommentaryTabV2` used to reconcile that by regrouping the whole feed
with `groupBy { over }.toSortedMap(reverseOrder())`, which discards chronology — rendering over 89
directly above over 83 as if overs 84–88 had been skipped, and a wicket at 60.2 above one at 80.5
with nothing to explain it. `buildCommentaryBlocks` walks the feed **in order**, opens a block per
over, and draws an innings divider where the innings changes: from the `innings` column, or — for
rows written before that column could be trusted — from an over number that *rises* while walking
back in time, which is the shape an innings boundary has. Never re-sort this feed.

**Never state a figure for a partially-stored over.** `overTotalRuns`/`isMaidenOver` require six
legal deliveries present. One stored dot ball rendered as "Over 89 · MAIDEN · 0 runs".

**The commentary `ball` column is the over-and-ball label, and it is the only trustworthy one.**
`CommentaryEntry.ball` comes straight off the `match-commentary` row, where `match-sync` writes
`ballMetric.label` — `"19.2"`, or The Hundred's `"Ball 72"` (pinned by the smoke probes at
`match-sync/src/main.js`). Anything that wants the ball number must read this column. Regexing a
leading `40.4` out of the prose does not work: that prefix exists only on lines the AI chain
rewrote, and the passthrough branch (`ai_generated = false`) writes raw Cricbuzz text unprefixed,
which is the dominant path in production. No over-shaped regex can match The Hundred either.

**Deduplication that reads the ball out of the prose deletes most of the innings.**
`TimelineEventExtractor.deduplicateByBall` used to key both of its passes on
`ballNumberOf(text)`, so on raw passthrough rows every delivery in an over looked like one
unnumbered line. Pass 1 then keyed on the text itself — collapsing the three separate balls that
are all `"Tymal Mills to Matthew Hollard, no run"` into one — and pass 2 compared lines *across*
ball numbers with a prefix floor of `DEDUP_MIN_LENGTH` = 15 characters, which is the width of
`"Tymal Mills to "`. Every short delivery was therefore judged a duplicate of the one long line in
its over, and the long line is always the wicket or the boundary. Over 18 of ETPL match 169380
rendered as two balls of six while the "This Over" chips above it (which come from the live-detail
over summary, not this feed) showed all six. Three unit tests asserted that collapse as though it
were the contract, describing it as "pre-existing behaviour this perf change must not alter". It
was the bug. `deduplicateByBall` now takes a `ballOf` accessor — `CommentaryParsing` passes the
`ball` column — pass 2 compares only within one ball, and `DEDUP_PREFIX_RATIO` is `1.0`, a strict
prefix, which is the actual shape of a progressive republish. **Check a dedup change against an
over of raw passthrough rows, never against distinctly-worded AI prose**, which is what the old
fixtures used and why they never caught this.

**The head-to-head card never rendered, and four cards showed figures nothing produced.**
`H2HRepository` lowercased the pair it looked up and compared it with keys the trainer writes in
original case; none of the 1,732 keys in the live file is lowercase, so the lookup never matched.
It now indexes the file case-insensitively on load. The file is the **T20 record only**, and it used
to pool a country's men's and women's sides under one key: a women's pair now has its own
(`A|||B|female`), and the card is shown on T20 matches only. Every other format reads its own
record — right format, right gender — from the match context, on the Info tab. Removed in the same pass, because each was a
constant: the toss card's "venue win %", "H2H toss 3-2" and "Dew factor HIGH"; the prediction
card's 85% confidence badge and "Updated: Just now"; and the Info tab's weather panel (wind, dew
point and visibility hardcoded, temperature and humidity defaulted) with its pace/bounce/spin bars
scored from keywords in the preview text. The pitch report is shown as written; the forecast and
the ground's record now come from `mc-{matchId}`.

**Highlights are YouTube embeds, and the embed needs a Referer.** Every row's `embed_url` is
`www.youtube.com/embed/{id}` (100 of the newest 100, 2026-10-04). `HighlightPlayerDialog` used to
`loadUrl` that address directly — a top-level navigation with no Referer, which YouTube answers with
"error 153" on every clip. `youtubeEmbedHtml` (`ui/components/HighlightEmbed.kt`) wraps the player
in an iframe loaded with `loadDataWithBaseURL(EMBED_ORIGIN, …)`, the app id as an https origin.
There is no native player and there cannot be one: YouTube exposes no stream, and downloading or
re-hosting a clip breaks its terms, Play policy and the broadcaster's copyright. A clip that still
fails reports its code through the IFrame API — 150 on the emulator was a video deleted upstream
(9 of those 100 answer 404 on oEmbed) — and the dialog then offers "Open in YouTube".
