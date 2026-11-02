# iOS rebuild ledger (branch `ios-rebuild`)

Spec: `/Users/tanushmalhotra/.claude/plans/take-a-look-at-humble-eclipse.md` (approved).
Build check after every unit: XcodeBuildMCP `build_sim` (scheme `Bouncer`, sim "iPhone Duo") with 0 errors.
One commit per unit, only paths under `ios/`.

| Unit | Scope | Acceptance | Status |
|---|---|---|---|
| U0 | Delete dead code, drop unused SPM packages, wire entitlements | build_sim passes; no RapidAPI/OpenRouter/Cricinfo/Vercel code left | done 5bb4f747 |
| U1 | Theme tokens, Plex fonts, components (`Bouncer/Theme`, `Bouncer/Components`) | build_sim passes; fonts registered at runtime | done 35a4bb0b |
| U2 | Data layer (`Bouncer/Data`), Realtime, flight parser, unit tests | build_sim + test_sim pass | done 60a36c16 |
| U3 | App shell: TabView, routes, appearance, FavouritesStore | build_sim passes | done 39ef3a5e |
| U4 | Home screen | build_sim; live matches render in sim | done 6f727c35 |
| U6 | News list + article | build_sim; stories render | done af92392e |
| U7 | Series list + More | build_sim; appearance toggle works | done bd353a3a |
| U5 | Match detail (header, Live, Summary, Scorecard, Commentary, Squads, Info) | build_sim; all tabs render for a live match | done bc643cc1 |
| Review | Fresh reviewer on full diff, fix gaps, sim snapshots light+dark | no correctness gaps open | done b32f0deb, 29892c91 |

## Results
- U0 5bb4f747: 35 files removed, SwiftSoup dropped (UIImageColors kept for TeamLogoColorFetcher), entitlements wired, build_sim OK.
- U1 35a4bb0b: tokens, Plex fonts (PostScript IBMPlexSans, -Medm, -SmBld, -Bold; call BouncerFonts.register() at launch), 12 components. build_sim OK.
- U2 60a36c16: Data/{Core,Models,Scrape,Repositories,Realtime}, 35 tests pass. Storage has no ETag (client TTL only); fetchedAt is epoch ms; commentary event "NONE" for plain balls; commentary realtime channel shape unconfirmed.
- U3 39ef3a5e: Route .match(id:String) .news(id:Int) .series(id:String); @Environment(AppEnvironment.self) .matches .news .series .favourites; placeholder screens under Features/. 35 tests pass.
- U4 6f727c35: Home All/Live seen in sim with real live matches + hero. Not seen: Upcoming/Recent grouping, filter menu, dark. Live refresh effectively 30s (repo TTL).
- U6 af92392e: News list seen (real stories). Article screen NOT seen (sim reset on tap) - verify in review.
- U7 bd353a3a: Series/More built, snapshot timed out - verify in review. Clear cache only clears URLCache.
- U5 bc643cc1: hero + Live/Summary/Scorecard/Commentary/Squads/Info seen with real data; realtime not observed (match at stumps). Tab accent always gold; innings chips show raw wire name. test_sim 35/35.
- Review b32f0deb: fresh reviewer found 8 defects (stale phase, chip/filter desync, Milestones auto-paging, restart re-reading first paint, stop/connect realtime race, Int64 trap on huge doubles, hasMore from filtered count, shouldPing ignoring context.startMs). All fixed; test_sim 40/40. No test for the stop/connect race.
- 29892c91: port Android displayOvers ("19.6" shows "20") in TeamRow, MatchRow, MatchHero. test_sim 41/41.
- Sim verification (iPhone Duo, iOS 27.1): Home All/Upcoming/Recent dark + light, filter menu, News list + article, Series list -> series matches -> finished match (hero, Summary, Scorecard), More light (teams Edit sheet, appearance, cache, version). Series "app disappeared" was test_sim relaunching the shared sim, not a crash.
- Open gaps: realtime commentary channel shape not observed live; tab accent always gold; innings chips show raw names ("RSA 1st Innings" vs hero "SA"); Clear cache clears URLCache only; Home live refresh effectively 30s.
