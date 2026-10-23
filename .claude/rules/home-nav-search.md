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

**The Live list is scraped every 6 s only where a list of live scores is on screen** (`LIVE_LIST_ROUTES`: Home) and once a minute elsewhere, including under an open match, where it used to stop. The overlay reads the list's current entry (`currentListEntry`), so `MatchDetailViewModel.onMatchUpdated` runs on each emission; with a frozen entry and a stopped poll, "the matches list outranks a row that stopped being written" could not happen on the tap path.

**Home's top card is the followed team's** (`featuredFor`, `ui/HomeFeatured.kt`, pinned by `HomeFeaturedTest`): a followed live match in favourites order, else a followed match starting within 24 h, else any live match, else none. The rows below leave out whatever the card shows. See `design-system.md`.

**One offline banner, recorded even over fresh lists.** `pollFailureNeedsBanner` (`LiveActivityViewModel`) shows the failure when the status is on screen and either its list is not fresh or the failure is `Offline`. A server blip over fresh scores stays quiet, but offline the scores stop moving and the banner, with its saved-at time, is the only signal Home gives. Failures after a successful fetch used to be dropped.

**Rows lead with the side that is batting** (`MatchCardV2`: the other side steps back), crests are 28dp, an unstarted match shows a countdown ("in 2h 15m") until 12 h out and then the day and time (`MatchStartLabel.kt`; `MatchStartTimeTest` pins the status-line-first parse), and a Test shows its innings.

## Navigation

**There is no Matches tab, and no All matches screen.** The bottom bar is Home / Series / News / More. Home lists every match: its Upcoming and Recent tabs head each day (`HomeMatchRows.kt`; Recent newest first), and its filter menu holds competition, gender, format and "Only my teams" (`HomeFilters.kt`; format and my-teams are applied by Home and saved in prefs). `AllMatchesScreenV2` was Home again with a format filter and a By date tab; both moved here and the screen, its route and its link went (October 2026). `app://bouncer/matches` opens Home. `SeriesListScreen` is the Series tab; `series/…` highlights Series, `match/…` Home.

**A page opened from any tab keeps that tab lit** (`BouncerScreen.ownerTab`, `sharedRoutes`): a player, a team, search, the comparison, Alerts. `player/` and `team/` used to light up More wherever they were opened from. Pinned by `BouncerScreenInitOrderTest`.

**Series detail's Points table is a tab, not a link** (`PointsTableTab`). The tab used to push a separate `points-table/{id}` screen titled "Tournament Standings" for every series; the route is gone. A standings row opens its team, and T/NR appears only when a side has one. Series stats rows carry the player's Cricbuzz id into the player route.

**A deep link is consumed once and cleared by the activity** (`ui/DeepLinks.kt`). It was remembered by its URI string, and every alert for a match carries the same URI, so the second alert tapped for a match opened nothing. A match link opens through `onMatchClick` (the overlay) after `resolveMatch` has waited for the first fetch and loaded the statuses not yet fetched.

**The More tab is the settings.** Your teams (each row opens its team page; Edit pushes the favourites editor), notifications, an inline Light/Dark/System picker, data and behaviour, and About / Redo setup / DevTools in debug. `SettingsScreenV2` and `AppearanceScreenV2` are gone; `app://bouncer/settings`, `appearance` and `favourites` open More. The "Auto-refresh", "Win probability" and "AI insights" switches went with them: each was saved and read by nothing. Do not add a switch nothing reads.

**Alerts is one screen** (`AlertsScreen`, behind Home's bell; `reminders` opens it on its own tab): Inbox, Following (`MatchSubscriptionManager`'s cards and alert prefs, each row Home's match row with its bell) and Reminders. Inbox and reminder rows open their match by id (the overlay, or `match/{id}`, which waits for the list).

**The match overlay opens routes beneath it** (`LocalOpenRoute`): its series link, the header's teams, scorecard and team-sheet players. Backing out of that page reopens the match.

**Team detail is the team's hub**: a favourite star (the long-press sheet now offers one item per side; it used to add or remove only the first), cached fixtures and results, and the last named eleven, joined to the team sheet on Cricbuzz team id.

**The More tab used to be Settings, Appearance, About.** Its "Explore" grid was removed in September 2026 because none of its four tiles was a feature: Stats opened a hub whose Analytics page was hardcoded (a fixed "V Kohli 9.4" leaderboard and an India-vs-Australia bar); Trophies and Achievements opened one badge gallery where 3 of 24 badges could ever unlock; Activity (`IntelligenceFeedScreenV2`) was navigated to with no match and no ViewModel, so it was always empty. That feed was the only caller of `LiveActivityViewModel.holdCommentary`, so the device-side commentary hold, `probabilityEvents`, `aiCommentary` and `keyEvent` went with it. The match-detail screen's own commentary subscription and presence ping (`MatchDetailViewModel`) are unaffected. Do not re-add a tile without a real data source behind it.

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
- The server's `rankPlayers` mirrors the normalisation and typo tier; both are pinned to `player-search/test/fixtures/search-normalization.json` (cases and `tolerance`) by `SearchNormalizationParityTest` and the function's own test.
- **Typo tolerance steps with length** (`SearchScore.editTolerance`): 0 edits for 1-3 chars, 1 for 4-7, 2 for 8+. "in" no longer fuzzy-matches "won"; "kolhi" (a swap, 1 edit) still finds Kohli. Change it in `SearchText.kt`, `playerIndex.js` and the fixture together.
- **`parseQuery`** (`utils/SearchQueryParser.kt`) pulls year/season, format (TEST/ODI/T20I/T20), stage (FINAL/SEMI/QUALIFIER) and an "a v b" pair out of the normalised query. Facets only boost (+0.02 each, clamped below the next band floor); a pair whose two sides resolve to teams (`resolveSide`) is *required* of a match and scores at the word-prefix band. Names are scored on the entity words as well as the full text.
- Within a score: live, then recency (finished newest first, upcoming soonest first), then favourites. Teams: favourites, fixture count, corpus order. Match fields are normalised once per list (`MatchSearchEntry`, keyed by list identity).
- Recents are saved on IME search as well as on a result tap. With nothing live, idle suggestions are the newest-started running `series_cache` series (max 6), then the fixed list only if the table is empty.
- A score tick re-runs only the local leg (`onSourcesChanged`), never the billed player lookup.
