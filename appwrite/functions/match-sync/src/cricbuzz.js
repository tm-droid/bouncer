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
      profileUrl: "",
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
    out.push({
      name,
      overs: txt($, stats[1]) || "0",
      maidens: txt($, stats[2]) || "0",
      runs: txt($, stats[3]) || "0",
      wickets: txt($, stats[4]) || "0",
      noBalls: "0",
      wides: "0",
      economy: txt($, stats[stats.length - 1]) || "",
      profileUrl: "",
    });
  });
  return out;
}

function parseFallOfWickets($, $content) {
  const out = [];
  const section = $content.find("div:contains(Fall of Wickets)").first();
  if (section.length === 0) return out;
  const fowText = section.next().text() || "";
  fowText.split("),").forEach((item, idx) => {
    const parts = item.trim().split(" (");
    if (parts.length < 2) return;
    const detail = parts[1].replace(/\)/g, "").split(", ");
    if (detail.length < 2) return;
    out.push({ score: parts[0], overs: detail[1], batsman: detail[0], number: idx + 1 });
  });
  return out;
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
    partnerships: [],
    extras: "",
    total: "",
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
  };
}
