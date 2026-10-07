// What the model said during a match and how the match ended, one file per finished match.
//
// The model was only ever scored offline, on Cricsheet. Nothing scored what was actually published
// from live Cricbuzz inputs, where the failures are different ones: a name that does not join, a
// probability oriented to the wrong side, a context that was never built. `wpe-{matchId}` holds the
// per-over probabilities already stamped on the over history, the pre-match figure, and Cricbuzz's
// own structured result, so `appwrite/tools/winprob-live-score.mjs` can score them later.
//
// Pure: no I/O. The device never reads these files.

export const EVAL_PREFIX = "wpe-";
export const EVAL_RETENTION_MS = 60 * 24 * 60 * 60 * 1000;

/** Rival captures kept per match. One per over, so this covers two ODI innings with room. */
export const RIVAL_MAX_SAMPLES = 120;
const RIVAL_RAW_MAX_CHARS = 400;

const round3 = (v) => Math.round(v * 1000) / 1000;

/**
 * Who won, as `team1` / `team2` relative to [team1Id], from Cricbuzz's `matchHeader.result`.
 *
 * By team id only. A win whose winner is neither side, or a result type this does not recognise,
 * is `unknown` and is left out of scoring — a guess here would be scored as a prediction error.
 */
export function outcomeOf(result, team1Id, team2Id) {
  const type = String(result?.type ?? "").toLowerCase();
  if (!type) return "unknown";
  if (type === "win") {
    const winner = result.winnerId == null ? null : String(result.winnerId);
    if (winner !== null && team1Id != null && winner === String(team1Id)) return "team1";
    if (winner !== null && team2Id != null && winner === String(team2Id)) return "team2";
    return "unknown";
  }
  if (type.includes("tie")) return "tie";
  if (type.includes("draw")) return "draw";
  if (/no.?result|abandon|cancel/.test(type)) return "no_result";
  return "unknown";
}

/**
 * A rival's published figure as a bounded string, or null when the page carries none.
 *
 * Kept raw on purpose: Cricbuzz's `winProbability` prop was `$undefined` on every match looked at
 * before this was written, so its populated shape is unknown and is read by the report tool once
 * a sample exists rather than assumed here.
 */
export function rivalSample(raw) {
  if (raw === undefined || raw === null || raw === "" || raw === "$undefined") return null;
  const text = typeof raw === "string" ? raw : JSON.stringify(raw);
  return text ? text.slice(0, RIVAL_RAW_MAX_CHARS) : null;
}

/** A live match's rival samples, kept in Storage so a container restart mid-match loses none. */
export const RIVAL_PREFIX = "wpr-";

/** Stored and in-memory samples as one list: one per innings and over, oldest first, bounded. */
export function mergeRival(stored, memory) {
  const byOver = new Map();
  for (const s of [...(Array.isArray(stored) ? stored : []), ...(Array.isArray(memory) ? memory : [])]) {
    if (s && typeof s.raw === "string") byOver.set(`${s.innings}|${s.over}`, s);
  }
  return [...byOver.values()].sort((a, b) => (a.at ?? 0) - (b.at ?? 0)).slice(-RIVAL_MAX_SAMPLES);
}

/**
 * The file for one finished match, or null when there is nothing in it to score.
 *
 * [overs] is the over history; only points carrying a stamped `p` are kept. [team1Id] is the side
 * every `p` is oriented to — the first-innings side.
 */
export function buildEvalLog({
  matchId, title, format, gender, team1Id, otherTeamIds = [], result, status, overs,
  prematch = null, modelVersion = null, hadContext = false, hadElevens = false, rival = [], now = Date.now(),
  teams = [], startMs = null,
}) {
  const points = (Array.isArray(overs) ? overs : [])
    .filter((o) => Number.isFinite(o?.p) && Number.isInteger(o.innings) && Number.isFinite(o.over))
    .map((o) => ({ innings: o.innings, over: o.over, score: o.score ?? null, wickets: o.wickets ?? null, p: o.p }));
  const pre = prematch && Number.isFinite(prematch.p) && prematch.teamId != null
    ? { teamId: String(prematch.teamId), p: round3(prematch.p) }
    : null;
  if (points.length === 0 && !pre) return null;

  const first = team1Id == null ? null : String(team1Id);
  const second = otherTeamIds.map(String).find((id) => id !== first) ?? null;
  return {
    version: 1,
    matchId: String(matchId),
    title: title ?? "",
    format: format ?? null,
    gender: gender ?? null,
    team1Id: first,
    team2Id: second,
    // Names and the start time, which is all a Cricinfo match can be joined on: it has its own ids.
    teams: (Array.isArray(teams) ? teams : []).slice(0, 2).map((t) => ({ id: t?.id == null ? null : String(t.id), name: t?.name ?? null })),
    startMs: Number.isFinite(startMs) ? startMs : null,
    modelVersion,
    hadContext,
    hadElevens,
    prematch: pre,
    resultType: result?.type ?? null,
    winnerId: result?.winnerId == null ? null : String(result.winnerId),
    outcome: outcomeOf(result, first, second),
    status: status ?? null,
    endedAt: now,
    points,
    cricbuzz: Array.isArray(rival) ? rival.slice(-RIVAL_MAX_SAMPLES) : [],
  };
}

/**
 * Whether a newly computed pre-match figure should replace the one stored on the context. A
 * fixture is rescored every run and the number drifts in its third decimal; a write per drift
 * would rewrite the context file, and send every device holding it back for a download.
 */
export function prematchMoved(stored, teamId, p, threshold = 0.02) {
  if (!Number.isFinite(p) || teamId == null) return false;
  if (!stored || String(stored.teamId) !== String(teamId)) return true;
  return Math.abs(stored.p - p) >= threshold;
}

/** Every upcoming fixture's latest pre-match figure, by match id, until its match has been logged. */
export const PREMATCH_FILE = "prematch-log";
export const PREMATCH_TTL_MS = 7 * 24 * 60 * 60 * 1000;

const sameName = (a, b) => {
  const fold = (v) => String(v ?? "").toLowerCase().replace(/[^a-z0-9]/g, "");
  return fold(a) !== "" && fold(a) === fold(b);
};

/**
 * The Cricbuzz team id of the side a fixture's title names as [sideName], or null. The match list
 * writes it as `teamId`, and as an empty string when Cricbuzz gave none.
 */
export function fixtureTeamId(fixture, sideName) {
  const hit = (Array.isArray(fixture?.teams) ? fixture.teams : []).find((t) => sameName(t?.name, sideName));
  const id = hit?.teamId ?? hit?.id;
  return id == null || String(id) === "" ? null : String(id);
}

/**
 * [log] with [matchId]'s figure recorded, or null when nothing changed. Entries past
 * `PREMATCH_TTL_MS` are dropped on the way, so the file is bounded by a week of fixtures.
 */
export function notePrematch(log, matchId, teamId, p, now = Date.now()) {
  if (!prematchMoved(log?.[String(matchId)], teamId, p)) return null;
  const next = {};
  for (const [id, entry] of Object.entries(log ?? {})) {
    if (entry && Number.isFinite(entry.at) && now - entry.at < PREMATCH_TTL_MS) next[id] = entry;
  }
  next[String(matchId)] = { teamId: String(teamId), p: round3(p), at: now };
  return next;
}
