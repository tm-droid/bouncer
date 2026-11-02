# iOS rebuild ledger (branch `ios-rebuild`)

Spec: `/Users/tanushmalhotra/.claude/plans/take-a-look-at-humble-eclipse.md` (approved).
Build check after every unit: XcodeBuildMCP `build_sim` (scheme `Bouncer`, sim "iPhone Duo") with 0 errors.
One commit per unit, only paths under `ios/`.

| Unit | Scope | Acceptance | Status |
|---|---|---|---|
| U0 | Delete dead code, drop unused SPM packages, wire entitlements | build_sim passes; no RapidAPI/OpenRouter/Cricinfo/Vercel code left | done 5bb4f747 |
| U1 | Theme tokens, Plex fonts, components (`Bouncer/Theme`, `Bouncer/Components`) | build_sim passes; fonts registered at runtime | done 35a4bb0b |
| U2 | Data layer (`Bouncer/Data`), Realtime, flight parser, unit tests | build_sim + test_sim pass | done 60a36c16 |
| U3 | App shell: TabView, routes, appearance, FavouritesStore | build_sim passes | pending |
| U4 | Home screen | build_sim; live matches render in sim | pending |
| U6 | News list + article | build_sim; stories render | pending |
| U7 | Series list + More | build_sim; appearance toggle works | pending |
| U5 | Match detail (header, Live, Summary, Scorecard, Commentary, Squads, Info) | build_sim; all tabs render for a live match | pending |
| Review | Fresh reviewer on full diff, fix gaps, sim snapshots light+dark | no correctness gaps open | pending |

## Results
- U0 5bb4f747: 35 files removed, SwiftSoup dropped (UIImageColors kept for TeamLogoColorFetcher), entitlements wired, build_sim OK.
- U1 35a4bb0b: tokens, Plex fonts (PostScript IBMPlexSans, -Medm, -SmBld, -Bold; call BouncerFonts.register() at launch), 12 components. build_sim OK.
- U2 60a36c16: Data/{Core,Models,Scrape,Repositories,Realtime}, 35 tests pass. Storage has no ETag (client TTL only); fetchedAt is epoch ms; commentary event "NONE" for plain balls; commentary realtime channel shape unconfirmed.
- U3: running.
