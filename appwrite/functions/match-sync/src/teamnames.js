/**
 * Resolve the two sides of a match to the team names the trained model is keyed by.
 *
 * The model's `teamStats` comes from Cricsheet and is keyed by full names ("South Africa",
 * "Papua New Guinea"). Cricbuzz's miniscore gives us only `batTeamName`, which is a short code
 * ("SA", "PNG", "SUR"). So `teamStats[battingTeam]` never hit, `?? 0.5` fired for both sides, and
 * `teamStrengthDiff` — a model feature and a heuristic term — was silently zero on every match.
 *
 * The obvious fix, a short-code-to-name lookup table, is unsafe. Cricbuzz serves far more domestic
 * cricket than international, and the codes collide across the two: "SUR" is Surrey in a county
 * game and Suriname in the corpus; "KEN" is Kent and Kenya. Both collisions are live — a Surrey vs
 * Kent fixture would have picked up Suriname's and Kenya's win rates. A wrong strength is worse
 * than the neutral 0.5 it replaced, because nothing downstream can tell it was a guess.
 *
 * So resolution runs off the match TITLE, which carries full names ("Surrey vs Kent, Group A"),
 * and **both** sides must be in `teamStats` or neither resolves. That makes a collision
 * unreachable: a code is never mapped to a name, it only ever picks between two names already
 * known to be real corpus teams. A domestic fixture fails the check and keeps today's behaviour.
 */

/** Codes that are neither a prefix nor the initials of their full name. */
const IRREGULAR_CODES = {
  USA: "United States of America",
  UAE: "United Arab Emirates",
  WI: "West Indies",
  SA: "South Africa",
  NZ: "New Zealand",
  PNG: "Papua New Guinea",
  CZK: "Czech Republic",
  IOM: "Isle of Man",
  KOR: "South Korea",
  TCI: "Turks and Caicos Island",
  CRC: "Costa Rica",
  CAY: "Cayman Islands",
  STH: "St Helena",
  CIV: "Ivory Coast",
};

const normalize = (s) => String(s ?? "").trim().toLowerCase();

/** Split "Surrey vs Kent, Group A" into ["Surrey", "Kent"], or null when it is not a two-sided title. */
export function teamsFromTitle(title) {
  const head = String(title ?? "").split(",")[0];
  const sides = head.split(/\s+vs\.?\s+/i).map((s) => s.trim()).filter(Boolean);
  return sides.length === 2 ? sides : null;
}

/**
 * Does a Cricbuzz short code denote this full name?
 *
 * Only ever asked about a name already confirmed to be in the corpus, and only to choose between
 * the two sides of one match — so a loose rule here cannot reach across matches.
 */
export function codeMatchesName(code, fullName) {
  const c = normalize(code);
  const f = normalize(fullName);
  if (!c || !f) return false;
  if (c === f) return true;
  if (normalize(IRREGULAR_CODES[String(code ?? "").trim().toUpperCase()]) === f) return true;
  if (f.startsWith(c)) return true; // IND -> India, KEN -> Kent
  const initials = f.split(/\s+/).map((w) => w[0]).join("");
  return c === initials; // SL -> Sri Lanka
}

/**
 * Map a live situation onto corpus team names.
 *
 * @returns `{ battingTeam, bowlingTeam }` with names as `teamStats` keys them, or **null** when
 *   either side is outside the corpus or the batting side cannot be attributed unambiguously.
 *   Null is not an error — it is the honest "no strength signal for this fixture", and the caller
 *   falls back to the neutral 0.5 that has been in effect all along.
 */
export function resolveMatchTeams({ title, battingTeamName, teamStats } = {}) {
  const stats = teamStats ?? {};
  const sides = teamsFromTitle(title);
  if (!sides) return null;

  // Both sides must be corpus teams. This is the guard that makes the code matching below safe.
  const canonical = sides.map((side) => {
    if (Object.prototype.hasOwnProperty.call(stats, side)) return side;
    return Object.keys(stats).find((key) => normalize(key) === normalize(side)) ?? null;
  });
  if (canonical.some((name) => name == null)) return null;

  const hits = canonical.filter((name) => codeMatchesName(battingTeamName, name));
  // Exactly one side must claim the code. Neither means we cannot tell who is batting; both means
  // the code is ambiguous within this fixture. Guessing the orientation would invert the result.
  if (hits.length !== 1) return null;

  const battingTeam = hits[0];
  return {
    battingTeam,
    bowlingTeam: canonical.find((name) => name !== battingTeam) ?? null,
  };
}

/**
 * The team-strength table, whichever shape the model file is in.
 *
 * `evaluate` accepts both a v3 model (stats nested under `teamStats`) and a bare stats object, and
 * resolution has to read the same table `evaluate` will, or it would validate names against one
 * map and look them up in another.
 */
export function teamStatsOf(model) {
  return model?.teamStats ?? model ?? {};
}
