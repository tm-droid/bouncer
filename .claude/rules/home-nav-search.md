---
paths:
  - "app/src/main/kotlin/**/viewmodels/**"
  - "app/src/main/kotlin/**/ui/navigation/**"
  - "app/src/main/kotlin/com/tanush/bouncer/ui/*.kt"
  - "app/src/main/kotlin/**/utils/SearchText.kt"
---

# Home, Navigation, Series, News and Search

## Home

**Home paints from disk, and a fetch that fails is a failure.** `LiveActivityViewModel` seeds Live, Upcoming and Recent from `MatchCacheService.getOfflineMatches` before any request answers, then fetches the three concurrently. `cachedMatches` therefore holds seeded lists too: "has this status been fetched" is `freshStatuses`, never `cachedMatches.containsKey`. `CricBuzzScraper.fetchLiveMatches` throws `MatchListUnavailableException` when it cannot fetch; it used to return an empty list, which the caller stored (offline showed "no matches" and wrote `[]` over the saved list). An empty list from it is a real answer. The list is published before the win-probability file is refreshed and again after. Pinned by `LiveActivityColdStartTest`.

**The Live list is scraped every 6 s only where a list of live scores is on screen** (`LIVE_LIST_ROUTES`: Home, All matches) and once a minute elsewhere, including under an open match, where it used to stop. The overlay reads the list's current entry (`currentListEntry`), so `MatchDetailViewModel.onMatchUpdated` runs on each emission; with a frozen entry and a stopped poll, "the matches list outranks a row that stopped being written" could not happen on the tap path.

## Navigation

**There is no Matches tab.** The bottom bar is Home / Series / News / More. Home lists every match; its "All matches" link pushes `AllMatchesScreenV2`. `SeriesListScreen` is the Series tab, no longer a row under More; `series/…` and `points-table/…` highlight Series, `match/…` highlights Home.

**A deep link is consumed once and cleared by the activity** (`ui/DeepLinks.kt`). It was remembered by its URI string, and every alert for a match carries the same URI, so the second alert tapped for a match opened nothing. A match link opens through `onMatchClick` (the overlay) after `resolveMatch` has waited for the first fetch and loaded the statuses not yet fetched.

**The More tab is Settings, Appearance, About (and DevTools in debug), nothing else.** Its "Explore" grid was removed in September 2026 because none of its four tiles was a feature: Stats opened a hub whose Analytics page was hardcoded (a fixed "V Kohli 9.4" leaderboard and an India-vs-Australia bar); Trophies and Achievements opened one badge gallery where 3 of 24 badges could ever unlock; Activity (`IntelligenceFeedScreenV2`) was navigated to with no match and no ViewModel, so it was always empty. That feed was the only caller of `LiveActivityViewModel.holdCommentary`, so the device-side commentary hold, `probabilityEvents`, `aiCommentary` and `keyEvent` went with it. The match-detail screen's own commentary subscription and presence ping (`MatchDetailViewModel`) are unaffected. Do not re-add a tile without a real data source behind it.

**Diagnostics can be switched off, and every doubt resolves to off** (`Diagnostics.kt`). The opt-out is a file in `filesDir`, read before Sentry starts with no dependency on `PreferenceStore` — an unreadable store answers every read with its default, and the default is "on". It is in both backup rule sets. `sendDefaultPii` is off. Do not move the choice back into a preference.

## News

**News keeps its last headlines and opened bodies** (`NewsDiskCache`, JSON files in the cache directory; no Room). Images are asked for at the size drawn (`NewsArticleData.imageUrlAt`): Cricbuzz's image host renders any `WxH`. Server-side body rules (`bouncer-news-sync`) are in `appwrite-functions.md`.

## Series and player caches

**The Series tab and the player profile screen read their caches.** `SeriesListViewModel` paints from the Room `series_cache` first and then merges the scrape, keeping cached rows when the scrape fails. It used to live-scrape on every open: it could not show a series search could find (that table holds `active-series`, active **plus archived**), showed nothing offline, and replaced the list with an error screen on any failure. `PlayerDetailScreenV2` and `PlayerComparisonViewModel` go Room → `AppwritePlayerProfilesClient.pullProfile` → scrape via `PlayerCacheWorker.cachedProfile`; both used to scrape unconditionally, so the profile disagreed with search about the same player. `statsJson` had no reader until `deserializeStats` existed.

## Search

**Search ranks in memory over what the app already holds, and it carries the Cricbuzz id.**

- Every comparison goes through `normalizeForSearch` + `SearchScore.of` (`utils/SearchText.kt`): accent-folded, punctuation-free, word by word for multi-word queries, with optimal-string-alignment distance so a swapped pair ("kolhi") is one edit.
- Corpora: `LiveActivityViewModel.allMatches` (every cached status, **not** `currentMatches`, which Home's filters already narrowed); `TeamAcronymManager.allTeams()` (~1,000 curated sides and codes; a query equal to a curated code scores `ACRONYM` and counts as an exact entity, so "mi" is not fuzzy-corrected); all of `series_cache`; the name columns of `player_cache`. Loaded once per screen by `SearchCorpusLoader` and ranked in Kotlin.
- **Do not reintroduce SQL-side search.** The DAOs' `LIKE … LIMIT 5/10` searches had no `ORDER BY`, dropped rows before ranking, and matched a player on `country` ("india" listed every cached Indian player).
- `expandedSearchQueries` expands competition acronyms per word ("ipl 2026").
- Network players are re-scored by the device (`mergePlayers`) instead of a flat 0.9, keep their `cricbuzzId`, and the player route carries it (`player/{name}?cbId=`). `scrapeProfile` then skips the name search whose `first()` opened the wrong profile for two players sharing a name, and the cache row is `cb_{id}` so a name-keyed row cannot mask the right player.
- The server's `rankPlayers` mirrors the normalisation and typo tier; both are pinned to `player-search/test/fixtures/search-normalization.json` by `SearchNormalizationParityTest` and the function's own test.
- A score tick re-runs only the local leg (`onSourcesChanged`), never the billed player lookup.
