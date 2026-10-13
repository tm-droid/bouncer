---
paths:
  - "appwrite/functions/match-sync/**"
  - "appwrite/functions/generate-commentary/**"
---

# match-sync: Commentary Pipeline and Provider Ladder

**`bouncer-match-sync` Appwrite function:** scheduled `* * * * *`. Polls the match lists itself first (`runPoll`, see the function list), using the live list in memory and falling back to `live-matches.json` in `cricket-stats`; if neither has anything, returns 200 `{skipped:true}` (not an error). For each live match: fetches Cricbuzz live HTML + scorecard → upserts `match-live-detail` → writes EVERY new delivery to `match-commentary` → rewrites the oldest `commentaryCapFor` of them with the LLM. **Storage is not gated on a viewer; rewriting is.** It used to be the other way round, and a match nobody had open stored one ball per 45 minutes — so the commentary tab showed a sample of the match (overs missing outright, one boundary in an innings, a "recent" line hours old) rather than its commentary. `commentaryMatchSet` caps storage at 16 matches per run, viewers first. **Every stored delivery is now rewritten**, because the calls are batched: `commentaryCapFor` no longer rations rewriting and gates only the DETAIL tier (scorecard fetch, detail row, scorecard row — a write-budget decision), while the slow tier's pass — 60 minutes since 2026-09-30, when followed matches started pinging presence — decides detail freshness for a match nobody has open or follows, not prose. Those last two were 1 and 45 min while groq's 1,000/day was the whole budget; re-derived 2026-09-16 against a ~2,500/day Gemini ladder that was assumed rather than measured (it is ~540), from the measured 11 concurrent unwatched matches. The derivation is written above the constants in `main.js` and mirrored **deliberately** in `slow-tier.test.js` — a test that imports the constant it is checking cannot catch a wrong constant, so that mirror is the one place in this repo where duplication is the point. The watermark seeds from the newest `match-commentary` row (`limit(1)`, one billed read per cold container) and is no longer persisted to the detail row, because most live matches have no detail row. `innings` is stamped **per commentary item** from Cricbuzz's `inningsId`, falling back to the miniscore and then to the scorecard length — the old write-time stamp mislabelled an innings' tail as the next innings. Env vars: `APPWRITE_AI_CACHE_DB_ID`, `MATCH_COMMENTARY_COL_ID`, `MATCH_LIVE_DETAIL_COL_ID`, `MATCH_CACHE_BUCKET_ID`, `APPWRITE_API_KEY`, `GROQ_API_KEY` (+ fallbacks), `SENTRY_DSN` (optional — unset disables server-side reporting), `PROBE_SOURCES` (optional).

**Server-side observability (`match-sync/src/observability.js`):** `@sentry/node`, initialized per container from `SENTRY_DSN` and flushed before every return (the container freezes on return, so an unflushed queue is lost). Reports: raw-text passthrough with the per-provider reasons from `generateLine`'s `attempts`, commentary write failures, a viewer-gated match that produced nothing, an unreadable `active-viewers` file (a 404 is normal and stays quiet), read-budget 402s, **a provider chain with fewer than two reachable rungs** (error level — generation still works, so every other signal looks healthy while `generateVerifiedLine`'s retry has nobody to fall back to; this is the state that went six weeks unnoticed), and **a 429 naming a quota id the per-day latch cannot classify**. The last two are also reported by `generate-commentary`, which on a day with no live match is the only caller talking to the providers at all. Setting `PROBE_SOURCES=1` turns one execution into an upstream reachability probe (status + bytes + markup markers for Cricinfo and Cricbuzz, from inside the Appwrite container) and returns without syncing — that is the only way to answer whether Cricinfo is reachable server-side, which is a different question from whether it is reachable from a laptop or a phone.

**The slow tier is an LLM budget, not a fetch cadence — and the real ball-loss mechanism is the
upstream window.** `handleRun` does `Promise.allSettled(allLive.map(...))`: **every** live match has
its Cricbuzz live page fetched and every new delivery stored, on every one-minute run.
`commentaryMatchSet` caps storage at 16 and `MAX_MATCHES` is also 16, so that slice never drops anything;
`slowTier` feeds only `commentaryCapFor`, the 10/1/0 **rewrite** budget. `slow-sync-state` looks
stale (entries 30+ minutes old while those matches sync every minute) because it is refreshed only
for matches that were on the tier — that is not evidence of a 45-minute fetch cadence, and it was
misread as one.

What actually loses deliveries is that **the live page is a 20-item window**, about two overs —
measured on three live pages, `commText=20` across 55 `self.__next_f.push` chunks. Normally a 20×
margin at a one-minute cadence. But on 2026-09-12 Cricbuzz published nothing for match 161777 for
**thirteen minutes** (match-sync ran every minute throughout, logging `0 new commentary rows`), then
republished a backlog that jumped `12.5` straight to `13.4`. 13.1–13.3 never appeared on the page and
are absent from the table. The watermark absorbed the hole and the tab rendered a three-ball over
indistinguishable from a real one. `findCommentaryGaps` (`cricbuzz.js`) now reports these to Sentry;
it deliberately does not count the source over's tail, because whether that ball was captured by an
earlier run is unknowable from one batch. **There is no backfill**: `/live-cricket-full-commentary/{id}`
is client-rendered and yields zero deliveries server-side, `/cricket-full-commentary/{id}` 404s and
`/cricket-commentary/{id}` 307s back to the live page. Do not re-investigate recovery without new
evidence.

**`MAX_MATCHES` drops live matches past the cap, so it is filled viewers first.** Anything past
the cap gets no commentary, no detail row and no win probability. It was 12 and sliced in Cricbuzz's
list order *before* the viewer set was built, so a viewer watching match 13 was invisible to the
viewer gate entirely; 13 live matches bound it on 2,219 runs in nine days (ANDROID-4P). Now
`liveMatchSlice` puts watched matches first and the cap is 16 — bounded by `READ_BUDGET_PER_RUN` (20),
because a cold container pays one watermark seed read per stored match. The run log prints the
pre-slice count, and Sentry hears about a binding cap at most hourly per dropped-id set (`error` if a
dropped match had a viewer). Steady-state server conditions — chain depth, Cricbuzz unreachable or
blocked, this cap — go through `reportThrottled`; they were one event per one-minute run.

**A finished match is finalised from the recent list, because it is never on the live one.**
The final run — last detail and scorecard rows, the result alert, the summary, the `wpe-` log —
fires when `syncMatch` sees a terminal status, and that was written when an ended match lingered in
`live-matches`. `belongsOn` (`poll.js`) classifies on Cricbuzz's `state`, so a match moves to
`recent-matches` the moment it completes and `syncMatch` never saw it again: over 1,100 runs on
2026-10-05/06 not one match was finalised, and every finished match's rows were frozen wherever the
last live run left them (174057 read "need 87 runs in 56 balls" a day after it was won; 173113 read
"Innings Break"). `endedMatches` (`main.js`) now takes up to two a run from the recent list — only
ids in the poll's `startedIds`, so a match that was already over when first seen is left alone —
and adds them to that run's set and to nothing published. The `:final` marker is kept by **age** (seven days, a
day longer than `startedIds` remembers a match). It was first kept only while the match was on the
recent list, which is the newest twenty: two matches trading its last place lost their markers on
alternate runs, and 173691 was finalised 88 times in half a day. A dropped marker is a second
finalisation, so count `finalised on` per match in the run logs after touching this. A match finalised more than ten minutes after it was last
seen live sends no alert, and the finalising run does not stamp a probability. **Check a finished
match's `match-live-detail` status after any change to which list a match is on.**

**`backlogCtx` is module state and two executions can be alive in one container.** A run that
finished nulled the shared variable under the next run, which threw `Cannot set properties of null
(setting 'viewers')` and failed outright — seen once, at 14:54:38 on 2026-10-03, when two
executions started a second apart on a fresh deployment. Each run now holds its own `runCtx` and
clears the shared variable only if it is still its own. Anything else at module scope that one
run sets and another reads has the same hazard; `contextIo` (the context store's `storage`/`log`)
is deliberately re-pointed at the start of every run for the same reason.

**Rewriting is batched across MATCHES, and that is what makes every ball affordable.** Measured
2026-09-16: 6,672 deliveries a day is ~960 calls batched, against the ~6,700 one call per ball
would need. The "~2,500-call ladder" this was sized against was never real (Gemini is ~540/day), and
~800 high-stakes verify calls come on top, so on 2026-09-24/25 the free ladder ran dry mid-evening
UTC and rewrites fell to ~25% until the Pacific reset, then ran at ~94%. The batch is filled by **breadth, not by waiting**: `handleRun` already runs the matches
concurrently, ~7 new deliveries arrive per run across them, and `queueRewrite` coalesces those on a
250ms debounce into one `generateBatch` call. Filling a batch by over instead would need ~10 minutes
of accumulation against the 0-60s a ball currently takes to appear — the cron stays the only thing
gating that. Cross-match contamination was the risk and was measured: seven deliveries from seven
unrelated matches returned 7/7 keys with zero players leaked.

Three rules hold it up. **Lines are matched to deliveries by `[matchId|ball]` key, never by
position** — a model that drops or reorders a line must leave that delivery absent so it stores raw
text, because a confidently wrong line is far worse than a raw one. **Every queued promise must
resolve**, including when the batch throws: `handleRun` awaits every match and every match awaits
its deliveries, so one unresolved promise costs the run its 120s timeout (pinned by a seam,
`__setBatchFn`, because `generateBatch` returns an empty Map rather than throwing and no fetch stub
can reach that catch). And **every rewritten line is fact-checked, in one batched call** —
`verifyBatch`, run inside `flushBatch` right after `generateBatch`, each line judged against its own
raw Cricbuzz text, verdicts matched by key. Until 2026-09-25 only wickets and boundaries were
checked, one call each; checking every ball per-line would be ~6,500 calls a day. It fails closed:
a FAIL is `verification_failed`, and an unanswered key or no checker at all is
`verifier_unavailable` — never an unchecked line. Measured before shipping on real lines plus six
planted errors (four→six, wrong fielder, lbw→bowled, wrong thrower, bowler batting, invented
milestone): all six caught in 3/3 runs via qwen (~700ms for 14 lines) and 2/2 via the Gemini
fallback. The checker is sent the ball number with each source ("Ball 58.6: …") — without it
"closes the over" was unverifiable, and first/last balls of overs were most of the rejections — and
its rules separate a FACT (who, runs, outcome, dismissal, fielder, anything about the over, innings
or match — a "maiden", a "fifty partnership") from COLOUR (line, length, a defensive push), tuned on
2026-09-25 against its own `[VERIFY-FAIL]` log lines, which carry the rejected text because nothing
else stores it. qwen scored 14/14 × 3 on real rejections plus planted errors; the Gemini fallback
misses over/innings claims (passed "a maiden over" 3/3), so qwen staying reachable matters. The rules follow
`SYSTEM_INSTRUCTION`'s own licence ("the atmosphere is yours — the facts are not"), because a checker
stricter than the writer's brief rejects most good lines. The batch prompt carries the raw Cricbuzz line alone: it already names bowler and
batter, and per-match score context cannot travel in a cross-match batch without becoming a
fabricated fact.

**Raw Cricbuzz text is never stored — a delivery with no verified line is retried, then held.**
Measured 2026-09-26/27: 133 of ~5,400 rows were raw, ~90% `verification_failed`, and the checker
was right — groq wrote "Mahipal Lomror sends a blistering drive… for a FOUR" when Lomror was
bowling, a whole batch of six that way. The quota was not the leak; one rejection with no second
attempt was. Two mechanisms close it. (1) `rewriteWithRetry` (`main.js`) gives every rejected or
dropped delivery **one** more writer call in the same run, `exclude`-ing the rung that failed, and
re-samples the same rung only when no other writer answers; a batch nobody answered is not retried
(the rungs are 429/latched and would fail again this second). (2) Anything still without a verified
line goes to the **rewrite backlog** instead of being written: `holdDelivery` stores everything the
row needs (the live page is a ~20-item window, so the ball will not be on it later), the watermark
still advances, and the held delivery is offered to the writer again on later runs — including runs
with nothing live — and the
late row carries its original `sort_key`, which the device's `sortKey`-positioned insert puts where
the ball was bowled. **Since 2026-10-03 a held delivery mostly rides in the spare slots of a fresh
batch** (`flushBatch` → `takeHeld`): every Gemini rung is limited by requests a day, not tokens, and
a typical batch carried 6.95 of its 12 deliveries, so a top-up costs no call. The size stays
`BATCH_MAX_ITEMS` (12), the size the writer and checker were probed at. The serving order is
`backlogOrder`: matches someone has open, then other live matches, then finished ones, **newest ball
first** in each — it was oldest-first across everything, which after a dry evening spent the
morning's quota on yesterday's match while the one being watched waited. `drainBacklog` still sends
dedicated batches, two a run (`BACKLOG_DRAIN_PER_RUN`, 24), but a FINISHED match gets them only
while its backlog is ≤ 24 (the ordinary trickle) or in the 3 hours before the Pacific reset, when
unused quota would expire (`dedicatedEntries`). **Nothing is ever dropped and there is no age limit**
— an old match must always end up complete; that was an explicit decision, do not add an expiry.
A delivery held during a run is not offered again in that run, and `inFlight` keeps one held
delivery out of two batches.

**A source can contradict itself, and then no line passes.** Match 151554's 15.5 read "Gudakesh
Motie to Ruturaj Gaikwad, 1 run, …, Rohit Sharma carves it" — the scorer's header and the
commentator's description naming different batters. The checker failed every rewrite for an hour,
two writers and two checks a run (~240 calls, on a ~1,330-call day), and what finally passed was
groq **echoing the raw text back**, stored as `ai_generated: true`. Three guards since 2026-10-03:
`sourceConflictNote` appends a scorer's note to the text both the writer and the checker read
(fires only when the description names the other live batter and not the header's; probed 3× —
without the note in the checker's copy the correct unnamed line failed 3/3); `isEcho` drops a line
that is the source again, so it gets its retry instead of being stored; and a held delivery's
`rejections` count stops dedicated batches at `HELD_STUCK_REJECTIONS` (3) — it still rides in spare
slots and the pre-reset sweep, and is never dropped — and raises one **error**-level Sentry event
naming the ball. Before that the only report was the generic hourly "Commentary is being held"
warning inside an existing issue (ANDROID-4Z), which named neither the ball nor the cause. Persisted as `rewrite-backlog` in `cricket-stats`, written only when it
changes, never over a file this container failed to read. **A held delivery's alert is sent when it
is first seen**, not when its row lands — a wicket alert minutes late is past `ALERT_MAX_AGE_MS` —
and the drain sends none. Held balls count as present for `findCommentaryGaps`. The cost is
lateness, never a wrong line: when every writer is spent the tab stops gaining lines and then
catches up in order. `reportThrottled("rewrite-backlog")` fires past 15 minutes or 200 held.
Pinned by `rewrite-backlog.test.js`. A "who bowls, who bats" rule in the writer prompt, with the
roles tagged on each input line, was probed the same day on groq over 36 real rejected deliveries
and cut role swaps without reaching zero ("Sofia Mardones takes a single off Maria Miranda"), so it
was not shipped; the checker catches those swaps and the retry replaces them.

**A delivery is not one row, and the ball label is not its identity.** Audited 2026-10-03 over
1,500 rows against the scorecards and the live pages. (1) Cricbuzz republishes a corrected delivery
under a new timestamp and the row id is the timestamp's hash, so the withdrawn version stayed beside
the correction: match 171061's 10.3 was a single, then a catch that never happened, then the single
again. `supersedeOlder` (`main.js`) now deletes the older **legal** row for the same innings and
label when a newer one is written — in memory per container, so a cold start forgets and the device
rule covers it. (2) A wide or no-ball carries the label of the legal ball that follows, and every
label repeats in the next innings. The device's dedup keyed on the label alone and kept the
*longest* text, so `88.4` wide hid `88.4` FOUR and a withdrawn row could outlast its correction.
`deliveryKey` (`CommentaryParsing.kt`) is innings + label, plus the row's `sortKey` for a
WIDE/NOBALL, and the **newest** row wins. Pinned by `CommentaryFilterTest` and
`commentary-supersede.test.js`.

**A source with no description is written in code, not by a model.** "Chinelle Henry to Mupachikwa,
no run" has nothing to rewrite, and the writer filled it with a length, a shot and a field position
on every ball of matches 173107 and 174881 — which the checker passes by design, as colour.
`plainLine` (`main.js`) writes those from bowler, batter and outcome with `provider: "template"`,
`ai_generated: false`; a dismissal still goes to the writer because its source carries facts. This
is about a quarter of deliveries on an associate-heavy day, and none of them cost a call.
`keepsScorerText` rejects a model line that kept the scorer's header or scorecard tail, which exact-
match `isEcho` let through 24 times in those 1,500 rows.

**The voice went to match-situation lines, and they are event-driven** (`situation.js`). A row with
a blank `ball`, `provider: "situation:<rung>"`, written only on a trigger: the same batter's last 2
balls faced both sixes or last 3 all boundaries, a bowler's 2 wickets in an over (run-outs
excluded), 3 wickets in 18 balls, a 0.15 win-probability move inside 15 minutes, or a side first
crossing 0.90. There is **no fixed interval** — a user decision; do not add one. The writer sees
structured facts only and `validSituationLine` checks the result in code: every number, counted
quantity and capitalised word must be in the facts. A failed line is dropped, never retried. It is
stored when its over ends (`sort_key` = the next over's first ball − 1), because a non-delivery row
mid-over splits that over into two blocks on the device. Win-probability triggers are skipped for
Tests and for matches outside the detail tier.

**Three more triggers read the match's context, and a milestone word needs the trigger's own
facts.** `stateTrigger` (`situation.js`): a batter crossing 50/100/150/200, with their record in
the archive's matches of that format; a partnership passing the same marks; and a first innings
ending at a ground whose usual first-innings score the archive knows (`typicalFirstInnings` on the
context). Each remembers what the last run saw, so a cold container that first sees a batter on 73
says nothing — crossing a mark is the event, being past it is not. `UNBACKED_RE` is no longer an
outright ban: a line may say "half-century" only when its facts do, and only a milestone trigger's
facts ever do. **"career" is never in the facts** — Cricsheet is the matches it recorded, so a
record is "5210 runs in 131 recorded one-day innings". A spelled-out number ("sixty-four balls")
fails the line, correctly: it cannot be checked against a digit.

**A match has a preview and a summary, and neither is ever unchecked prose** (`surfaces.js`,
stored on `mc-{matchId}`). The facts are built in code — head-to-head record, the ground's usual
score, toss, forecast, the model's pre-match chance; or the result, each innings, its top scorer
and best bowling. One writer call; `validSurface` then requires every sentence to be grounded on
its own, both sides to be named, and weather to be called a forecast; then the batch fact-checker
runs with the facts as its source, because the code check proves a number is IN the facts and
cannot see it given to the wrong side. Any failure stores the same facts as plain sentences
(`provider: "template"`). Probed on real contexts on 2026-10-03: 8 of 8 written versions passed
both. A preview needs two things beyond the fixture or it is not written; a summary is written on
the finalising run, the one run every match gets a scorecard on.

**The checker rules were not the reason contradictions were stored.** Four stored lines that
contradict their source (163077 89.1 "left alone" → "defends it back down the pitch") all FAIL
under the existing `VERIFY_BATCH_RULES`, 3/3 on qwen and on `gemini-flash-lite-2`, in isolation. A
reworded rule scored the same, so nothing was changed. Why they passed in production is not
established.

**Cricinfo is a second source where it has a commentator, and its page cannot be paged.**
`/series/{slug}-{id}/{slug}-{id}/ball-by-ball-commentary` is server-rendered: `__NEXT_DATA__` →
`props.appPageProps.data.content.comments` is ~19 deliveries (three overs) as data, and
`content.innings[].inningOvers` is **every completed over of every innings**. Measured 2026-10-06
from the Appwrite container: 200, ~900 KB, 250 ms. `?fromInningOver=` is ignored and
`/_next/data/…` and `hs-consumer-api` are 403, so the window is polled on every run for every
joined live match and kept in memory (`cricinfoMatchStep`, `main.js`). A match joins by both team
names plus start time within 12 h (`joinCricinfoMatch`); a delivery by innings + ball label, which
the two sites write identically (`18.6` ≡ `18.6`). A wide or no-ball is never joined. Prose exists
only where `ballByBallSource` is `"feedback"` — internationals and A tours, 2 of 8 live matches
when measured — so this changes nothing for associate cricket. Three uses:

- **Second report.** When the two sites agree on bowler, batter, runs and wicket (`disagreement`),
  the delivery's text gains ` [Second report: …]`, read by the writer AND the checker
  (`BATCH_SYSTEM_INSTRUCTION`, `VERIFY_BATCH_RULES`). Cricinfo's text is never stored; `isEcho`
  drops a line that carries it whole. A bare scorer's line is templated (`plainLine`) only when
  Cricinfo has no description either. Nothing waits for the second site.
- **Gap fill.** A hole between two of a run's Cricbuzz deliveries is filled from Cricinfo's
  (`cricinfoFills` → `asCricbuzzLine`), through the same rewrite, check and hold. `provider` ends
  `+cricinfo`; `sort_key` is the ball before the hole plus 1, 2, 3 ms. A label this container has
  already stored is not a hole — Cricbuzz republishes old balls under new timestamps.
- **Over history.** `fillOverHistory` adds only overs the history lacks; a Cricbuzz reading and
  its stamped `p` are never replaced. This is what gives the worm an innings a cold container missed.

**Everything from that page is untrusted, and the cleaner is an allowlist.** `promptSafe`
(`cricinfo.js`) keeps Latin letters, digits, spaces and sentence punctuation — **no colon, no
bracket, no line break** — because the prompt's structure is `[key]:` tags, `Source:` labels and
bracketed notes. Four denylist versions were each shown a case they missed (nested entities, a
lone surrogate joining two halves of a label, full-width colons, look-alike scripts); do not go
back to one. A header and a dismissal are not cleaned at all: each must BE its shape or is dropped.
Bodies are size-capped while read (`readBounded`). What an allowlist cannot stop is a plain-English
instruction; the writer is told both accounts are reported text, and the checker and `carriesLink`
gate what is stored. A 403/429 latches every Cricinfo request for an hour and reports at error
level; any other failure latches that one match. A request that got **no answer** — the 8 s timeout, a dropped connection — is not a failure of
the page: it is asked again in five minutes and only the third in a row costs the hour
(`askAgainSoon`). One timeout used to switch a match's second source off for an hour and raise an
error (ANDROID-5E, 32 in two days).

**The same page feeds the Info tab, and the statistics page a wagon wheel.** `matchFacts` puts
umpires, TV umpire, referee, debutants and reviews left on `mc-{matchId}` as `cricinfo`, written
only when changed. `match-statistics` carries `batsmenPerformance[].wagonData` — eight zones that
sum to the batter's runs — **for the two batters at the crease only**, so `cricinfoWagonStep` keeps
the latest snapshot per batter (detail tier, at most every scorecard interval) and publishes
`scorecard.wagon`; the device shows the runs and balls the snapshot covers, not the scorecard's.
Zones are octants clockwise from the top with the bowler's end at the bottom, not mirrored for a
left-hander (verified against per-ball coordinates and the commentary's words). **The pitch map is
not shipped**: `bowlersPerformance[].{lhb,rhb}PitchMap` has numeric `line`/`length` codes whose
meaning has not been verified against per-ball `pitchLine`/`pitchLength` names — do that on a live
covered match before drawing it. A manual execution with body `probe-sources` runs the
reachability probe without `PROBE_SOURCES`.

**A hang is not a refusal in the provider ladder.** An endpoint that times out is skipped for the
rest of the walk and the line (every Gemini rung is one endpoint, so a Google hang used to spend the
whole 45 s there and never ask groq); a 429 still falls through to the next rung on the same
endpoint. `deadlineAt` spans every walk of a request, and match-sync stops asking providers 90 s
into a run. `verifyBatch` takes `writtenBy` and asks models that did not write the lines first.

**An overturned review is not a wicket, and the outcome clause is what says so.** Cricbuzz writes
the on-field decision and the review in one item ("…, no run, … given OUT LBW, but he reviews …
survives"), so every dismissal pattern fires on it. `classifyEvent` (`llm.js`) and
`TimelineEventExtractor.isWicketLine` both read `outcomeClause` — the field after
"<Bowler> to <Batter>," — before any marker in the prose: a clause stating runs is a positive
statement that nobody was dismissed. `outcomeClause` lives in `llm.js`, which `cricbuzz.js` imports,
because llm.js is vendored byte-identically into `generate-commentary`, which has no `cricbuzz.js`.

**The verification call has its own token budget, and it is not the answer's length.** Every
provider in the chain is a reasoning model — groq runs `openai/gpt-oss-20b` with
`reasoning_effort: low` — and reasoning tokens come out of the same `max_tokens` as the reply. So
`verifyLine`'s budget cannot be sized for the word "PASS": at `maxTokens: 16` the budget was spent
before a verdict was ever emitted, groq returned an *empty completion* on the verify prompt while
answering the generate prompt (100) perfectly, and with cerebras and sambanova both on HTTP 402 the
fact-check failed closed on every delivery. That is the whole of ANDROID-38 — 1,858 balls stored as
raw Cricbuzz text — and generation was never the broken half. It is 256 now, pinned by
`verification-scope.test.js`. 64 was tried first and measured insufficient in production: one ball
verified and the next returned `empty completion (length)` again. The verdict is four characters;
the budget is sized by how long the model thinks before emitting them. An `empty completion` note carries its `finish_reason` for the same
reason: `length` (a spent budget) and `stop` (a model that answered nothing) need opposite fixes.
**256 was still not enough for groq** — production logged `groq: empty completion (length)` on
every verify on 2026-09-25, so each high-stakes ball fell through to the two 2048-floor Gemini rungs
(~800/day), which spent both 20/day buckets and left groq alone (ANDROID-4T, and ANDROID-38 again).
groq now carries `tokenFloor: 1024` in `PROVIDERS`; the floor lives on the rung, not in
`verifyLine`. A line that was generated but could not be verified by anyone is
`verifier_unavailable`, not `verification_failed` — the first is a spent chain, the second a
hallucination.

**A 402 is account state, and it was being paid for on every ball while raising no alarm.** Measured
2026-09-15 against the live keys: `GET /v1/models` returns **200** on all three providers and lists
both configured model ids, while `POST /chat/completions` returns **402** on *every* model both
accounts offer — Cerebras `{"type":"payment_required_error","param":"quota"}` (free trial spent),
SambaNova `{"balance_units":0,"code":"PAYMENT_METHOD_REQUIRED"}` (no card). A model a key cannot
reach answers **404 `model_not_found`**, which is what `c324d5b`, `82b467b` and `3bcf060` actually
fixed; this is not that, and no model swap escapes it. Two consequences were being absorbed
silently. (1) `unavailable` is scoped to one line on purpose — a 429 clears by the next ball — so a
402 provider was re-asked on **every delivery**, two doomed round trips at a 15s timeout each, for
six weeks. `paymentBlocked` (`llm.js`) is module-scoped for the opposite reason and holds for the
container's life; the skip is still written to `attempts`, because a three-rung chain that quietly
becomes one rung is the defect this file keeps repeating. (2) `deadProviders` counted **429 only**,
so a provider failing 100% of calls with 402 landed in `calls`, never in the failure column, and
could never reach `rateLimited === calls` — the aggregate alert built for exactly this outage was
structurally blind to it. The tally now counts any non-200 and carries the dominant status, and a
402 **skips the `DEAD_PROVIDER_MIN_CALLS` threshold entirely**: one 402 is conclusive where one 429
is not, and once latched the provider can never be called enough times to clear a ratio. Pinned by
the 402 cases in `verification-scope.test.js`.

**The chain needs a second reachable provider, and credits alone do not give it one.** With cerebras
and sambanova on 402 the chain was groq-only, and `generateVerifiedLine` retries a failed
verification with `exclude: [used.provider]` — deliberately a *different* provider. Groq-only means
that retry has nothing to call, returns null, and every verification FAIL becomes
`reason: "verification_failed"` and a raw-text row. Paying for groq does not change that; it is
structural. Groq's free tier is also 1,000 requests **per day** (`x-ratelimit-limit-requests`) and
8,000 tokens per minute, against two groq calls per rewritten ball and four on a retry.

**Gemini's free quota is per project PER MODEL, so one key buys three rungs — of very different
sizes.** The 429 body names the quota and carries `quotaDimensions: {model}`. Measured limits:
`gemini-3.5-flash-lite` 15 RPM (429 `quotaValue`, 2026-09-16) and 500 RPD / 250k TPM (read from AI
Studio, the only place Google publishes them now); `gemini-3.6-flash` and `gemini-3-flash-preview`
**20 RPD** (`GenerateRequestsPerDayPerProjectPerModel-FreeTier`, `quotaValue: "20"`, production
2026-09-24). The ladder was sized on 2026-09-16 at "≈ 2,500 calls/day" by assuming flash-lite's 500
applied to all three ids — it did not, and the real Gemini supply is **~540/day**, plus groq until
its tokens-per-day cap (hit ~04:30 UTC on 2026-09-25). **Never infer a rung's limit from a sibling
model**: read it at https://ai.dev/rate-limit or from its first per-day 429. All four rungs were
probed against the real `SYSTEM_INSTRUCTION` on wicket/dot/six fixture balls before being added —
the model list is not the set of models a key can call, and `gemini-2.5-flash-lite` proves it by
appearing in `/v1/models` and answering 404 on a completion.

**Any number of Gemini projects.** `PROVIDERS` lists each Gemini rung once; `ladder()` adds a `-<n>`
twin behind it for `GEMINI_API_KEY_2` (always, skipped with its usual note when unset — the names
are stored in `match-commentary.provider` and key the per-day latch, so they must not move) and for
`GEMINI_API_KEY_3`…`_20` when set. One line asks at most twelve rungs over the network and walks
for at most 45 s: with ten projects there are forty Gemini rungs, and `generate-commentary` is
callable by any device, so an unbounded walk was one request reaching every project's quota.

**The ladder since 2026-09-25**: writers `gemini-flash-lite` → `gemini-3.1-flash-lite-preview` →
`groq` (gpt-oss-20b) → `gemini-3.6-flash` → `gemini-3-flash-preview`; plus `groq-qwen`
(`qwen/qwen3.8-27b`), **`verifyOnly`** — asked first for every verdict, never asked to write
(`rungsFor(purpose)`). Probe verdicts that decided it: the preview model had no factual error in 3×11
hard deliveries; qwen invents names when writing ("Babar Zaman") but was perfect as a checker in 2
tokens; `gpt-oss-120b` said "bowled LBW" 3/3 and is rejected; `gemma-4-*` time out or emit raw
`<thought>`; `gemini-omni-*` and `gemini-3.1-pro-preview` have no free quota; `antigravity-*` is
Interactions-API only. groq's per-day 429 is prose ("tokens per day (TPD)"), not a QuotaFailure, so it
latches for 30 minutes (`GROQ_DAY_LATCH_MS`) — without it an exhausted groq rung was re-asked 399
times in one night.

**Limits read from the consoles on 2026-10-03, and what they add up to.** Both flash-lite models:
15 RPM / 250K TPM / 500 RPD. `gpt-oss-20b` and qwen: 30 RPM / 1K RPD / 8K TPM / **200K TPD** — the
token cap is what binds on both. One project therefore supplies ~1,330 calls a day (qwen ~160
checks, flash-lite 500 + 500, gpt-oss ~130, the two reasoning rungs 40), against ~2,400 for the
measured busy day (6,672 deliveries) and ~2,930 for 16 matches bowling for 8 hours. **The checker is
the ceiling**: qwen is token-bound at ~117 tokens a delivery, so it covers ~1,500 deliveries a day
however the writers batch, and every check past that lands on a Gemini writer rung — the weaker
checker (it passed "a maiden over" 3/3) and double the burn on that rung.

**A second Gemini PROJECT is four more buckets; a second key on the same project is none.** The
`-2` rungs (`GEMINI_API_KEY_2`, `account: "gemini-2"`) sit directly behind their twins: same model
ids, prompts and floors, so the probe verdicts carry over. `dailyExhausted` is keyed by rung name
and `paymentBlocked` by account, so the projects latch independently (pinned in
`provider-ladder.test.js`). Unset, each is skipped as `no GEMINI_API_KEY_2`.

Three traps, each of which looks like free capacity and is not. **An alias is not a bucket**:
bursting `gemini-flash-lite-latest` returns `quotaDimensions: {"model":"gemini-3.5-flash-lite"}` —
it bills what it resolves to. **A reasoning rung needs its own `tokenFloor`**: `gemini-3.6-flash` at
`max_tokens: 512` returns `completion_tokens: 18` against `total_tokens: 717` and
`finish_reason: length`, the gpt-oss trap one rung down, so those two rungs carry `tokenFloor: 2048`
while flash-lite keeps the caller's 100. And **a per-day 429 is not a per-minute one** — `unavailable`
is per-line because a rate limit clears by the next ball, which is true of the 15 RPM bucket and
false of the per-day one, so `dailyExhausted` latches until the Pacific reset. Both quotaIds are now
observed verbatim (per-day on 2026-09-24), and `drainUnknownQuotaIds` still reports any 429 matching
neither.

Rejected rungs, so nobody re-adds them: `gemini-3.1-flash-lite` (fast and a separate bucket, but
dropped "ramp" and invented "sharp bouncer" — a hallucination ships, where a timeout merely falls
through), `gemini-3.7-flash` and `gemini-flash-latest` (16s timeout / 503 against a 15s
`AbortSignal`), the Pro ids (429 on the first call), every `gemini-2.5-*` (404, "no longer available
to new users"), and **OpenRouter's `:free` models**, which answered "temporarily rate-limited
upstream" 429 on every call without even consuming the key's 50/day. Cerebras and sambanova left
`PROVIDERS` for the same reason and stay in `probeProviders()` alone, which is how a funded account
would still be noticed. Pinned by `provider-ladder.test.js` in both function dirs.

**On 2026-09-16 the chain was not capacity-starved** — it became so once every ball was rewritten
(see "Rewriting is batched" and the Gemini limits above). Measured 2026-09-16 over 13 days of
`match-commentary`: **94 `ai_generated = true` rows/day** on average and 367 on the busiest day —
~11% of groq's 1,000/day on its own. The number that matters is the other one: **0.7-8.3% of stored
commentary is AI-written**, so the tab is 91-99% raw Cricbuzz text. That is not an RPD problem. It
is `commentaryCapFor` returning 0 for unviewed matches, the slow tier's interval × per-pass cap, and
verification failing closed on a one-rung chain. The extra rungs fix the third and pay for raising
the first two — `SLOW_TIER_INTERVAL_MS` is 45 min *only* because 1,000/day was the whole budget
(`main.js` records the 15-minute option being rejected at 768/day). Concurrency for that sizing,
measured the same day over 312 hours: **mean 2.87 matches bowling at once, 4.28 while any cricket is
on, p90 10, peak 13**, with 33% of hours having none. Count a match live only if it wrote a
commentary row that hour — an open `match-live-detail` window says 7.06/19, because a Test holds its
row open overnight.

**A batch key is thirteen digits long and a model does not always copy them.** `generateBatch`
matched a reply line to its delivery by the exact `matchId|ball|timestamp` tag; for
`169910|26.4|1791530585051` gemini-flash-lite answered `…585050` four times of four, the line was
dropped with nothing in `attempts`, and the delivery read as `providers_exhausted` — two writer
calls a run for two hours (ANDROID-4Z). A tag whose match and ball belong to exactly one delivery
in the batch is now that delivery's; two deliveries sharing a label (a wide and the next ball) are
still told apart by timestamp alone and a miscopy between them is dropped. A dropped tag is written
to `attempts`. **`providers_exhausted` with no attempt recorded means an answer was thrown away,
not that nobody answered.**

**Three monitors were reporting things that were not so (2026-10-09).** `findCommentaryGaps` walks
a batch in timestamp order, and Cricbuzz's timestamps do not always follow the over: a run that
wrote 26.1–26.4 reported 26.3 and 26.4 lost (ANDROID-5H). A label elsewhere in the same batch is no
longer missing. `disagreement` compared the two sites' short names by a shared word, and Cricbuzz's
"Ahmadzai" and Cricinfo's "Yamin" share none, so every ball Yamin Ahmadzai bowled lost its second
report (ANDROID-5C); it now takes `playerNamesOf(detail)` — the full names on the match's own
Cricbuzz pages. The `batter`/`runs` disagreements that remain are the sites labelling balls one
apart after an extra, and leaving the second report out there is correct. And FCM's one retry
followed a 429 at once; it waits a second now (ANDROID-50).
