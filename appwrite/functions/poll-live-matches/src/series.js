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

/** Format/level classification. Deliberately says nothing about whether it is women's cricket. */
export function seriesCategory(name) {
  if (/IPL|PSL|BBL|CPL|League|T20/i.test(name)) return "LEAGUE";
  if (/Domestic|Ranji|Sheffield/i.test(name)) return "DOMESTIC";
  return "INTERNATIONAL";
}
