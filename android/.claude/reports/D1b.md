# D1b: first-class through the Test head

## Changed
- appwrite/functions/match-sync/src/winprob.js:744 `evaluateLong` accepts TEST and FC (T10, UNKNOWN stay undetermined).
- winprob.js:757-759 team strength reads the MDM key for FC, TEST key for Test. `evaluate` (limited overs) on FC stays undetermined.
- appwrite/functions/match-sync/test/format-routing.test.js: FC scores as a real three-class triple; FC reads MDM strength and ignores a TEST-only key (and the reverse for Test); T10/UNKNOWN refused.
- main.js unchanged: FC already routes to `evaluateLong`; `orientToTeam1` subtracts the draw; `exportTeams` (asOfState.js 240-251) exports MDM keys.

## Tests
- `node --test appwrite/functions/match-sync/test/format-routing.test.js`: tests 9, pass 9, fail 0.
- Revert check (HEAD winprob.js): pass 7, fail 2, both new FC tests.
- `npm --prefix appwrite/functions/match-sync test`: tests 500, pass 500, fail 0.

## Android (read only, no edits)
FC renders correctly; nothing is gated on format.
- WinProbabilityStore.kt:41,114 maps drawProbability to drawPercent (0-100).
- MatchLiveDetailAlignment.kt:70 and :55 subtract the draw when re-orienting; :103 carries winProbabilityDraw.
- MatchDetailViewModel.kt:369 and MatchDetailViewV2.kt:174-181 pass the draw through; :194-199 clears it only when the match is completed.
- ui/components/v2/WinProbabilityBar.kt:36-38,66-72 draws the neutral draw slice whenever the draw is non-null.
