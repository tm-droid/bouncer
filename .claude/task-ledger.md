# Goal: Archive + history in search and beyond, better search ranking, M3 refresh indicator

Plan (full specs, research, decisions): /Users/tanushmalhotra/.claude/plans/so-recently-we-made-streamed-corbato.md
Resume: read the plan's unit spec, then find the first `[ ]` below. Previous ledger (design upgrade, complete) is in git history.

B = app/src/main/kotlin/com/tanush/bouncer · T = ./gradlew testDebugUnitTest --tests · D = detekt under JBR 21 · V = assembleDebug testDebugUnitTest lintDebug + D
Commit per unit (no push until the end). Every commit touching app/src/main/ rewrites distribution/whatsnew/whatsnew-en-US to cover all units so far (≤500 chars).

## Units
- [x] U0 (66e39ba2): M3 refresh indicator (disc + gold arc, haptic, reduced motion), delete CricketBallPullRefresh | files: B/ui/components/v2/PullRefreshWrapper.kt, LightModeContrastTest | done when: V green, no CricketBall refs
- [x] U1 (3a650a5e): foundations (static-site cache, models, HistoryTeams, CricsheetScorecard, PastMatches + cs- guards) | done when: V + T *CricsheetScorecardTest *PastMatchesTest *HistoryTeamsTest *MatchDetailPastMatchTest
- [x] U2 (9a670335): search ranking (query parser, typo thresholds w/ server parity, tier ordering, precompute, recents on submit, trending) | done when: V + T *Search* + node --test appwrite/functions/api
- [x] U3 (a97af42c): past-match search (PastMatchIndex, PastMatchRow, Past matches section) | done when: V + T *PastMatchIndexTest
- [x] U4 (2eb11238): Afghanistan notice | done when: V + test
- [x] U5 (b04c243d): historical players + career page + player-page link | done when: V + T *PlayerNameMatchTest *CareerStatsTest *CareerJoinTest *SearchMergeTest *BouncerScreenInitOrderTest
- [x] U6 (6b8a17dc): fuller past matches (Cricsheet card fallback, overs/worm, evalLog win-prob) | done when: V + T *ArchivedMatchTest *ProbabilityHistoryTest
- [x] U7 (1b12f827): head-to-head from s/h2h (match card all formats, search pair card) | done when: V + T *CricsheetH2HTest *H2HRepositoryKeyTest
- [x] U8 (017d7e2f, db23fed8): team records + venue records (builder idx/venues.json) | done when: V + T *VenueKeyTest *TeamRecordTest + node --test appwrite/tools/history
- [x] U9: CUT by owner 2026-10-24 ("gimmicky, not the vibe of the app") — do not build
- [x] Final (pushed 6d834cf0; Play internal+alpha 1.0.11 versionCode 1673, notes 490 chars; history rebuilt with idx/venues.json): emulator verification (plan's Verification list), whatsnew polish, git push (CI will not run: Actions minutes exhausted 2026-10-23), LOCAL Play upload: versionCode = highest on Play + 1 (query edits API with ~/.config/bouncer/play-service-account.json), BOUNCER_VERSION_CODE=… ./gradlew bundleRelease, upload via Play Developer API to tracks internal + alpha with whatsnew notes; history rebuild for venues runs locally (build.mjs + push gh-pages) since Actions is out
