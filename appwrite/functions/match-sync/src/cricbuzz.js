// Server-side port of the on-device Cricbuzz parsers
// (CricBuzzScraper.kt Next.js-flight-payload logic + CricBuzzParser.kt CSS selectors).
// Output shapes mirror the Kotlin models so the device can Gson-parse the payload directly:
//   ScrapedBatsman, ScrapedBowler, Scorecard/Innings/BatsmanStats/BowlerStats/FallOfWicket.

import * as cheerio from "cheerio";

const BASE = "https://www.cricbuzz.com";
export const liveScoresUrl = (matchId) => `${BASE}/live-cricket-scores/${matchId}`;
export const scorecardUrl = (matchId) => `${BASE}/live-cricket-scorecard/${matchId}`;

const UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

export async function fetchHtml(url, timeoutMs = 12_000) {
  const resp = await fetch(url, { headers: { "User-Agent": UA }, signal: AbortSignal.timeout(timeoutMs) });
  if (!resp.ok) throw new Error(`HTTP ${resp.status} from ${url}`);
  return resp.text();
}

// ---------------------------------------------------------------------------
// Next.js flight-payload parsing (port of CricBuzzScraper.kt:28-104,551-585)
// ---------------------------------------------------------------------------

function parseLenient(s) {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

function normalizeNextFlightPayload(raw) {
  let normalized = String(raw).trim();
  const colonIndex = normalized.indexOf(":");
  if (colonIndex >= 1 && colonIndex <= 9) {
    const prefix = normalized.slice(0, colonIndex);
    if ([...prefix].every((c) => /[0-9a-f]/i.test(c))) {
      normalized = normalized.slice(colonIndex + 1);
    }
  }
  if (normalized.startsWith("I")) normalized = normalized.slice(1);
  normalized = normalized.trim();
  return normalized.startsWith("{") || normalized.startsWith("[") ? normalized : null;
}

function unwrapNextFlightPayload(scriptContent) {
  const el = parseLenient(scriptContent);
  if (!Array.isArray(el)) return null;
  if (el.length <= 1 || typeof el[1] !== "string") return null;
  return normalizeNextFlightPayload(el[1]);
}

function findMatchingParenEnd(html, openParenIdx) {
  let parenCount = 0;
  let inString = false;
  let escape = false;
  for (let i = openParenIdx; i < html.length; i++) {
    const ch = html[i];
    if (escape) {
      escape = false;
      continue;
    }
    if (ch === "\\") {
      escape = true;
      continue;
    }
    if (ch === '"') {
      inString = !inString;
      continue;
    }
    if (inString) continue;
    if (ch === "(") parenCount++;
    else if (ch === ")") {
      parenCount--;
      if (parenCount === 0) return i;
    }
  }
  return -1;
}

function forEachNextFlightPayload(html, cb) {
  const startMarker = "self.__next_f.push(";
  let startIndex = 0;
  while (true) {
    const idx = html.indexOf(startMarker, startIndex);
    if (idx === -1) break;
    const openParenIdx = idx + startMarker.length - 1;
    const endIdx = findMatchingParenEnd(html, openParenIdx);
    if (endIdx === -1) {
      startIndex = idx + 1;
      continue;
    }
    const scriptContent = html.substring(openParenIdx + 1, endIdx);
    const unwrapped = unwrapNextFlightPayload(scriptContent);
    if (unwrapped) {
      const parsed = parseLenient(unwrapped);
      if (parsed) cb(parsed);
    }
    startIndex = endIdx + 1;
  }
}

function collectKeyedPairs(node, valueKey, orderKey, results) {
  if (node && typeof node === "object" && !Array.isArray(node)) {
    if (valueKey in node && orderKey in node) {
      const value = node[valueKey];
      const order = Number(node[orderKey]);
      if (typeof value === "string" && value.trim() && Number.isFinite(order)) {
        results.push({ order, value });
      }
    }
    for (const k of Object.keys(node)) collectKeyedPairs(node[k], valueKey, orderKey, results);
  } else if (Array.isArray(node)) {
    for (const item of node) collectKeyedPairs(item, valueKey, orderKey, results);
  }
}

// Returns newest-first, de-duplicated by timestamp: [{ timestamp, commText }]
export function parseCommentary(html) {
  const results = [];
  forEachNextFlightPayload(html, (d) => collectKeyedPairs(d, "commText", "timestamp", results));
  const seen = new Set();
  return results
    .sort((a, b) => b.order - a.order)
    .filter((r) => {
      if (seen.has(r.order)) return false;
      seen.add(r.order);
      return true;
    })
    .map((r) => ({ timestamp: r.order, commText: r.value }))
    .filter((r) => r.commText.length > 5);
}

function parseRecentBallsNext(html) {
  const data = [];
  forEachNextFlightPayload(html, (d) => collectKeyedPairs(d, "overSummary", "overNumber", data));
  if (data.length === 0) return [];
  const latest = data.reduce((a, b) => (b.order > a.order ? b : a));
  return latest.value.trim().split(" ").filter(Boolean);
}

// ---------------------------------------------------------------------------
// CSS-selector parsing (port of CricBuzzParser.kt) via cheerio
// ---------------------------------------------------------------------------

function txt($, el) {
  return $(el).text().trim();
}

// Cricbuzz player links are relative (e.g. "/profiles/46363/ravi-thapliyal"); absolutize them.
function absUrl(href) {
  if (!href) return "";
  return href.startsWith("http") ? href : `${BASE}${href}`;
}

// Economy = runs per over. Overs are cricket-notation "X.Y" where Y is balls (0-5) out of 6,
// so 9.4 overs = 9 + 4/6 overs. Computed rather than scraped (the scorecard's economy cell is
// unreliable), so it's always present and correct.
function computeEconomy(runs, overs) {
  const r = parseFloat(runs);
  const o = parseFloat(overs);
  if (!isFinite(r) || !isFinite(o) || o <= 0) return "";
  const whole = Math.floor(o);
  const balls = Math.round((o - whole) * 10);
  const decimalOvers = whole + balls / 6;
  if (decimalOvers <= 0) return "";
  return (r / decimalOvers).toFixed(2);
}

// Live batsmen/bowler/score come from the Next.js flight-payload `miniscore`
// object — the legacy cb-min-* CSS classes are no longer in Cricbuzz's SSR HTML.
function collectPayloads(html) {
  const payloads = [];
  forEachNextFlightPayload(html, (p) => payloads.push(p));
  return payloads;
}

function findMiniscore(payloads) {
  let found = null;
  const walk = (node) => {
    if (found) return;
    if (node && typeof node === "object" && !Array.isArray(node)) {
      if (node.batsmanStriker && node.inningsId !== undefined && node.matchScoreDetails) {
        found = node;
        return;
      }
      for (const k of Object.keys(node)) walk(node[k]);
    } else if (Array.isArray(node)) {
      for (const item of node) walk(item);
    }
  };
  payloads.forEach(walk);
  return found;
}

const s = (v, fallback = "0") => (v === null || v === undefined ? fallback : String(v));

function mapBatsman(b, isStriker) {
  if (!b || !b.name) return null;
  return {
    name: s(b.name, ""),
    runs: s(b.runs),
    balls: s(b.balls),
    fours: s(b.fours),
    sixes: s(b.sixes),
    strikeRate: s(b.strikeRate, "-"),
    isStriker,
  };
}

function mapBowler(b) {
  if (!b || !b.name) return null;
  return {
    name: s(b.name, ""),
    overs: s(b.overs),
    maidens: s(b.maidens),
    runs: s(b.runs),
    wickets: s(b.wickets),
    economy: s(b.economy, "-"),
  };
}

function parseScorecardBatsmen($, $content) {
  const out = [];
  $content.find("div.grid.scorecard-bat-grid").each((_, row) => {
    const $row = $(row);
    const name = $row.find("a").first().text() || "";
    if (!name || name.includes("Batter")) return;
    const stats = $row.children();
    if (stats.length < 6) return;
    let dismissal = $row.find("div.text-cbTxtSec").text();
    if (!dismissal) {
      const candidates = stats.toArray().slice(1);
      for (const el of candidates) {
        const $el = $(el);
        if ($el.find("a").length === 0 && /[a-zA-Z]/.test($el.text())) {
          dismissal = $el.text();
          break;
        }
      }
      dismissal = dismissal || "";
    }
    out.push({
      name,
      dismissal,
      runs: txt($, stats[1]) || "0",
      balls: txt($, stats[2]) || "0",
      fours: txt($, stats[3]) || "0",
      sixes: txt($, stats[4]) || "0",
      strikeRate: txt($, stats[5]) || "0",
      profileUrl: absUrl($row.find("a").first().attr("href")),
    });
  });
  return out;
}

function parseScorecardBowlers($, $content) {
  const out = [];
  $content.find("div.grid.scorecard-bowl-grid").each((_, row) => {
    const $row = $(row);
    const name = $row.find("a").first().text() || "";
    if (!name || name.includes("Bowler")) return;
    const stats = $row.children();
    if (stats.length < 5) return;
    // Grid columns: name, O, M, R, W, NB, WD, ECO, (trailing empty cell). The old code read the
    // trailing empty cell as economy → always "". Compute economy from runs/overs instead, and
    // pull the real no-balls/wides columns when present.
    const overs = txt($, stats[1]) || "0";
    const runs = txt($, stats[3]) || "0";
    out.push({
      name,
      overs,
      maidens: txt($, stats[2]) || "0",
      runs,
      wickets: txt($, stats[4]) || "0",
      noBalls: stats.length > 5 ? txt($, stats[5]) || "0" : "0",
      wides: stats.length > 6 ? txt($, stats[6]) || "0" : "0",
      economy: computeEconomy(runs, overs),
      profileUrl: absUrl($row.find("a").first().attr("href")),
    });
  });
  return out;
}

// Fall of Wickets and Partnerships are rendered as sibling `div.scorecard-fow-grid` rows,
// each section introduced by a bold header row ("Fall of Wickets", "Powerplays",
// "Partnerships"). Walk the rows and collect the ones under the requested header.
function fowGridRows($, $content, header) {
  const out = [];
  let inSection = false;
  $content.find("div.scorecard-fow-grid").each((_, el) => {
    const kids = $(el).children().map((__, k) => $(k).text().trim()).get();
    const head = (kids[0] || "").trim();
    if (new RegExp(`^${header}$`, "i").test(head)) { inSection = true; return; }
    if (/^(Fall of Wickets|Powerplays|Partnerships)$/i.test(head)) {
      if (inSection) { inSection = false; }
      return;
    }
    if (inSection) out.push(kids);
  });
  return out;
}

function parseFallOfWickets($, $content) {
  return fowGridRows($, $content, "Fall of Wickets")
    .filter((kids) => kids.length >= 3)
    .map((kids, idx) => {
      // kids = [batsman, "score-wicketNumber" (e.g. "11-1"), over (e.g. "1.6")]
      const [runs, wkt] = String(kids[1]).split("-");
      return {
        score: (runs || kids[1] || "").trim(),
        overs: kids[2] || "",
        batsman: kids[0] || "",
        number: Number(wkt) || idx + 1,
      };
    });
}

function parsePartnerships($, $content) {
  return fowGridRows($, $content, "Partnerships")
    .filter((kids) => kids.length >= 2)
    .map((kids) => {
      const players = String(kids[0]).split(/\s*\/\s*/);
      return {
        player1: players[0] || "",
        player2: players[1] || "",
        runs: kids[1] || "",
        balls: kids[2] || "",
      };
    });
}

// Bold label ("Extras", "Total") whose value sits in the immediately following sibling.
function scoreLabelValue($, $content, label) {
  const lbl = $content
    .find("div.font-bold")
    .filter((_, e) => $(e).text().trim() === label)
    .first();
  return lbl.length ? lbl.next().text().trim() : "";
}

function parseInnings($, header) {
  const $header = $(header);
  const id = $header.attr("id");
  const $content = $(`#scard-${id}`);
  if ($content.length === 0) return null;
  let teamNameEl = $header.find("div.font-bold").first();
  if (teamNameEl.length === 0) teamNameEl = $header.find("div[class*=font-bold]").first();
  const teamName = teamNameEl.text() || "Unknown";
  const score = $header.find("span.font-bold").text();
  const overs = $header.find("span:contains(Ov)").text().replace(/\(/g, "").replace(/\)/g, "").trim();
  return {
    teamName,
    score,
    overs,
    batsmen: parseScorecardBatsmen($, $content),
    bowlers: parseScorecardBowlers($, $content),
    fallOfWickets: parseFallOfWickets($, $content),
    partnerships: parsePartnerships($, $content),
    extras: scoreLabelValue($, $content, "Extras"),
    total: scoreLabelValue($, $content, "Total"),
  };
}

export function parseScorecard(scorecardHtml) {
  const $ = cheerio.load(scorecardHtml);
  const innings = [];
  $("div[id^=team-][id*=-innings-]").each((_, header) => {
    const i = parseInnings($, header);
    if (i) innings.push(i);
  });
  return { innings };
}

// Parse the live page into the device-shaped match-live-detail payload,
// driven by the flight-payload miniscore object.
export function parseLiveDetail(liveHtml) {
  const ms = findMiniscore(collectPayloads(liveHtml));
  const recentBalls = parseRecentBallsNext(liveHtml);
  if (!ms) {
    return {
      batsmen: [],
      bowler: null,
      recentBalls,
      score: null,
      team1Score: null,
      team1Overs: null,
      team2Score: null,
      team2Overs: null,
      currentRunRate: null,
      requiredRunRate: null,
      status: null,
      situation: null,
    };
  }

  const batsmen = [
    mapBatsman(ms.batsmanStriker, true),
    mapBatsman(ms.batsmanNonStriker, false),
  ].filter(Boolean);

  const isl = [...(ms.matchScoreDetails?.inningsScoreList || [])].sort(
    (a, b) => a.inningsId - b.inningsId
  );
  const fmt = (e) => (e ? `${e.score}/${e.wickets}` : null);
  const ovs = (e) => (e ? s(e.overs, "") : null);
  const batScore = ms.batTeam ? `${ms.batTeam.teamScore}/${ms.batTeam.teamWkts}` : null;

  return {
    batsmen,
    bowler: mapBowler(ms.bowlerStriker),
    recentBalls,
    score: batScore ? `${batScore} (${s(ms.overs, "")})` : null,
    team1Score: fmt(isl[0]),
    team1Overs: ovs(isl[0]),
    team2Score: fmt(isl[1]),
    team2Overs: ovs(isl[1]),
    currentRunRate: ms.currentRunRate != null ? String(ms.currentRunRate) : null,
    requiredRunRate: ms.requiredRunRate != null ? String(ms.requiredRunRate) : null,
    status: ms.status ? String(ms.status) : null,
    situation: liveSituation(ms, isl),
  };
}

/**
 * Numeric match state for the win-probability model.
 *
 * Everything above is stringified for direct Gson binding on the device, which throws away the
 * numbers the model needs. This keeps them, from the same already-parsed miniscore — no second
 * fetch and no re-parse.
 *
 * `team1` throughout means the FIRST-INNINGS batting side, matching the team1Score/team2Score
 * convention above so a consumer does not need a second mapping.
 */
function liveSituation(ms, isl) {
  const num = (v) => (typeof v === "number" && Number.isFinite(v) ? v : null);
  const current = isl.find((e) => e.inningsId === ms.inningsId) ?? isl[isl.length - 1];
  if (!current) return null;

  const ballsBowled = num(current.ballNbr);
  const isChase = ms.inningsId === 2 && num(ms.target) != null;

  return {
    inningsId: num(ms.inningsId),
    matchFormat: ms.matchScoreDetails?.matchFormat ? String(ms.matchScoreDetails.matchFormat) : null,
    battingTeamName: current.batTeamName ? String(current.batTeamName) : null,
    // Match on team name, not innings id: a Test has four innings entries, two of them belonging
    // to the side currently batting, so "some other innings" picks the wrong team.
    bowlingTeamName: isl.find((e) => e.batTeamName && e.batTeamName !== current.batTeamName)?.batTeamName ?? null,
    // The batting side is team1 only while the first innings is in progress.
    battingTeamIsTeam1: current.inningsId === isl[0]?.inningsId,
    runsScored: num(current.score),
    wicketsLost: num(current.wickets),
    ballsBowled,
    target: isChase ? num(ms.target) : null,
    runsNeeded: isChase ? num(ms.remRunsToWin) : null,
    // oversRem is the authoritative balls-left source in a chase; Cricbuzz already accounts for a
    // reduced-overs match, which deriving from a format budget would not.
    oversRemaining: num(ms.oversRem),
    currentRunRate: num(ms.currentRunRate),
    requiredRunRate: num(ms.requiredRunRate),
  };
}
