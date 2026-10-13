---
paths:
  - "app/src/main/kotlin/**/network/**"
  - "appwrite/functions/match-sync/src/cricbuzz.js"
  - "app/src/test/java/**/network/**"
---

# Cricbuzz Scraping

**Gender is read off the names** (`network/MatchGender.kt`). Cricbuzz's list has no gender field
and the scraper wrote a constant `"Men"`, so the filter in the ViewModel had nothing to separate.
Home's two filters are `HomeFilters` (`ui/HomeFilters.kt`), saved on change and restored once per
ViewModel; All matches reads `statusMatches`, the selected status before those filters.

**An empty Upcoming list is Cricbuzz's state, not a broken page.** `live-scores/upcoming-matches`
sometimes shows "Nothing to show" while fixtures exist, and the ~90 matches the scraper still
parses there are the site-wide live ticker, which the PRE_MATCH filter correctly rejects. Do not
switch the source: `/cricket-schedule/upcoming-series/…` lists series schedules, and switching to it
was reverted. When the scrape is empty, `LiveActivityViewModel.updateMatches` serves
`MatchCacheService.lastKnownUpcoming()` — the last non-empty Upcoming list this device saw, minus
fixtures whose `startDateMs` has passed. The `upcoming-matches` Storage file cannot stand in: it is
overwritten with the same empty list and carries no start times. Pinned by `UpcomingFallbackTest`.

**Series detail reads the series' own pages, and every series tab needs the slug.**
`/cricket-series/{id}/{tab}` answers 200 with no data for every tab; only
`/cricket-series/{id}/{slug}/{tab}` carries it, so `seriesSlugOf` reads the slug off the unslugged
page (`SeriesDetailScraper`, `PointsTableScraper`). Schedule walks `matchDetailsMap` groups — never
bare `matchInfo`, which is also the site-wide ticker on the same page. Stats is
`/stats?statsType=X`, server-rendered per type, and one page carries every format's table
(`initialStats.{odi|t20|test}StatsList`); a series that has not started has no stats section at
all, which is the normal answer, not drift. The Series tab's Past view is
`/cricket-scorecard-archives/{year}` (sections `internationalData`/`leagueData`/`domesticData`/
`womenData`); `/cricket-schedule/series/all` is current and upcoming only. Pinned by
`SeriesDetailFlightTest` and `SeriesListMergeTest`. The schedule opens on the next match
(`scheduleAnchorIndex`, pinned by `SeriesScheduleAnchorTest`).

**Team sheets come from `/cricket-match-squads/{id}`, fetched only when the Info tab opens.** The page is server-rendered (unlike `/live-cricket-over-by-over/{id}`, which is client-rendered and carries no per-over data server-side — the same dead end as the full-commentary page): its flight stream holds `{team1: {team, players}, team2: …}` where `players` is keyed `"playing XI"` + `"bench"` after the toss and `"Squad"` before it. `MatchSquadsRsc` parses it; `MatchSquadsSource` caches an XI for the process and a pre-toss squad for 10 minutes. No Appwrite cost. Aligned to `match.teams` by team id. Pinned by `MatchSquadsRscTest` against both captured shapes.

**Cricbuzz profile pages are App Router.** `/profiles/{id}/{slug}` carries no `__NEXT_DATA__` block — only `self.__next_f.push(...)` chunks — and the legacy Jsoup selectors (`cb-font-40`, `cb-plyr-inf`, `cb-plyr-thead`, …) match **zero** elements on it. Both of `PlayerProfileScraper`'s original strategies were therefore dead, and the fallback produced a profile with a name and every other field empty rather than an error. `PlayerProfileRsc` reads the flight stream via `forEachNextFlightPayload` — the same parser the match pages use — and runs first; the other two stay as fallbacks. Pinned by `PlayerProfileRscTest` against captured markup.

**Scorecard player names are in a span, not the row's link.** A `div.grid.scorecard-bat-grid` /
`scorecard-bowl-grid` row's only `<a>` elements are a hidden per-player menu — "View match
performance", then "View profile" pointing at `/profiles/{id}/{slug}` — while the name sits in a
`span` whose class carries `hover:underline`. `row.select("a").first()` therefore named **every**
batter and **every** bowler "View match performance"; those names were written to `match-scorecard`
by `bouncer-match-sync` and rendered by the Scorecard tab, where `items(..., key = { it.name })`
turned the collision into `IllegalArgumentException: Key … was already used` (Sentry ANDROID-40).
`playerNameOf` (both `CricBuzzParser.kt` and `match-sync/src/cricbuzz.js`) reads the span first and
falls back to a non-menu link. Pinned by `CricBuzzScorecardRowFilterTest` against captured markup,
with `cricbuzz.js` declared as a Gradle test input so a one-sided edit fails the build.
**Never key a lazy list on scraped text** — keys must include the index.

**That menu is in the fall-of-wickets cell too, and partnerships are a different grid.** The
ANDROID-40 fix reached only the batting and bowling grids. A `div.grid.scorecard-fow-grid` row has
three cells and the first holds the name span *and* the same hidden menu, so both parsers named
every dismissed batter `KL RahulView match performanceView profile` — rendered as "KL RahulView
match", and, because `mapFallOfWickets` looks the batter up in `innings.batsmen` by name, followed
by a `0 (0)` where their score should be. `score` is the **runs half alone** (`"37"`, not `"37-1"`)
in both parsers: `OverviewDerivedData` reads it with `toIntOrNull`, for which the joined form is
silently 0. Separately, partnerships render as `div.scorecard-pship-grid` and never as fow-grid
rows, so the header walk that looked for a "Partnerships" section among them returned nothing on
every match — `innings.partnerships` was always empty and the Partnerships card never appeared.
Kotlin also had no partnerships parser at all, and left `extras`/`total` blank. Pinned by
`CricBuzzFowPartnershipTest`. The Kotlin FOW parser had been dead since the App Router rewrite and
its unit test kept passing, because that test asserted a hand-written `45-1 (D Warner, 8.2)` text
blob rather than captured markup — the same trap the paragraph above records.

**A Test side's score is two innings, and the two data paths both used to drop one.** "266 & 83/2"
is one side's score, and both producers collapsed it: the match list's `latestInnings` returned
`inngs2` if present else `inngs1`, and `match-sync`'s `parseLiveDetail` read `inningsScoreList[0]`
and `[1]` — innings by index, which is per-team only in a limited-overs match, so a Test's third and
fourth entries went nowhere. `teamInningsScore` (`network/TeamInningsScore.kt`) and
`teamInningsSides` (`match-sync/src/cricbuzz.js`) now aggregate per side and join with `" & "`,
dropping the wicket count from a completed innings **in TEST/FC only** — limited-overs output is
unchanged. Pinned on both sides by `MultiInningsScoreParityTest`. `parseScore` used to delete `&`
outright, which left the joined string parsing as neither innings and every numeric consumer at
zero; it reads the last segment (the innings being batted) and keeps the whole string for display.

**A scraped site's 5xx and a cancelled coroutine are not app errors, and neither is told by its
name.** `ScrapeFetcher` throws `ScrapeHttpException(code)`; `isUpstreamOutage` walks the cause chain
for a 5xx and `AppResult.Error.report()` and the ViewModels skip it (ANDROID-54/56) — a 4xx still
reports. `SentryOptionsConfig` tests the throwable itself for `CancellationException`: R8 renames
library-internal classes, so `type.endsWith("CancellationException")` never matched Compose's in a
release build (ANDROID-53, whose site was `PlayerProfileScraper.attemptScrape` catching it as a
failed scrape). `ScrollableTabRow`'s default indicator indexes `tabPositions[selectedTabIndex]`
unguarded; a row whose tab count changes passes its own (`SearchTabs.kt`, ANDROID-52).
