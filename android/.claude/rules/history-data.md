---
paths:
  - "appwrite/tools/history/**"
  - ".github/workflows/publish-history-builder.yml"
  - "app/src/main/kotlin/**/data/history/**"
---

# Historical match data (Cricsheet, GitHub Pages)

**Every past match the app shows ball-by-ball, and every record, comes from a static site, not Appwrite.** `appwrite/tools/history/build.mjs` turns Cricsheet's `all_json.zip` into JSON files. `appwrite/tools/history/workflow/build.yml` runs **inside the public repo** `tm-droid/bouncer-history` weekly (Monday 03:30 UTC) and publishes to its `gh-pages` branch, served at `https://tm-droid.github.io/bouncer-history/`. It is not on Appwrite because Free's 2 GB storage and 5 GB/month bandwidth cannot hold or serve it.

## Rules that are not visible in the code

- **The data repo is public, so only Cricsheet-derived data goes in.** Cricsheet is ODC-BY: re-hosting is allowed with attribution, which the site README and the app give. Cricbuzz and Cricinfo content must never be added.
- **The builder runs in the public repo, because public Actions minutes are free** and this repo's are billed (its Actions were blocked 2026-10-24 for failed payments).
  - `publish-history-builder.yml` force-pushes `appwrite/tools/history/` as one orphan commit to that repo's `main` (with `workflow/build.yml` as `.github/workflows/build.yml`), via the `HISTORY_DEPLOY_KEY` secret here. Edit the tool here, never there: the next publish replaces `main`. While private Actions are blocked, publish by hand (same copy, orphan commit, push).
  - `main` there must stay the **default branch**: scheduled workflows run only from it, and the repo's Actions workflow permission must be `write` (the build pushes `gh-pages` with `GITHUB_TOKEN`).
  - The tool cannot import from elsewhere in this repo; `src/names.js` copies `squash`/`venueKey` from train-win-probability, with a test that they agree.
  - GitHub disables scheduled workflows in a public repo after 60 days without repo activity (commits, issues, PRs). Whether the workflow's own `gh-pages` push counts is not documented; a publish to `main` surely does. If the weekly build stops, re-enable it in the Actions tab or run `gh workflow run build.yml -R tm-droid/bouncer-history`.
- **Each build is one orphan commit force-pushed over `gh-pages`.** A rebuild rewrites most files, and keeping history would grow the repo by the whole site every week.
- **Size.** First build (2026-10-14): 23,055 matches, 41,541 files, 343 MB, 36 s, 470 MB peak RSS.
  - The build fails over 900 MB, because Pages refuses a site over 1 GB.
  - `m/` (the match files) is 324 MB of it; `s/p/` (players) 76 MB.
  - **jsDelivr is not an option:** it refuses GitHub packages over 150 MB.
- **Verified against a real scorecard** (2011 World Cup final, `m/433606.json`): totals, overs, top scores and bowling figures match.
  - Archive-wide, batter runs plus extras equal the innings total in every innings.
  - About 0.3% of chase targets (52 of 17,421) differ from the first-innings total + 1. These are reduced-overs targets that Cricsheet does not tag with a method; it is the data, not the builder.
- **Afghanistan is missing on purpose** (the Cricsheet maintainer has withheld its matches since 2024-11-14). No rebuild will bring them back; the app says so rather than look empty, and takes Afghanistan scorecards, ball-by-ball and records from the match archive (`archive-data.md`).

## Layout (schema `v` 1, short keys because the files are the payload)

| Path | Contents |
|---|---|
| `manifest.json` | `v`, `built`, `matches`, `files`, `bytes`; written last |
| `idx/years.json` | `{year: {month: count}}` |
| `idx/m/YYYY-MM.json` | rows `[id, date, format, gender, team1, team2, winnerIdx, result, event, venue]` |
| `idx/search.json` | the same rows without the venue, every match |
| `idx/teams.json` | `[name, gender, key, matches]` |
| `idx/venues.json` | `[key, name, city, matches, lat?, lon?, src?]`, most played first; `key` is the `s/venue` file name; `src` `w` Wikidata, `c` city geocode; 4 cells when unplaced |
| `idx/weather.json` | `{matchId: w}`, the weather side index and the builder's self-cache |
| `m/<id>.json` | one match: `t` teams, `p` people `[name, registryId?]`, `xi`, `o` outcome, `inn[]` with `b` deliveries, `sc` scorecard, `ov` per-over `[runs, wkts]`, `w` weather (absent until fetched) |
| `s/team/<key>.json`, `s/h2h/<a>__<b>.json`, `s/venue/<key>.json`, `s/p/<registryId>.json`, `s/p/names/<letter>.json` | records |

- **A delivery** is `[over, ball, batter, bowler, nonStriker, batRuns, extraRuns, extrasBits, wickets?]`.
  - `ball` is the scorer's label: a wide or no-ball keeps the number of the ball it replaces.
  - Bits (`X` in `src/match.js`): 1 wide, 2 no-ball, 4 bye, 8 leg-bye, 16 penalty, 32 non-boundary.
  - A wicket is `[kind, outIdx, ...fielderIdx]`.
- **Format keys** (`formatKey`): Cricsheet's `T20` covers both T20 internationals and league T20s, so records use `T20I` for `team_type: international`.
- **Keys:** a women's side is `<slug>__w`; a pair is the two slugs sorted.
- **Players without a registry id** are left out of player records, because a bare name cannot be joined across matches.
- **Super overs** are kept in the match file (`so: 1`) but left out of every record.
- **Player records** (`s/p/<regId>.json`) are `{n, f:{format:[20 ints]}, tm:{side:matches}, m:[[id,date,format,team,opponent,runs,balls,out,wkts,ct]...]}`, newest match first. `m` has no result or event, so the career page joins the last 20 through `PastMatchIndex.cricsheetById` and keeps the record's own fields for a match the archive replaced. Ints 0-15: matches, innings, not outs, runs, balls, high score, high score not out, 50s, 100s, balls bowled, runs conceded, wickets, best wickets, best runs (-1 until bowled), catches, stumpings. Ints 16-19 (appended, schema `v` stays 1 because a reader of the first 16 is unaffected): fours, sixes, innings with 4+ wickets, innings with 5+ wickets. A `non_boundary` 4 or 6 counts as neither; wickets are the bowler's own (no run out, retired, obstructing). `CareerStats` shows them only when every format has 20 ints (`PlayerCareer.hasBoundaries`); an older record keeps `formatStatItems(hasBoundaries = false)`. `names/<letter>.json` is keyed by the surname's first letter (`["V Kohli", regId, team, matches, cricbuzzId?]`). Readers: `CareerStats`, `HistoryClient.names/player`.

- **Team and ground records** (`HistoryRecords`). `s/team/<key>.json` `f` is `{format:[played, won, lost, tied, drawn, no result]}` and feeds the team page's Records section. A ground is found from Cricbuzz's "Name, City" through `idx/venues.json` (`VenueKey.lookup`): the exact `slug(name + " " + city)` first, else the one row whose squashed name matches and whose city agrees; two candidates give null. `s/venue` `f` is `{format:[matches, first-innings runs, first innings counted, won batting first, won chasing]}`; Tests carry no win counts. The Info tab shows this card when Cricsheet has the ground in the match's format (T20 internationals are `T20I`, league T20s `T20`), and falls back to `context.venue` otherwise, so a ground never shows two cards. Until a rebuild publishes `idx/venues.json` the lookup finds nothing and the fallback shows.
- **Cricsheet splits one ground by city** (49 of 743 `idx/venues.json` names have a city-less row beside a city row: the MCG is `melbourne-cricket-ground` 13 Tests plus `-melbourne` 10). `VenueKey.lookup` returns every row with the same squashed name and a matching or missing city (max 3, two different cities is ambiguous), and `HistoryRecords.venue` sums their counters; one failed fetch is null, never a partial record.
- **Cricsheet register** (`src/register.js`). `build.mjs` reads `people.csv` and `names.csv` from beside `all_json.zip` and downloads them from `cricsheet.org/register/` when absent; a failed download builds without them (warned) instead of leaving the site stale. A header change throws, so ids never vanish silently.
  - Player records gain trailing `cb` (Cricbuzz id) and `ci` (Cricinfo id), only when the register has a plain numeric one. The register is sparse for Cricbuzz: 2026-10-24 build, `cb` on 50 of 13,696 players (0.4%, Kohli has none), `ci` on all but one. So `cb` is an exact join for a few players and never a substitute for the name join.
  - `names/` rows get a fifth cell, the `cb`, when known. Other names from `names.csv` become extra rows with the same regId under the alias's own surname initial, kept only when the alias surname differs from the primary (a spelled-out given name already matches by initials; 466 rows, +0.2 MB). Old builds read four cells and show an alias row as one more player; new builds `distinctBy` regId. Never put an array in a row: the old parser casts every cell to a primitive and drops the whole file.
  - `CareerJoin.link` takes a row whose `cb` equals the Cricbuzz profile id before it tries the name test; `HistoricalPlayerScreen` links to the live profile when the record has `cb`.
- **Player bios** (`src/wikidata.mjs`, Wikidata CC0 + Wikimedia Commons). `s/p/<regId>.json` gains a trailing `bio` `{b, bp, wp, img, ia, il, ilu, ip}` (all optional), joined through `ci` = Wikidata P2697. Old builds read named keys only and ignore it.
  - `b` birth date by Wikidata precision (`YYYY-MM-DD`, `YYYY-MM` or `YYYY`; less exact is dropped), `bp` birthplace (P19 label), `wp` English Wikipedia URL, `img` 330 px Commons thumbnail URL (P18; bytes are never stored), `ia` artist as plain text, `il` licence name, `ilu` licence URL, `ip` Commons file page. No death date is stored, so the app shows no age.
  - **Attribution rule:** Wikidata is CC0, but Commons licences are per file. A photo is kept only with a named licence that is not non-free/fair use, and the app must show `Photo: <ia>, <il>` linked to `ilu` (else `ip`).
  - Several SPARQL queries (one per property, joined by id): a single join times out at WDQS's 60 s. Birthplace labels come from `wbgetentities`, photos from the Commons API in batches of 50. Cached as `wikidata-cache.json` beside `all_json.zip` for 6 days; a failed query builds without bios (warned), or from a stale cache.
  - Batting/bowling style skipped: P2545 (bowling style) is on 1,159 of 31,716 cricketers and Wikidata has no batting-hand property.
  - 2026-10-24 build: `bio` on 7,162 of 13,696 players (52%), photo on 1,373 (10%), +1.1 MB.
- **Weather and ground coordinates** (`src/weather.mjs`, `venuecoords.mjs`, `openmeteo.mjs`). `w` is `[tmax 0.1 C, rain 0.1 mm summed, humidity % mean, wind km/h max, cloud % mean]` over the match's days (`d` plus `ds`-1), from the Open-Meteo archive (`temperature_2m_max,precipitation_sum,relative_humidity_2m_mean,wind_speed_10m_max,cloud_cover_mean`). Append new positions, never reorder; `PastWeather.of` needs 5. Old builds ignore `w` and the extra venue cells (`parseVenues` reads cells by index).
  - **Attribution rule: Open-Meteo is CC BY 4.0.** The Info tab shows "Weather data: Open-Meteo.com" under every weather line and the site README credits it; keep both. Ground coordinates: Wikidata (CC0) else Open-Meteo geocoding.
  - **Self-cache.** The weekly runner is empty, so the build first fetches the published `idx/weather.json` and `idx/venues.json` and asks Open-Meteo only for matches missing from them, at most `WEATHER_LIMIT` calls (default 4500, 0.7 s apart): the free tier is 600/min, **5,000/hour**, 10,000/day, so more than 5,000 in one run is refused. One request serves one ground's matches within 14 days (one call), newest first, so the archive fills over about two weekly runs (8,527 requests for 22,028 matches). Matches in the last 6 days wait (the archive lags). A 429 stops the run; any error builds without that weather, never fails. `--weather-cache <dir>` also keeps `weather-cache.json` there for a faster local fill; `--weather-limit n`, `--no-weather`.
  - **Venue coordinates:** Wikidata items that are a cricket field (Q682943 and subclasses) or have sport cricket, label/alias matched by squashed name; used only when within 100 km of the city's geocode (grounds share names), else the city point. 2026-10-24: 663 of 743 grounds placed (Wikidata 214, city 449); 80 have no city Open-Meteo knows, so their matches get no weather.
  - `appwrite/tools/weather-backfill.mjs` (model features) imports `chooseCandidate` from `openmeteo.mjs`. The app shows weather only for `cs-` matches (read from the match's own file).
