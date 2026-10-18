# tools/

One-off and scheduled maintenance scripts. Nothing here is part of the app build.

| Script | What it does |
|---|---|
| `update_logos.py` | Refreshes team logo assets. Run daily by `.github/workflows/daily_logo_update.yml`. |
| `update_logos_extra.py` | Run weekly after `update_logos.py`. A TheSportsDB badge replaces Cricbuzz's crest: the real logo for a club or franchise, and the board's crest for a national side and its variants. Set `NATIONAL_BOARD_CRESTS = False` for flags instead. It also fills sides with no crest, including every side in the public archive's match index. Sources, in order: TheSportsDB, then Wikidata (one SPARQL query), then a variant's parent side ("India Women" uses India's crest). Results are cached in `team_lookups.json`. Each crest is tested against the app's two plates: a line-art crest that vanishes on one is listed in `assets/TeamLogoInk.json` to be drawn reversed there; a multicolour one that vanishes is not used. |
| `extract_team_colors.py` | Writes each team's colours to `TeamColors.json`. Sources, in order: TheSportsDB/Wikidata kit colours, the crest, then the national flag (flag-icons SVGs via `FLAG_ICONS_DIR`). The hand-picked colours in `ui/theme/TeamPalettes.kt` win over all of them. |
| `team_sources.py`, `team_variants.json` | The pure half of the two scripts above: name normalising, variant stripping (pinned to the app by `TeamVariantParityTest`), the country-to-flag map, and response parsing. Tested offline: `python -m unittest discover -s tools/tests`. |
| `update_acronyms.py` | Refreshes the team acronym map. |
| `inspect_match.py`, `inspect_cricbuzz_data.py` | Ad-hoc scrapers for eyeballing Cricbuzz markup while debugging a parser. |
| `fix_legacy_colors.py` | One-off codemod from the theme migration. Kept for reference; do not run it against the V2 tokens. |

These lived loose in the repository root alongside `match_page.html` and
`matches_list.html` — two captured Cricbuzz pages that nothing referenced and
that were not test fixtures, so those were deleted rather than moved. The
captured markup the parser tests *do* use lives with those tests.
