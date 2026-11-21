---
paths:
  - "appwrite/tools/archive/**"
  - ".github/workflows/publish-archiver.yml"
  - "app/src/main/kotlin/**/data/history/Archive*"
---

# Match archive (Bouncer's own matches, forever, on GitHub Pages)

**Every match Bouncer follows is kept here after Appwrite prunes it.** That covers its commentary rows, final scorecard, live detail, `mc-` context with the summary, and `wpe-` win-probability log. `match-commentary` keeps 14 days and `mc-` files 14 days, and Appwrite Free cannot hold more. The site is the `gh-pages` branch of the **public** repo `tm-droid/bouncer-archive`, served at `https://tm-droid.github.io/bouncer-archive/`.

It is a different thing from the Cricsheet site (`history-data.md`): that one is every international and major league match since 2003, rebuilt weekly; this one is only what Bouncer saw, appended to and never rebuilt.

## Rules that are not visible in the code

- **The repo is public by the user's decision (2026-10-14), with everything per match in it.**
  - The commentary is Bouncer's own text: rewritten by the LLM chain or written by `plainLine`.
  - **A row holding Cricbuzz's raw text is never archived.** Those are rows whose `provider` is `cricbuzz` or `cricbuzz:<reason>`. `archivedRow` drops them, and a test pins it.
  - Scorecards and details are structured facts from Cricbuzz pages. Never add page HTML or any other site's prose.
- **Where it runs.** The archiver runs in the public repo, every 3 hours (`workflow/archive.yml`, published there as `.github/workflows/archive.yml`), because a public repo's Actions minutes are free.
  - This repo's CI used about 1,600 minutes in the first 14 days of October 2026.
  - The same repo runs the Appwrite pause alarm (`workflow/appwrite-alive.yml`, every 6 h) for the same reason. Its scheduled runs are attributed to tm-droid, so a failure emails the owner.
  - The source lives only here, in `appwrite/tools/archive/`. `publish-archiver.yml` force-pushes it, with every `workflow/*.yml`, to the archive repo's `main` as one orphan commit on every change, with the deploy key in `ARCHIVE_DEPLOY_KEY`. Never edit the archive repo's `main` directly.
- **The site is appended to, never rebuilt.** Its source is gone from Appwrite after 14 days, so a lost site cannot be regenerated. Each run is a normal commit on `gh-pages`, never a force-push.
- **Each commentary row is read once.**
  - Rows are read by a `$createdAt` watermark in `state.json`, with a 5-minute overlap merged by row id. Reads bill per row returned.
  - A run reads at most `ARCHIVE_MAX_ROWS` (60,000). A run that hits the cap finalises nothing, because it has not seen every match's newest rows.
  - **The watermark lives in the site, so a failed push re-reads everything.** From 2026-11-04 to 2026-11-21, GitHub push protection refused every `gh-pages` push. Every run re-read all rows since Nov 4 (25,551 rows in the last one, at 8 runs a day), and that spent the month's Appwrite read quota. Two rules followed:
    - **The archiver refuses to read when the site's `manifest.updated` is over 12 h old** (`stalePublish`). After fixing a publish, run it once with `gh workflow run archive.yml -R tm-droid/bouncer-archive -f catch_up=true`.
    - **An archived row's `id` is the first 16 characters of its Appwrite id** (`rowKey`). A 36-hex id fits GitHub's "Databricks token" pattern about 0.3% of the time, and a match file is one line, so the whole file is rescanned on every rewrite. `mergeRows` shortens old full ids on rewrite. The app never reads `id`.
- **When a match is final.**
  - **Ended:** the recent list (20 rows, about a day) calls it finished, or, once its rows have been quiet for 6 h, its detail row's status does. It is final after 6 h of quiet and 6 h since it was seen ended, which leaves room for the rewrite backlog and the finalising run's summary and `wpe-` log.
  - **Stale:** a match nothing calls finished is final after 4 days with no rows.
  - Rows that arrive after finalising are still merged in.
  - **The prune deletes only rows of final matches created before the archive's watermark** (`bouncer-api`'s end-of-cycle prune, `appwrite-functions.md`), so a slow archive leaves rows in Appwrite rather than losing them. `mc-` files are still pruned after 14 days by match-sync and are copied only at finalisation, so the 4-day stale rule must stay well under that.
- **The manifest feeds the prune.** It carries `watermark` and `cycle` (`start`, `end`, `commentaryRows`). The count is this billing cycle's commentary rows: counted from the site's own files when a cycle starts, then incremented by each row a run adds (re-read rows merge into their own id). It matched Appwrite's total exactly on 2026-10-15 (4,775). The anchor is `CYCLE_ANCHOR_MS`/`CYCLE_DAYS` in `src/archive.js`.
- **The API key is read-only** (`rows.read`, `files.read`), stored as the archive repo's `APPWRITE_API_KEY` secret. The workflow has no `pull_request` trigger, so a fork can never run it with the secret.
- **Size.** Pages refuses a site over 1 GB, and the run fails over 900 MB.
  - Measured on the backfill: about 285 B a row, and about 4,000 rows a day (60k in 15 days), so roughly 35 MB a month and 2 years per site. The largest match file is a Ranji game, 1,570 rows and 600 KB raw; Pages serves it gzipped. Replace this with a measured month.
  - When full, the next site is a new repo (`bouncer-archive-2`); the app would then read both.
- **Scheduled workflows in a public repo stop after 60 days without repository activity.** Whether the workflow's own commits count is unverified. Check the run list after the first quiet stretch with no matches.

## Layout (schema `v` 1)

| Path | Contents |
|---|---|
| `manifest.json` | `v`, `updated`, `matches`, `files`, `bytes`, `watermark`, `cycle` |
| `state.json` | the watermark; `cycle` (`start`, `rows`); per pending match `lastRowMs`, `endedSeenMs` and its list entry; `seen`, every match-list entry (upcoming, live, recent) for 30 days, so a match that leaves the 20-row recent list before it is final still has its series and teams |
| `idx/years.json` | `{year: {month: count}}` |
| `idx/m/YYYY-MM.json` | `[id, date, format, gender, team1, team2, result, series, venue]`, newest first; `unknown.json` for a match with no date at all |
| `m/<cricbuzzId>.json` | `v`, `id`, `final`, `finalisedAt`, `rows` (the `match-commentary` columns under their own names plus `id`, in `sort_key` order), `meta` (its match-list entry), `detail`, `scorecard`, `context` (`mc-`), `evalLog` (`wpe-`), `partial: 1` when neither a list entry nor a context was available |

- **Backfill (2026-10-15 00:05 UTC, from a Mac, with the read-only key).** 60,301 rows, back to 2026-09-30, in two runs (the first hit the 60,000 cap and finalised nothing): 86 matches final, 42 pending, 19.2 MB. Every archived match tested matched Appwrite's row count (170697: 229 of 229). For those matches the `mc-` file and the list entry were often gone already, so they are `partial` and their index row has short team codes from the detail row.
- **Backfill of Afghanistan, scorecards only (2026-10-24, `backfill-cricbuzz.mjs`, run from a Mac).** Cricsheet withholds every Afghanistan match, so 387 men's Tests, ODIs and T20Is (2009-2026) and Afghanistan Premier League 2018 were written from Cricbuzz's scorecard and live pages (783 requests, 2.5 s apart, none refused).
  - **Shape.** `m/<id>.json` has `final: 1`, `rows: []`, `meta`, `detail`, `scorecard`, `source: "cricbuzz-backfill"` and an `info` object (venue, toss, umpires, referee, player of the match, squads) of names and ids only. Cricbuzz's squad list can include the bench, so it is `squads`, not a playing XI. Older pages have no officials (56 of 387 have umpires).
  - **Rules.** Never replaces a match file that exists or one in `state.json` `pending`/`seen`. Never touches the archiver's `watermark`/`cycle`. The archiver merges per month and recomputes years and counts from the files, so it does not undo these rows. No commentary text, report or HTML is kept; raw pages stay in a cache outside the site.
  - **Known gaps.** 12 abandoned matches have an empty card. Match 11297's card does not add up (249 + 8 extras against 256, 248 + 12 against 259), kept as Cricbuzz has it. Ball-by-ball is a separate crawler (`balls`: `{v:1, p:[[name, cricbuzzId]], inn:[{i, b, partial?}]}`, the Cricsheet delivery shape); the app draws it with the Cricsheet commentary, over and worm code and leaves a `partial` innings out of the overs. Runs come from the commentary lines corrected by each over separator's summary (`reconcile` in `src/balls.js`), used only when its total equals the board's change for that over: some separators carry a placeholder or a copy of the previous over. After the 2026-10-25 fixes, 19 of 266 matches (22 of 551 innings) stay partial: balls Cricbuzz dropped together with their separator, or one batter 1 run off with the total right (the commentary disagrees with Cricbuzz's own scorecard).
- **Afghanistan records (`afg-records.mjs`).** `node appwrite/tools/archive/afg-records.mjs <gh-pages checkout>` reads every `m/*.json` and writes `s/team/<key>.json` (Afghanistan and the Afghanistan Premier League sides only; an opponent in an international is a side Cricsheet records) and `s/h2h/<a>__<b>.json`, in the Cricsheet site's schemas and key rules (`history/src/keys.js`; its import path means the script runs from this repo, not from the published copy). Counted: Tests, ODIs and T20Is with Afghanistan (T20I) or an APL side (league `T20`) that have an innings; a match with no ball bowled is left out, as in Cricsheet. First run (2026-10-24): 378 matches, 6 team files, 36 pair files. **Rerun after any backfill**, then commit `s/` to `gh-pages` as a normal commit. The app reads them only when the Cricsheet site has no team key for a side (`HistoryRecords.team`, `H2HRepository`) and labels them as Bouncer's. Player records are not built: scorecards give no catches or stumpings without a name join.
- **A backfill file has `final: 1`, not `true`.** `ArchivedMatch.final` is JSON for that reason; as a Boolean the whole file failed to decode.
- **Rows are archived as Appwrite held them.** A withdrawn correction (`supersedeOlder` deletes it in Appwrite) may be archived before its deletion. The device's `deliveryKey` rule, newest row wins, already collapses that on read.
