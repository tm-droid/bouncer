/**
 * Series classification.
 *
 * Kept out of main.js so it can be tested without pulling in node-appwrite.
 */

/**
 * Whether a series is women's cricket.
 *
 * This is INDEPENDENT of format. The category chain below is first-match-wins, so testing Women
 * after T20 made "ICC Women's T20 World Cup" a LEAGUE and left WOMEN all but unreachable — but
 * simply reordering would move the error rather than fix it, stripping the competition of its T20
 * classification instead. The two are separate axes, so they are now separate fields.
 */
export function isWomensSeries(name) {
  // The apostrophe is optional — Cricbuzz emits both "Women's" and "Womens".
  return /\bwomen(?:'?s)?\b|\bwpl\b/i.test(name);
}

/**
 * Format/level classification. Deliberately says nothing about whether it is women's cricket.
 *
 * Word-bounded so a league acronym is not found inside an unrelated word. WPL is listed because
 * "Women's Premier League" is often abbreviated past the word "League".
 *
 * Must stay identical to `seriesCategoryOf` in
 * `app/src/main/kotlin/com/tanush/bouncer/network/SeriesClassification.kt` — the device reads this
 * function's output from the `active-series` bucket file and computes it locally on the scrape
 * fallback, so a divergence shows up as a series changing category depending on which path ran.
 */
export function seriesCategory(name) {
  if (/\b(?:IPL|PSL|BBL|CPL|WPL)\b|League|T20/i.test(name)) return "LEAGUE";
  if (/Domestic|Ranji|Sheffield/i.test(name)) return "DOMESTIC";
  return "INTERNATIONAL";
}
