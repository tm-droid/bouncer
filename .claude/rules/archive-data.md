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
  - The source lives only here, in `appwrite/tools/archive/`. `publish-archiver.yml` force-pushes it to the archive repo's `main` as one orphan commit on every change, with the deploy key in `ARCHIVE_DEPLOY_KEY`. Never edit the archive repo's `main` directly.
- **The site is appended to, never rebuilt.** Its source is gone from Appwrite after 14 days, so a lost site cannot be regenerated. Each run is a normal commit on `gh-pages`, never a force-push.
- **Each commentary row is read once.**
  - Rows are read by a `$createdAt` watermark in `state.json`, with a 5-minute overlap merged by row id. Reads bill per row returned.
  - A run reads at most `ARCHIVE_MAX_ROWS` (60,000). A run that hits the cap finalises nothing, because it has not seen every match's newest rows.
- **When a match is final.**
  - **Ended:** the recent list (20 rows, about a day) calls it finished, or, once its rows have been quiet for 6 h, its detail row's status does. It is final after 6 h of quiet and 6 h since it was seen ended, which leaves room for the rewrite backlog and the finalising run's summary and `wpe-` log.
  - **Stale:** a match nothing calls finished is final after 4 days with no rows.
  - Rows that arrive after finalising are still merged in.
  - **The 4-day rule must stay well inside the 14-day prune.** Once a prune deletes a match's rows before the archive has them, those rows are lost.
- **The API key is read-only** (`rows.read`, `files.read`), stored as the archive repo's `APPWRITE_API_KEY` secret. The workflow has no `pull_request` trigger, so a fork can never run it with the secret.
- **Size.** Pages refuses a site over 1 GB, and the run fails over 900 MB.
  - Measured on the backfill: about 285 B a row, and about 4,000 rows a day (60k in 15 days), so roughly 35 MB a month and 2 years per site. The largest match file is a Ranji game, 1,570 rows and 600 KB raw; Pages serves it gzipped. Replace this with a measured month.
  - When full, the next site is a new repo (`bouncer-archive-2`); the app would then read both.
- **Scheduled workflows in a public repo stop after 60 days without repository activity.** Whether the workflow's own commits count is unverified. Check the run list after the first quiet stretch with no matches.

## Layout (schema `v` 1)

| Path | Contents |
|---|---|
| `manifest.json` | `v`, `updated`, `matches`, `files`, `bytes` |
| `state.json` | the watermark; per pending match `lastRowMs`, `endedSeenMs` and its list entry; `seen`, every match-list entry (upcoming, live, recent) for 30 days, so a match that leaves the 20-row recent list before it is final still has its series and teams |
| `idx/years.json` | `{year: {month: count}}` |
| `idx/m/YYYY-MM.json` | `[id, date, format, gender, team1, team2, result, series, venue]`, newest first; `unknown.json` for a match with no date at all |
| `m/<cricbuzzId>.json` | `v`, `id`, `final`, `finalisedAt`, `rows` (the `match-commentary` columns under their own names plus `id`, in `sort_key` order), `meta` (its match-list entry), `detail`, `scorecard`, `context` (`mc-`), `evalLog` (`wpe-`), `partial: 1` when neither a list entry nor a context was available |

- **Backfill (2026-10-15 00:05 UTC, from a Mac, with the read-only key).** 60,301 rows, back to 2026-09-30, in two runs (the first hit the 60,000 cap and finalised nothing): 86 matches final, 42 pending, 19.2 MB. Every archived match tested matched Appwrite's row count (170697: 229 of 229). For those matches the `mc-` file and the list entry were often gone already, so they are `partial` and their index row has short team codes from the detail row.
- **Rows are archived as Appwrite held them.** A withdrawn correction (`supersedeOlder` deletes it in Appwrite) may be archived before its deletion. The device's `deliveryKey` rule, newest row wins, already collapses that on read.
