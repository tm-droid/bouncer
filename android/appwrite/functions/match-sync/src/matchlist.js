/**
 * The match list, parsed from Cricbuzz's own pages.
 *
 * This replaces a fetch of `${VERCEL_API_BASE}/matches?type=…`, a separately-hosted scraper whose
 * code lives in another repository and which returned `{id, title, status}` and nothing else. That
 * made this function the third independent parser of one fact — the device scrapes the same pages
 * for the phone UI, and this function already scrapes Cricbuzz directly for the schedule, the
 * series list and the archives — and the thinnest of the three fed everything server-side:
 * `match-sync`, the widgets, Android Auto and `ScorePollingWorker`.
 *
 * Because the summary carried no team objects, every consumer had to invent them. The widget split
 * the title string on " vs " to name the sides; Android Auto showed no score at all; and nothing
 * downstream had Cricbuzz's team ids, which are the only key the live payload can be aligned on.
 *
 * Port of `parseMatchObject` in `CricBuzzScraper.kt` and `teamInningsScore` in
 * `TeamInningsScore.kt`, reading the same fields off the same flight payload. Deliberately narrow:
 * only what a list needs. Everything richer belongs to the per-match pages `match-sync` already
 * fetches.
 */

import { forEachNextFlightPayload } from "./nextflight.js";

/** A side bats at most twice, and Cricbuzz keys the two innings on that side's score object. */
const INNINGS_KEYS = ["inngs1", "inngs2"];

/** Ten wickets ends an innings, so a completed innings is written as runs alone in a Test. */
const ALL_OUT_WICKETS = 10;

/** The formats in which a side bats twice — the only ones whose score reads "266 & 83/2". */
const MULTI_DAY_FORMATS = new Set(["TEST", "FC"]);

const isMultiDayFormat = (matchFormat) =>
  MULTI_DAY_FORMATS.has(String(matchFormat ?? "").toUpperCase());

/**
 * "266 & 83/2" — every innings this side has batted, oldest first, and the overs of the newest.
 *
 * Reading only the newer innings renders a Test's two-innings total as the second innings alone,
 * on every card, header, widget and notification. A completed innings drops its wicket count in
 * the multi-day formats only, which is how both Cricbuzz and Cricinfo write a Test score.
 *
 * Mirrors `teamInningsScore` in `app/src/main/kotlin/com/tanush/bouncer/network/TeamInningsScore.kt`
 * — the device parses the same payload on its own scrape, so the two must agree.
 */
export function teamInningsScore(scoreObj, key, multiDay) {
  const side = scoreObj?.[key];
  if (!side || typeof side !== "object") return { score: "", overs: "" };
  const innings = INNINGS_KEYS.map((k) => side[k]).filter((i) => i && typeof i === "object");
  const score = innings
    .map((inn) => {
      if (inn.runs === undefined || inn.runs === null) return null;
      const runs = String(inn.runs);
      const wickets = Number.parseInt(String(inn.wickets ?? "0"), 10) || 0;
      return multiDay && wickets >= ALL_OUT_WICKETS ? runs : `${runs}/${wickets}`;
    })
    .filter((s) => s !== null)
    .join(" & ");
  const newest = innings[innings.length - 1];
  return { score, overs: newest?.overs === undefined ? "" : String(newest.overs) };
}

/** One `matchInfo` object as a list summary, or null when it is not one. */
function summaryOf(obj) {
  const info = obj?.matchInfo;
  if (!info || typeof info !== "object") return null;
  const id = info.matchId;
  const team1 = info.team1?.teamName;
  const team2 = info.team2?.teamName;
  const status = info.status;
  if (id === undefined || id === null || !team1 || !team2 || typeof status !== "string") return null;

  const multiDay = isMultiDayFormat(info.matchFormat);
  const scoreObj = obj.matchScore ?? {};
  const s1 = teamInningsScore(scoreObj, "team1Score", multiDay);
  const s2 = teamInningsScore(scoreObj, "team2Score", multiDay);
  // Which side is batting comes from the score object's own `battingTeamId`, compared by id. The
  // names in this payload are full team names, but the LIVE payload names a side with a short code
  // ("EZONE", "RSAA") that matches no full name, so every join across the two goes by id.
  const battingId = scoreObj.battingTeamId === undefined ? null : String(scoreObj.battingTeamId);

  const side = (team, score, teamName) => ({
    teamId: team?.teamId === undefined ? "" : String(team.teamId),
    name: teamName,
    score: score.score,
    overs: score.overs,
    isBatting: battingId !== null && String(team?.teamId) === battingId,
  });

  return {
    id: String(id),
    title: `${team1} vs ${team2}`,
    // `status` and `state` are two different sentences about the same match and the device
    // classifies over both at once, so both travel. `state` alone ("Complete") does not say who
    // won; `status` alone can still read "Innings Break" after the result is in.
    status,
    state: typeof info.state === "string" ? info.state : "",
    matchFormat: typeof info.matchFormat === "string" ? info.matchFormat : "",
    seriesId: info.seriesId === undefined ? "" : String(info.seriesId),
    seriesName: typeof info.seriesName === "string" ? info.seriesName : "",
    matchDesc: typeof info.matchDesc === "string" ? info.matchDesc : "",
    startDate: info.startDate === undefined ? "" : String(info.startDate),
    teams: [side(info.team1, s1, team1), side(info.team2, s2, team2)],
  };
}

function collect(node, out, seen) {
  if (Array.isArray(node)) {
    for (const child of node) collect(child, out, seen);
    return;
  }
  if (!node || typeof node !== "object") return;
  const summary = summaryOf(node);
  // A list page repeats the same match across flight chunks; first wins, so a later chunk with a
  // partially-hydrated copy cannot replace a complete one.
  if (summary && !seen.has(summary.id)) {
    seen.add(summary.id);
    out.push(summary);
  }
  for (const key of Object.keys(node)) collect(node[key], out, seen);
}

/**
 * Every match on a Cricbuzz list page.
 *
 * Returns `[]` for markup it cannot read, which the caller must treat as "no answer" and never as
 * "no matches": overwriting a good list with an empty one is how a whole feature goes dark on a
 * single bad fetch.
 */
export function parseMatchList(html) {
  const out = [];
  const seen = new Set();
  forEachNextFlightPayload(String(html ?? ""), (data) => {
    try {
      collect(data, out, seen);
    } catch {
      // One unreadable chunk must not cost the whole page.
    }
  });
  return out;
}
