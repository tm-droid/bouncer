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
 *
 * From model v5 the table is keyed `"name|gender|format"`, because one bare name pooled a country's
 * men's and women's sides and all three formats into a single win rate. Membership is therefore
 * asked of a specific (gender, format) slice, and a side with no record in *that* slice does not
 * resolve — an Australia with 300 men's T20s says nothing about the women's Test side.
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

/**
 * Cricsheet records a women's match as the plain country name with `gender: "female"`, so
 * "Australia Women" and "London Spirit Women" have no corpus entry under those names. The suffix is
 * the gender marker, and it is carried in the key instead.
 */
export function stripWomenSuffix(name) {
  return String(name ?? "").replace(/\s+Women(?:'s)?$/i, "").trim();
}

/** Which side of the corpus a fixture belongs to, read from the title Cricbuzz gives us. */
export function genderFromTitle(title) {
  return /\bwomen(?:'s)?\b/i.test(String(title ?? "")) ? "female" : "male";
}

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
  const f = normalize(fullName);
  if (!f) return false;
  // Cricbuzz marks a women's side on the code ("AUS-W", "INDW") while Cricsheet carries the gender
  // in the key and the plain country name, so the marker has to come off before the code can match
  // anything. Stripping it is safe here for the same reason the prefix rule is: this only ever
  // chooses between two names already confirmed to be in this fixture and this corpus slice.
  const candidates = new Set();
  for (const raw of [code, String(code ?? "").replace(/-?w$/i, "")]) {
    const c = normalize(raw);
    if (c) candidates.add(c);
  }
  if (candidates.size === 0) return false;

  const initials = f.split(/\s+/).map((w) => w[0]).join("");
  for (const c of candidates) {
    if (c === f) return true;
    if (normalize(IRREGULAR_CODES[c.toUpperCase()]) === f) return true;
    if (f.startsWith(c)) return true; // IND -> India, KEN -> Kent
    if (c === initials) return true; // SL -> Sri Lanka
  }
  return false;
}

/** Key shape shared with `training.js` and `winprob.js`. All three must agree exactly. */
export function teamKey(team, gender, format) {
  return `${String(team ?? "").trim()}|${gender ?? "male"}|${format ?? "T20"}`;
}

// Case-insensitive name index per (gender, format) slice, cached against the stats object so a
// per-match rebuild does not walk 600 keys on every one of the twelve matches in a run.
const sliceIndexCache = new WeakMap();

function sliceIndex(teamStats, gender, format) {
  let byModel = sliceIndexCache.get(teamStats);
  if (!byModel) {
    byModel = new Map();
    sliceIndexCache.set(teamStats, byModel);
  }
  const cacheKey = `${gender}|${format}`;
  let index = byModel.get(cacheKey);
  if (index) return index;

  index = new Map();
  const suffix = `|${gender}|${format}`;
  for (const key of Object.keys(teamStats)) {
    if (!key.endsWith(suffix)) continue;
    index.set(normalize(key.slice(0, key.length - suffix.length)), key.slice(0, key.length - suffix.length));
  }
  byModel.set(cacheKey, index);
  return index;
}

/**
 * Map a live situation onto corpus team names.
 *
 * @param format the winprob format string ("T20", "ODI", "HUNDRED", "TEST"). A format with no
 *   corpus class — T10, UNKNOWN — has no slice to look in and resolves to null rather than
 *   borrowing another format's win rates.
 * @returns `{ battingTeam, bowlingTeam, gender }` with names as `teamStats` keys them, or **null**
 *   when either side is outside the corpus or the batting side cannot be attributed unambiguously.
 *   Null is not an error — it is the honest "no strength signal for this fixture", and the caller
 *   falls back to the neutral 0.5 that has been in effect all along.
 */
export function resolveMatchTeams({ title, battingTeamName, teamStats, format = "T20", gender } = {}) {
  const stats = teamStats ?? {};
  const sides = teamsFromTitle(title);
  if (!sides) return null;

  const resolvedGender = gender ?? genderFromTitle(title);
  const index = sliceIndex(stats, resolvedGender, format);

  // Both sides must be corpus teams in this slice. This is the guard that makes the code matching
  // below safe.
  const canonical = sides.map((side) => index.get(normalize(stripWomenSuffix(side))) ?? null);
  if (canonical.some((name) => name == null)) return null;

  const hits = canonical.filter((name) => codeMatchesName(battingTeamName, name));
  // Exactly one side must claim the code. Neither means we cannot tell who is batting; both means
  // the code is ambiguous within this fixture. Guessing the orientation would invert the result.
  if (hits.length !== 1) return null;

  const battingTeam = hits[0];
  return {
    battingTeam,
    bowlingTeam: canonical.find((name) => name !== battingTeam) ?? null,
    gender: resolvedGender,
  };
}

/**
 * The team-strength table, whichever shape the model file is in.
 *
 * `evaluate` accepts both a versioned model (stats nested under `teamStats`) and a bare stats
 * object, and resolution has to read the same table `evaluate` will, or it would validate names
 * against one map and look them up in another.
 */
export function teamStatsOf(model) {
  return model?.teamStats ?? model ?? {};
}
