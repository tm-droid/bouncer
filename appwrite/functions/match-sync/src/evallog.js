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

/**
 * The file for one finished match, or null when there is nothing in it to score.
 *
 * [overs] is the over history; only points carrying a stamped `p` are kept. [team1Id] is the side
 * every `p` is oriented to — the first-innings side.
 */
export function buildEvalLog({
  matchId, title, format, gender, team1Id, otherTeamIds = [], result, status, overs,
  prematch = null, modelVersion = null, hadContext = false, hadElevens = false, rival = [], now = Date.now(),
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
