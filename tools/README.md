# tools/

One-off and scheduled maintenance scripts. Nothing here is part of the app build.

| Script | What it does |
|---|---|
| `update_logos.py` | Refreshes team logo assets. Run daily by `.github/workflows/daily_logo_update.yml`. |
| `extract_team_colors.py` | Derives each team's colours from its crest into `TeamColors.json`. Run weekly after `update_logos.py`; the hand-picked colours in `ui/theme/TeamPalettes.kt` win over it. |
| `update_acronyms.py` | Refreshes the team acronym map. |
| `inspect_match.py`, `inspect_cricbuzz_data.py` | Ad-hoc scrapers for eyeballing Cricbuzz markup while debugging a parser. |
| `fix_legacy_colors.py` | One-off codemod from the theme migration. Kept for reference; do not run it against the V2 tokens. |

These lived loose in the repository root alongside `match_page.html` and
`matches_list.html` — two captured Cricbuzz pages that nothing referenced and
that were not test fixtures, so those were deleted rather than moved. The
captured markup the parser tests *do* use lives with those tests.
