// Highlightly cricket API as a last resort for a FINISHED match's scorecard, when Cricbuzz's own
// scorecard page cannot be had (403/429 CricbuzzBlockedError, a parse failure, an empty card).
// Live matches never come here: a card that is minutes old by the time it lands is worse than none.
//
// Why it is this cautious:
//  - The key's plan is 100 requests a window, SHARED with bouncer-api's hourly highlights sync
//    (`functions/api/src/highlights/main.js`: `planDates` = 24 runs x 1 date + 6 sweeps x 3 back-days
//    = 42 requests/day at most, with a QUOTA_RESERVE of 10). This fallback is capped at
//    DAILY_CALL_CAP = 20 calls/day, so the two together stay at 62 of 100.
//  - A wrong scorecard is worse than a missing one, so every step that cannot be proven gives up:
//    the join needs exactly one candidate, and the mapped totals must agree with Highlightly's own
//    match score, its fall of wickets and its partnerships.
//  - Env: HIGHLIGHTLY_API_KEY, else RAPIDAPI_KEY — the same pair, in the same order, as bouncer-api.
//    Neither set means the feature is off and no request is ever made.

import { teamsFromTitle } from "./teamnames.js";

export const HIGHLIGHTLY_HOST = "cricket-highlights-api.p.rapidapi.com";
/** Requests this function may spend per UTC day. At most 2 per match (date list + detail). */
export const DAILY_CALL_CAP = 20;
export const STATE_FILE = "highlightly-fallback";
export const SOURCE = "highlightly";
const FETCH_TIMEOUT_MS = 15_000;
const LIST_LIMIT = 100;
/** Timezone slack between Cricbuzz's start instant and Highlightly's. */
const START_TOLERANCE_MS = 36 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

const isoDay = (ms) => new Date(ms).toISOString().slice(0, 10);

/** The key bouncer-api reads, in the same order. Empty when the feature is off. */
export function highlightlyKey(env = process.env) {
  return String(env.HIGHLIGHTLY_API_KEY || env.RAPIDAPI_KEY || "").trim();
}

/** "India Women" vs "india women's": case, punctuation and the women's apostrophe-s folded. */
export function normTeam(name) {
  return String(name ?? "")
    .toLowerCase()
    .replace(/\bwomen['’]?s\b/g, "women")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

/** The two side names of a Cricbuzz fixture: the title first, the live page's header second. */
export function fixtureTeams({ title, metaTeams } = {}) {
  const fromTitle = teamsFromTitle(title);
  if (fromTitle) return fromTitle;
  const names = (metaTeams ?? []).map((t) => t?.name).filter(Boolean);
  return names.length === 2 ? names : null;
}

/**
 * The single FINISHED Highlightly match for these two sides within a day of [startMs].
 * Returns `{ item }` or `{ reason }`; zero or several candidates is a reason, never a guess.
 */
export function joinMatch(items, { teams, startMs }) {
  if (!teams || teams.length !== 2 || !Number.isFinite(startMs)) return { reason: "no_fixture" };
  const want = teams.map(normTeam);
  if (!want[0] || !want[1] || want[0] === want[1]) return { reason: "no_fixture" };
  const named = (items ?? []).filter((m) => {
    const got = [normTeam(m?.homeTeam?.name), normTeam(m?.awayTeam?.name)];
    return (got[0] === want[0] && got[1] === want[1]) || (got[0] === want[1] && got[1] === want[0]);
  });
  const near = named.filter((m) => {
    const at = Date.parse(m?.startTime ?? m?.startDate ?? "");
    return Number.isFinite(at) && Math.abs(at - startMs) <= START_TOLERANCE_MS;
  });
  if (near.length === 0) return { reason: "none" };
  if (near.length > 1) return { reason: "ambiguous" };
  const item = near[0];
  if (String(item?.state?.description ?? "").toLowerCase() !== "finished") return { reason: "not_finished" };
  if (!item.id) return { reason: "no_id" };
  return { item };
}

// ── Mapping: Highlightly detail -> exactly the shape parseScorecard returns ───────────────────

const DISMISSED = new Set(["caught", "bowled", "lbw", "stumped", "run out", "hit wicket"]);

const num = (v) => (typeof v === "number" && Number.isFinite(v) ? v : null);
const str = (v) => String(v ?? "").trim();

/** Cricbuzz's dismissal wording, or null for a status this cannot spell out faithfully. */
export function dismissalText(b) {
  const status = str(b?.dismissalStatus).toLowerCase();
  const bowler = str(b?.dismissalBowler?.name);
  const fielders = (b?.dismissalFielders ?? []).map((f) => str(f?.name)).filter(Boolean);
  switch (status) {
    case "not out": return "not out";
    case "bowled": return bowler ? `b ${bowler}` : null;
    case "lbw": return bowler ? `lbw b ${bowler}` : null;
    case "caught":
      if (!bowler) return null;
      return fielders.length === 0 || (fielders.length === 1 && fielders[0] === bowler)
        ? `c & b ${bowler}`
        : `c ${fielders[0]} b ${bowler}`;
    case "stumped": return bowler && fielders[0] ? `st ${fielders[0]} b ${bowler}` : null;
    case "hit wicket": return bowler ? `hit wicket b ${bowler}` : null;
    case "run out": return fielders.length > 0 ? `run out (${fielders.join("/")})` : "run out";
    default: return null;
  }
}

/** Cricket-notation overs ("6.4") to legal balls, or null when it is not valid notation. */
export function oversToBalls(overs) {
  const n = num(overs);
  if (n === null || n < 0) return null;
  const whole = Math.floor(n);
  const balls = Math.round((n - whole) * 10);
  return balls > 5 ? null : whole * 6 + balls;
}

const ballsToOvers = (balls) => (balls % 6 === 0 ? String(balls / 6) : `${Math.floor(balls / 6)}.${balls % 6}`);

const fixed2 = (n) => n.toFixed(2);

function extrasText(t) {
  const total = num(t.extras);
  if (total === null) return null;
  const parts = [["b", num(t.byes) ?? 0], ["lb", num(t.legByes) ?? 0], ["w", num(t.wides) ?? 0], ["nb", num(t.noBalls) ?? 0]];
  const known = parts.reduce((a, [, n]) => a + n, 0);
  if (known > total) return null;
  // Whatever the four kinds do not account for is the penalty kind, Cricbuzz's "p".
  if (total - known > 0) parts.push(["p", total - known]);
  const shown = parts.filter(([, n]) => n > 0).map(([k, n]) => `${k} ${n}`).join(", ");
  return shown ? `${total} (${shown})` : String(total);
}

/** "170/3" or "169" (all out) -> {runs, wickets|null}. */
function parseSideScore(text) {
  const m = /^\s*(\d+)(?:\s*\/\s*(\d+))?\s*$/.exec(String(text ?? ""));
  return m ? { runs: Number(m[1]), wickets: m[2] === undefined ? null : Number(m[2]) } : null;
}

function mapInnings(stat, { displayName, side }) {
  const t = stat?.team;
  if (!t) return { reason: "no_team" };
  const bat = [];
  for (const b of t.inningBatsmen ?? []) {
    if (num(b?.runs) === null) continue; // did not bat: Cricbuzz lists those apart, not as a row
    const name = str(b?.player?.name);
    const balls = num(b.balls);
    const dismissal = dismissalText(b);
    if (!name || balls === null || dismissal === null) return { reason: "batter_unreadable" };
    const sr = num(b.battingStrikeRate) ?? (balls > 0 ? (b.runs * 100) / balls : 0);
    bat.push({
      name,
      dismissal,
      runs: String(b.runs),
      balls: String(balls),
      fours: String(num(b.fours) ?? 0),
      sixes: String(num(b.sixes) ?? 0),
      strikeRate: fixed2(sr),
      profileUrl: "",
      _out: DISMISSED.has(str(b.dismissalStatus).toLowerCase()),
    });
  }
  if (bat.length === 0) return { reason: "no_batters" };

  let legalBalls = 0;
  const bowlers = [];
  for (const w of t.inningBowlers ?? []) {
    const name = str(w?.player?.name);
    const balls = oversToBalls(w?.overs);
    const runs = num(w?.concededRuns);
    const wickets = num(w?.wickets);
    const maidens = num(w?.maidens);
    if (!name || balls === null || runs === null || wickets === null || maidens === null) return { reason: "bowler_unreadable" };
    legalBalls += balls;
    bowlers.push({
      name,
      overs: String(w.overs),
      maidens: String(maidens),
      runs: String(runs),
      wickets: String(wickets),
      // Highlightly gives these per side, not per bowler; "0" is also Cricbuzz's own fallback.
      noBalls: "0",
      wides: "0",
      economy: balls > 0 ? fixed2((runs * 6) / balls) : "",
      profileUrl: "",
    });
  }
  if (bowlers.length === 0 || legalBalls === 0) return { reason: "no_bowlers" };

  const extras = extrasText(t);
  if (extras === null) return { reason: "extras_unreadable" };

  const fow = [...(t.fallOfWickets ?? [])]
    .sort((a, b) => (num(a?.order) ?? 0) - (num(b?.order) ?? 0))
    .map((f, i) => ({
      score: String(f?.runs),
      overs: String(f?.overs ?? ""),
      batsman: str(f?.dismissalBatsman?.name),
      number: i + 1,
      _runs: num(f?.runs),
    }));
  if (fow.some((f) => f._runs === null || !f.batsman)) return { reason: "fow_unreadable" };

  const total = bat.reduce((a, b) => a + Number(b.runs), 0) + num(t.extras);
  const wickets = fow.length;

  // Cross-checks: every one that can be made must hold, and at least one independent total must exist.
  if (bat.filter((b) => b._out).length !== wickets) return { reason: "wickets_mismatch" };
  if (wickets > 10) return { reason: "wickets_mismatch" };
  if (fow.length > 0 && fow[fow.length - 1]._runs > total) return { reason: "fow_exceeds_total" };
  let independent = 0;
  const known = parseSideScore(side?.score);
  if (known) {
    if (known.runs !== total) return { reason: "total_mismatch" };
    if (known.wickets !== null && known.wickets !== wickets) return { reason: "wickets_mismatch" };
    if (known.wickets === null && wickets !== 10) return { reason: "wickets_mismatch" };
    independent++;
  }
  const pships = t.inningPartnerships ?? [];
  if (pships.length > 0) {
    const sum = pships.reduce((a, p) => a + (num(p?.runs) ?? NaN), 0);
    if (sum !== total) return { reason: "partnership_mismatch" };
    independent++;
  }
  if (independent === 0) return { reason: "unverifiable_total" };
  const shownOvers = /^\s*(\d+(?:\.\d)?)\s*\//.exec(String(side?.info ?? ""))?.[1];
  const overs = ballsToOvers(legalBalls);
  // The batting side's overs are the bowlers' balls summed; the match's own "34.3/50 ov" must agree.
  if (shownOvers !== undefined && shownOvers !== overs) return { reason: "overs_mismatch" };

  const score = `${total}-${wickets}`;
  return {
    innings: {
      teamName: displayName,
      score,
      overs: `${overs} Ov`,
      batsmen: bat.map(({ _out, ...rest }) => rest),
      bowlers,
      fallOfWickets: fow.map(({ _runs, ...rest }) => rest),
      partnerships: pships.map((p) => ({
        player1: str(p?.firstPlayer?.name),
        player2: str(p?.secondPlayer?.name),
        runs: String(num(p?.runs) ?? 0),
        balls: String(num(p?.balls) ?? 0),
        contribution1: String(num(p?.firstPlayerRuns) ?? ""),
        contribution2: String(num(p?.secondPlayerRuns) ?? ""),
      })),
      extras,
      total: `${score} (${overs} Ov)`,
    },
  };
}

/**
 * Highlightly's match detail as a Cricbuzz-shaped `{ innings, source }`, or `{ reason }` when any
 * innings cannot be proven. [teams] are the fixture's Cricbuzz names, used as the innings labels.
 */
export function mapDetail(detail, { teams } = {}) {
  const d = Array.isArray(detail) ? detail[0] : detail;
  const stats = [...(d?.statistics ?? [])].sort((a, b) => (num(a?.inningNumber) ?? 0) - (num(b?.inningNumber) ?? 0));
  if (stats.length === 0 || stats.length > 4) return { reason: "innings_count" };
  const sides = [
    [normTeam(d?.homeTeam?.name), d?.state?.teams?.home],
    [normTeam(d?.awayTeam?.name), d?.state?.teams?.away],
  ];
  const labels = new Map((teams ?? []).map((n) => [normTeam(n), n]));
  const multi = stats.length > 2;
  const innings = [];
  for (const stat of stats) {
    const key = normTeam(stat?.team?.name);
    const displayName = labels.get(key);
    if (!displayName) return { reason: "team_label" };
    // A side's match score is the sum of its innings; only a one-innings-each match can be compared.
    const side = multi ? null : sides.find(([n]) => n === key)?.[1];
    const mapped = mapInnings(stat, { displayName, side });
    if (!mapped.innings) return { reason: mapped.reason };
    innings.push(mapped.innings);
  }
  return { card: { innings, source: SOURCE } };
}

// ── The fallback itself: cap, once-a-day-per-match, one cached date list per run ──────────────

const emptyState = (day) => ({ day, used: 0, tried: {} });

/**
 * Builds the per-run fallback, or null when no key is set.
 *
 * [loadState] resolves the stored `{day, used, tried}` (null when no file yet) and may throw;
 * [saveState] persists it. The counter is written ONCE per attempt, before the requests, so a
 * container that dies mid-attempt has still counted its calls and still marked the match tried.
 * An unreadable state refuses every attempt: spending blind is how a shared quota is blown.
 */
export function createHighlightlyFallback({
  apiKey = highlightlyKey(),
  fetchImpl = fetch,
  loadState,
  saveState,
  now = Date.now,
  cap = DAILY_CALL_CAP,
  log = () => {},
  error = () => {},
} = {}) {
  if (!apiKey) return null;
  const lists = new Map(); // date -> items, for this run only
  let state = null;
  let unreadable = false;

  const get = async (path) => {
    const r = await fetchImpl(`https://${HIGHLIGHTLY_HOST}${path}`, {
      headers: { "x-rapidapi-key": apiKey, "x-rapidapi-host": HIGHLIGHTLY_HOST },
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
  };

  const ready = async (today) => {
    if (unreadable) return false;
    if (!state) {
      try {
        const stored = await loadState();
        state = stored && typeof stored === "object" ? { ...emptyState(today), ...stored } : emptyState(today);
      } catch (e) {
        unreadable = true;
        error(`Highlightly fallback state unreadable (${e?.message}) — no requests this run`);
        return false;
      }
    }
    if (state.day !== today) state = emptyState(today);
    return true;
  };

  /** The mapped scorecard for one finished match, or null (and a log line saying why). */
  return async function scorecardFor({ matchId, title, metaTeams, startMs }) {
    const id = String(matchId);
    const tag = `Highlightly fallback ${id}`;
    const teams = fixtureTeams({ title, metaTeams });
    if (!teams || !Number.isFinite(startMs)) { log(`${tag}: no fixture teams or start time`); return null; }
    const today = isoDay(now());
    if (!(await ready(today))) return null;
    if (state.tried[id]) { log(`${tag}: already tried today`); return null; }
    const date = isoDay(startMs);
    const needed = lists.has(date) ? 1 : 2;
    if (state.used + needed > cap) { log(`${tag}: daily cap ${cap} reached (${state.used} used)`); return null; }

    state = { ...state, used: state.used + needed, tried: { ...state.tried, [id]: today } };
    try {
      await saveState(state);
    } catch (e) {
      error(`${tag}: could not persist the counter (${e?.message}) — not calling`);
      return null;
    }

    try {
      if (!lists.has(date)) {
        const body = await get(`/matches?date=${date}&limit=${LIST_LIMIT}`);
        const items = Array.isArray(body?.data) ? body.data : [];
        // A truncated list could hide a second candidate, and then "exactly one" proves nothing.
        if ((num(body?.pagination?.totalCount) ?? items.length) > items.length) { log(`${tag}: list for ${date} truncated`); return null; }
        lists.set(date, items);
      }
      const joined = joinMatch(lists.get(date), { teams, startMs });
      if (!joined.item) { log(`${tag}: no join (${joined.reason})`); return null; }
      const mapped = mapDetail(await get(`/matches/${encodeURIComponent(joined.item.id)}`), { teams });
      if (!mapped.card) { log(`${tag}: not mapped (${mapped.reason})`); return null; }
      log(`${tag}: scorecard from Highlightly match ${joined.item.id} (${mapped.card.innings.length} innings)`);
      return mapped.card;
    } catch (e) {
      error(`${tag}: request failed (${e?.message})`);
      return null;
    }
  };
}

export { DAY_MS };
