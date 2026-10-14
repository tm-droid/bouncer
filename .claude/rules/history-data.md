---
paths:
  - "appwrite/tools/history/**"
  - ".github/workflows/build-history.yml"
  - "app/src/main/kotlin/**/data/remote/History*.kt"
---

# Historical match data (Cricsheet, GitHub Pages)

**Every past match the app shows ball-by-ball, and every record, comes from a static site, not Appwrite.** `appwrite/tools/history/build.mjs` turns Cricsheet's `all_json.zip` into JSON files. `.github/workflows/build-history.yml` publishes them weekly (Monday 03:30 UTC) to the `gh-pages` branch of the **public** repo `tm-droid/bouncer-history`, served at `https://tm-droid.github.io/bouncer-history/`. It is not on Appwrite because Free's 2 GB storage and 5 GB/month bandwidth cannot hold or serve it.

## Rules that are not visible in the code

- **The data repo is public, so only Cricsheet-derived data goes in.** Cricsheet is ODC-BY: re-hosting is allowed with attribution, which the site README and the app give. Cricbuzz and Cricinfo content must never be added.
- **Each publish is one orphan commit force-pushed over `gh-pages`.** A rebuild rewrites most files, and keeping history would grow the repo by the whole site every week.
  - Pushes use the deploy key whose private half is the `HISTORY_DEPLOY_KEY` secret on `tm-droid/bouncer`. There is no other copy; to rotate it, make a new key pair and replace both halves.
- **Size.** First build (2026-10-14): 23,055 matches, 41,541 files, 343 MB, 36 s, 470 MB peak RSS.
  - The build fails over 900 MB, because Pages refuses a site over 1 GB.
  - `m/` (the match files) is 324 MB of it; `s/p/` (players) 76 MB.
  - **jsDelivr is not an option:** it refuses GitHub packages over 150 MB.
- **Verified against a real scorecard** (2011 World Cup final, `m/433606.json`): totals, overs, top scores and bowling figures match.
  - Archive-wide, batter runs plus extras equal the innings total in every innings.
  - About 0.3% of chase targets (52 of 17,421) differ from the first-innings total + 1. These are reduced-overs targets that Cricsheet does not tag with a method; it is the data, not the builder.
- **Afghanistan is missing on purpose** (the Cricsheet maintainer has withheld its matches since 2024-11-14). No rebuild will bring them back; the app must say so rather than look empty.

## Layout (schema `v` 1, short keys because the files are the payload)

| Path | Contents |
|---|---|
| `manifest.json` | `v`, `built`, `matches`, `files`, `bytes`; written last |
| `idx/years.json` | `{year: {month: count}}` |
| `idx/m/YYYY-MM.json` | rows `[id, date, format, gender, team1, team2, winnerIdx, result, event, venue]` |
| `idx/search.json` | the same rows without the venue, every match |
| `idx/teams.json` | `[name, gender, key, matches]` |
| `m/<id>.json` | one match: `t` teams, `p` people `[name, registryId?]`, `xi`, `o` outcome, `inn[]` with `b` deliveries, `sc` scorecard, `ov` per-over `[runs, wkts]` |
| `s/team/<key>.json`, `s/h2h/<a>__<b>.json`, `s/venue/<key>.json`, `s/p/<registryId>.json`, `s/p/names/<letter>.json` | records |

- **A delivery** is `[over, ball, batter, bowler, nonStriker, batRuns, extraRuns, extrasBits, wickets?]`.
  - `ball` is the scorer's label: a wide or no-ball keeps the number of the ball it replaces.
  - Bits (`X` in `src/match.js`): 1 wide, 2 no-ball, 4 bye, 8 leg-bye, 16 penalty, 32 non-boundary.
  - A wicket is `[kind, outIdx, ...fielderIdx]`.
- **Format keys** (`formatKey`): Cricsheet's `T20` covers both T20 internationals and league T20s, so records use `T20I` for `team_type: international`.
- **Keys:** a women's side is `<slug>__w`; a pair is the two slugs sorted.
- **Players without a registry id** are left out of player records, because a bare name cannot be joined across matches.
- **Super overs** are kept in the match file (`so: 1`) but left out of every record.
