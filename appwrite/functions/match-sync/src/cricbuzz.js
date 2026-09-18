// Server-side port of the on-device Cricbuzz parsers
// (CricBuzzScraper.kt Next.js-flight-payload logic + CricBuzzParser.kt CSS selectors).
// Output shapes mirror the Kotlin models so the device can Gson-parse the payload directly:
//   ScrapedBatsman, ScrapedBowler, Scorecard/Innings/BatsmanStats/BowlerStats/FallOfWicket.

import * as cheerio from "cheerio";
import { outcomeClause } from "./llm.js";
import {
  CRICBUZZ_BASE,
  BLOCK_BACKOFF_MS,
  CricbuzzBlockedError,
  MIN_REQUEST_GAP_MS,
  fetchHtml,
  isBlockStatus,
  resetThrottleState,
} from "./cricbuzz-http.js";
import { forEachNextFlightPayload } from "./nextflight.js";

const BASE = CRICBUZZ_BASE;
export const liveScoresUrl = (matchId) => `${BASE}/live-cricket-scores/${matchId}`;
export const scorecardUrl = (matchId) => `${BASE}/live-cricket-scorecard/${matchId}`;

// The polite-HTTP layer moved to ./cricbuzz-http.js so `poll-live-matches` can use the same
// headers, User-Agent rotation, request spacing and 403/429 backoff instead of its own bare
// `User-Agent`-only fetch. Re-exported here so this module's existing importers are unaffected.
export {
  BLOCK_BACKOFF_MS,
  CricbuzzBlockedError,
  MIN_REQUEST_GAP_MS,
  fetchHtml,
  isBlockStatus,
  resetThrottleState,
};

// ---------------------------------------------------------------------------
// Next.js flight-payload parsing (port of CricBuzzScraper.kt:28-104,551-585)
// ---------------------------------------------------------------------------

/**
 * Every `overSeparator` in the flight payload: its glyph string, its over number, and its clock.
 *
 * `overNumber` restarts at 1 every innings, so it cannot order the results. The live page carries a
 * ~20-item window, and at an innings boundary that window holds both the previous innings' over 50
 * and the new innings' over 2 — picking the larger number serves the innings that is already over.
 * `timestamp` is the same field `collectCommentaryItems` orders by, and it is monotonic across the
 * whole match.
 */
function collectOverSummaries(node, results) {
  if (node && typeof node === "object" && !Array.isArray(node)) {
    if ("overSummary" in node && "overNumber" in node) {
      const summary = node.overSummary;
      const over = Number(node.overNumber);
      const ts = Number(node.timestamp);
      if (typeof summary === "string" && summary.trim() && Number.isFinite(over)) {
        results.push({ over, summary, ts: Number.isFinite(ts) ? ts : null });
      }
    }
    for (const k of Object.keys(node)) collectOverSummaries(node[k], results);
  } else if (Array.isArray(node)) {
    for (const item of node) collectOverSummaries(item, results);
  }
}

// RSC back-references ("$27", "$2a") sit where React hoisted the string elsewhere in the stream.
const RSC_BACK_REFERENCE = /^\$[0-9a-f]+$/;

// Mirrors CricBuzzScraper.collectCommentaryItems. An earlier generic collector kept only a value
// and an order key, which discarded `ballMetric` on every delivery — see BallNotation.kt for why
// that field is format-dependent and why "$undefined" must stay null rather than borrowing a
// neighbour's number.
function collectCommentaryItems(node, results) {
  if (node && typeof node === "object" && !Array.isArray(node)) {
    if ("commText" in node && "timestamp" in node) {
      const text = node.commText;
      const order = Number(node.timestamp);
      if (typeof text === "string" && text.trim() && !RSC_BACK_REFERENCE.test(text) && Number.isFinite(order)) {
        // `inningsId` when the node carries it. This is the only per-BALL innings there is: every
        // other source is the innings being batted at write time, which mislabels the tail of an
        // innings the moment the next one starts. null when absent — the caller falls back rather
        // than borrowing a neighbour's number, same rule as ballMetric's "$undefined".
        const innings = Number(node.inningsId);
        results.push({
          order,
          text,
          ballMetric: ballMetricOf(node.ballMetric),
          innings: Number.isFinite(innings) && innings > 0 ? innings : null,
        });
      }
    }
    for (const k of Object.keys(node)) collectCommentaryItems(node[k], results);
  } else if (Array.isArray(node)) {
    for (const item of node) collectCommentaryItems(item, results);
  }
}

// Port of BallNotation.ballMetricOf. Returns { label, over, ball } or null for non-deliveries.
export function ballMetricOf(raw) {
  if (raw === null || raw === undefined) return null;
  const value = String(raw).trim();
  if (!value || value === "$undefined") return null;
  const overBall = value.match(/^(\d{1,3})\.(\d)$/);
  if (overBall) {
    return { label: value, over: Number(overBall[1]), ball: Number(overBall[2]) };
  }
  // The Hundred: a flat 1-100 ball counter, no over structure.
  const counter = value.match(/^(\d{1,3})$/);
  if (counter) return { label: `Ball ${value}`, over: null, ball: Number(counter[1]) };
  return null;
}

/**
 * Deliveries the upstream feed skipped, given the balls one run is about to write.
 *
 * Cricbuzz's live page carries a window of 20 commentary items — about two overs, measured on three
 * live pages on 2026-09-12. At the one-minute sync cadence that is a wide margin. It stops being one
 * when Cricbuzz itself stalls: on 2026-09-12 match 161777 published nothing for thirteen minutes
 * (match-sync ran every minute throughout and logged `0 new commentary rows`), then republished a
 * backlog that jumped from `12.5` straight to `13.4`. Deliveries 13.1, 13.2 and 13.3 never appeared
 * on the page at all and are absent from the table.
 *
 * Nothing noticed. The watermark advanced over the hole and the commentary tab rendered over 13 with
 * three balls, which is indistinguishable from an over that genuinely had three. Those deliveries
 * cannot be recovered — the "Full Commentary" tab at `/live-cricket-full-commentary/{id}` is
 * client-rendered and carries zero deliveries server-side — so the only thing worth doing is saying
 * so out loud.
 *
 * @param balls `{ innings, ball }` in the order they are written, i.e. OLDEST first.
 * @returns `[{ innings, after, before, missing }]`, one entry per hole.
 */
export function findCommentaryGaps(balls) {
  const gaps = [];
  let previous = null;

  for (const entry of Array.isArray(balls) ? balls : []) {
    const metric = ballMetricOf(entry?.ball);
    const innings = entry?.innings ?? null;

    // A label with no over structure (The Hundred's `Ball 72`) has no "next ball" to predict, so it
    // can neither open nor close a gap. Reset rather than guess.
    if (!metric || metric.over === null) { previous = null; continue; }
    // An innings boundary is not a gap, and neither is the first ball after a cold container.
    if (!previous || previous.innings !== innings) {
      previous = { innings, metric };
      continue;
    }

    const missing = deliveriesBetween(previous.metric, metric);
    if (missing > 0) {
      gaps.push({ innings, after: previous.metric.label, before: metric.label, missing });
    }
    // Advance on any forward move, gap or not. Staying put would re-report the same hole against
    // every later ball in the over.
    if (missing >= 0) previous = { innings, metric };
  }
  return gaps;
}

/**
 * Legal deliveries missing between two ball labels, or 0 when they are contiguous.
 *
 * Returns -1 for a label that does not move forward. A wide or a no-ball REPEATS the previous label
 * rather than advancing it, and Cricbuzz also republishes earlier deliveries with fresh timestamps
 * (over 12 of match 161777 was written twice) — neither is a gap, and treating a repeat as one would
 * make this fire on every extra bowled.
 *
 * **The tail of the source over is deliberately not counted**, and this is the one judgement call in
 * here. Going `12.5 → 13.4` this reports 3 (13.1–13.3), not 4: whether 12.6 is missing is
 * unknowable from one run's batch, because an earlier run may already hold it — which is exactly
 * what happened in the real case, where 12.6 was written seventeen minutes before 12.5 was
 * republished. An over can also legitimately end early when the innings does. Undercounting by the
 * one ball at the seam is the right trade against reporting a hole that is not there.
 */
function deliveriesBetween(from, to) {
  if (to.over === from.over) return to.ball > from.ball ? to.ball - from.ball - 1 : -1;
  if (to.over < from.over) return -1;
  const wholeOvers = (to.over - from.over - 1) * BALLS_PER_OVER;
  return wholeOvers + (to.ball - 1);
}

/** Legal deliveries in an over — used only to size an over that was skipped in full. */
const BALLS_PER_OVER = 6;

// Returns newest-first, de-duplicated by timestamp: [{ timestamp, commText, ballMetric, innings }]
export function parseCommentary(html) {
  const results = [];
  forEachNextFlightPayload(html, (d) => collectCommentaryItems(d, results));
  const seen = new Set();
  return results
    .sort((a, b) => b.order - a.order)
    .filter((r) => {
      if (seen.has(r.order)) return false;
      seen.add(r.order);
      return true;
    })
    .map((r) => ({ timestamp: r.order, commText: r.text, ballMetric: r.ballMetric, innings: r.innings }))
    .filter((r) => r.commText.length > 5);
}

// ── Runs off the delivery, read from the RAW Cricbuzz text ───────────────────
//
// Same reasoning as classifyEvent, and the same failure it was written for. commText is
// "<Bowler> to <Batter>, <outcome>[, description]" and that outcome clause is the only place the
// number lives — the AI rewrite that follows turns "1 run" into "scampers through for a single"
// and the digit is gone. `event` is four-valued (WICKET|SIX|FOUR|NONE) and cannot carry it, so the
// device was left re-deriving runs from broadcast prose and defaulting to 0: every single rendered
// as a dot ball, and an over of six of them rendered as a MAIDEN.
//
// Anchored to the clause on purpose. An unanchored /\b(\d+) runs?\b/ over the whole line — which is
// what the device did — also matches "India need 42 runs from 30" and puts a 42 on a dot ball.
//
// Returns TOTAL runs off the delivery, extras included, because the commentary tab's over card sums
// this for the over total and the maiden badge.
//
// null means "not recognised", never 0: absence and a genuine dot ball have to stay
// distinguishable, or the device is back to inventing the figure this exists to stop it inventing.
// Mirrored by parseBallRuns in CommentaryParsing.kt; CommentaryRunsParityTest fails the build on
// a one-sided edit.
// The outcome clause parser lives in llm.js, which classifyEvent shares it with; see
// outcomeClause there for why the import runs in this direction.
const RUNS_OFF_BAT = /^(\d{1,3}) runs?$/i;
const NO_RUN = /^no run$/i;
const OUTCOME_FOUR = /^four$/i;
const OUTCOME_SIX = /^six$/i;
// Two wordings, both live. Cricbuzz pushes a short flash node ("THATS OUT!! Caught!!") and then the
// detailed one ("out Caught by Steven Smith!! ..."). Measured over 124 deliveries across 8 live
// matches, the flash form was 11 of the 12 clauses the `^out\b`-only version could not read.
const OUTCOME_WICKET = /^(?:out\b|that'?s out\b)/i;
// Extras, in Cricbuzz's own shorthand: "1 wd", "2 nb", "1 lb", "4 b", plus the bare word it uses
// when the extra is worth one. The captured word decides the KIND, which travels as data for the
// same reason the runs do — the rewrite turns "wide" into "strays down leg" and the device was
// deciding `isWide` by searching that prose for the word.
const EXTRA_KIND = /^(?:(\d{1,3})\s*)?(wd|wides?|nb|no.?balls?|lb|leg ?byes?|byes?|b)$/i;
// Cricbuzz writes the bare-word extras with their runs in the NEXT segment — "leg byes, 1 run" —
// where the shorthand carries them in the same one ("4 lb"). A boundary bye puts the word FOUR
// there rather than a numeral ("byes, FOUR" — `B4` in the over summary), which the numeric-only
// version scored as 1.
const EXTRA_FOLLOW = /^[^,]+,\s*[^,]+,\s*(?:(\d{1,3}) runs?|(four|six))\b/i;

// Named, not inline, so CommentaryRunsParityTest can compare them against the Kotlin copies. An
// unnamed literal here is a pattern the guard cannot see, which is how a one-sided edit gets in.
const KIND_WIDE = /^(?:wd|wides?)$/i;
const KIND_NOBALL = /^(?:nb|no.?balls?)$/i;
const KIND_LEGBYE = /^(?:lb|leg ?byes?)$/i;
const KIND_BYE = /^(?:byes?|b)$/i;

const EXTRA_KINDS = [
  [KIND_WIDE, "WIDE"],
  [KIND_NOBALL, "NOBALL"],
  [KIND_LEGBYE, "LEGBYE"],
  [KIND_BYE, "BYE"],
];

function extraKindOf(word) {
  for (const [re, kind] of EXTRA_KINDS) if (re.test(word)) return kind;
  return null;
}

function followRunsOf(text) {
  const m = String(text).match(EXTRA_FOLLOW);
  if (!m) return null;
  if (m[1]) return Number(m[1]);
  return /^four$/i.test(m[2]) ? 4 : 6;
}

/**
 * What the delivery was worth and what kind of delivery it was: `{ runs, kind }`.
 *
 * `runs` is TOTAL runs off the delivery, extras included, because the over total sums it. `kind` is
 * one of WIDE / NOBALL / BYE / LEGBYE, or null for an ordinary delivery.
 */
export function parseBallOutcome(text) {
  const clause = outcomeClause(text);
  if (!clause) return { runs: null, kind: null };
  if (NO_RUN.test(clause)) return { runs: 0, kind: null };
  const offBat = clause.match(RUNS_OFF_BAT);
  if (offBat) return { runs: Number(offBat[1]), kind: null };
  if (OUTCOME_FOUR.test(clause)) return { runs: 4, kind: null };
  if (OUTCOME_SIX.test(clause)) return { runs: 6, kind: null };
  // A dismissal's own clause carries no runs. A run-out that completed one is `W1` in the over
  // summary, but the clause does not say so, and inventing the 1 is the failure this file exists
  // to prevent.
  if (OUTCOME_WICKET.test(clause)) return { runs: 0, kind: null };
  const extra = clause.match(EXTRA_KIND);
  if (!extra) return { runs: null, kind: null };
  const kind = extraKindOf(extra[2]);
  if (!kind) return { runs: null, kind: null };
  return { runs: extraRunsOf(kind, extra[1], text), kind };
}

/**
 * Total runs off an extra: the number the clause states, read the way Cricbuzz means it.
 *
 * Two numbers with two meanings, and Cricbuzz's own over summary is what settles them. An INLINE
 * number is the delivery's total, penalty included: `1 Wd3 0 0 4 W W` totals `overRuns = 8`, so
 * `Wd3` is 3 and not 4. A number in the FOLLOWING segment is the runs scored, to which the
 * penalty is added: match 169561's over 5 reads `B1 0 1 0 1 N2 0` for `overRuns = 6`, so its
 * "no ball, 2 runs" is 3; match 154452's `6 Wd 6 6 1 4 N1 1` totals 27, so "no ball, 1 run" is 2.
 *
 * Only wides and no-balls carry a penalty. Byes and leg byes are the batting side's, so
 * "byes, 1 run" is worth exactly the one run — `B1`.
 */
function extraRunsOf(kind, inline, text) {
  if (inline) return Number(inline);
  const penalty = kind === "WIDE" || kind === "NOBALL" ? 1 : 0;
  const follow = followRunsOf(text);
  // A bare "wide" or "no ball" scored nothing beyond its penalty; a bare "byes" is worth at least
  // the one run Cricbuzz writes as `B1`.
  if (follow === null) return penalty || 1;
  return penalty + follow;
}

export function parseBallRuns(text) {
  return parseBallOutcome(text).runs;
}

/**
 * The over being bowled, as the miniscore states it: its glyphs and its number. Null when the
 * miniscore carries nothing to read.
 *
 * `recentOvsStats` is the string behind Cricbuzz's own "this over" bar, and it is live. Sampled
 * across three county matches on 2026-09-17 it is normally one over: `1` at 43.1, `0 1 0` at 42.3,
 * `4 4` at 30.2, and at an over break the over that just ended (`1 0 0 0 0 0` at 44). But the same
 * field also arrived as `...  | 0 4 0 0 0 1  | 0 0 2 1 0 W` at 118 — several overs joined by `|`.
 *
 * So the bar is not trusted to be one over. The ball count in `overs` says how many legal deliveries
 * the over holds (97.3 → 3, 23 → 6), and exactly that many are taken from the end, counting only
 * legal balls: a wide or no-ball is re-bowled, so `Wd 1` at x.1 is two glyphs and one ball. Extras
 * immediately before the first of them belong to this over too, because the previous over always
 * ends on a legal delivery.
 *
 * The Hundred writes `recentOvsStats: ""` and counts balls in `overs` (72, 100), so it gets no over
 * number and no trimming, and an empty string falls back to the over summaries.
 */
export function liveOverOf(ms) {
  const raw = typeof ms?.recentOvsStats === "string" ? ms.recentOvsStats : "";
  const glyphs = raw.split(/[\s|]+/).filter((g) => g && g !== "...");
  if (glyphs.length === 0) return null;
  if (ms.matchScoreDetails?.matchFormat === "HUN") return { balls: glyphs, over: null };
  const position = overPosition(ms.overs);
  if (!position) return { balls: glyphs, over: null };
  return { balls: lastLegalBalls(glyphs, position.balls), over: position.over };
}

/** 97.3 is 3 balls into over 98; 23 is over 23 complete. Null before a ball is bowled. */
function overPosition(overs) {
  const n = Number(overs);
  if (!Number.isFinite(n) || n <= 0) return null;
  const whole = Math.floor(n);
  const part = Math.round((n - whole) * 10);
  return part > 0 ? { over: whole + 1, balls: part } : { over: whole, balls: 6 };
}

// A wide or a no-ball is not one of the over's six: `Wd`, `Wd3`, `Nb`, `N1`. Byes and leg byes are.
const ILLEGAL_DELIVERY = /^(?:WD\d*|NB|N\d+)$/i;

/** The last `count` legal deliveries, with the extras bowled among and just before them. */
function lastLegalBalls(glyphs, count) {
  let legal = 0;
  let start = glyphs.length;
  while (start > 0 && legal < count) {
    start--;
    if (!ILLEGAL_DELIVERY.test(glyphs[start])) legal++;
  }
  while (start > 0 && ILLEGAL_DELIVERY.test(glyphs[start - 1])) start--;
  return glyphs.slice(start);
}

/**
 * The most recently COMPLETED over: its glyphs and its number. The fallback for a page whose
 * miniscore carries no `recentOvsStats`.
 *
 * Cricbuzz hangs `overSummary` off the delivery that ENDS an over (ball `48.6` carries
 * `overNumber: 49`), so this is never the over being bowled. The number travels with the glyphs
 * because the device cannot otherwise tell whether they describe the over its commentary feed is
 * showing or the one before it — that mismatch is what made a strip headed "This Over" show the
 * previous one.
 */
function parseRecentBallsNext(html) {
  const data = [];
  forEachNextFlightPayload(html, (d) => collectOverSummaries(d, data));
  if (data.length === 0) return { balls: [], over: null };
  const latest = data.reduce((a, b) => (newerOverSummary(b, a) ? b : a));
  return { balls: latest.summary.trim().split(" ").filter(Boolean), over: latest.over };
}

/** Later by clock; by over number only when neither side carries one. */
function newerOverSummary(candidate, best) {
  if (candidate.ts !== null && best.ts !== null) return candidate.ts > best.ts;
  if (candidate.ts !== null) return true;
  if (best.ts !== null) return false;
  return candidate.over > best.over;
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

// The player's name for one `scorecard-*-grid` row.
//
// Cricbuzz's current markup puts the name in a `span.hover:underline`. The row's only <a> elements
// are a hidden per-player menu — "View match performance", then "View profile" pointing at
// /profiles/{id}/{slug} — so the long-standing `$row.find("a").first()` named EVERY batter and
// EVERY bowler "View match performance". Those names were written to `match-scorecard` and then
// rendered by the Android Scorecard tab, where two list items sharing a key is a crash.
//
// Order matters: the span first, then a link that is not one of the menu labels, so older markup
// with the name in the anchor still parses. Mirrors playerNameOf in CricBuzzParser.kt.
const MENU_LINK_LABELS = ["View match performance", "View profile"];

function playerNameOf($, $row) {
  const span = $row.find("span[class*='hover:underline']").first().text().trim();
  if (span) return span;
  const link = $row
    .find("a")
    .toArray()
    .map((a) => $(a).text().trim())
    .find((text) => text && !MENU_LINK_LABELS.includes(text));
  return link || "";
}

// The player's Cricbuzz profile link, which is a menu item rather than the name's own anchor.
function profileUrlOf($, $row) {
  return absUrl($row.find('a[href*="/profiles/"]').first().attr("href"));
}

function parseScorecardBatsmen($, $content) {
  const out = [];
  $content.find("div.grid.scorecard-bat-grid").each((_, row) => {
    const $row = $(row);
    const name = playerNameOf($, $row);
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
      profileUrl: profileUrlOf($, $row),
    });
  });
  return out;
}

function parseScorecardBowlers($, $content) {
  const out = [];
  $content.find("div.grid.scorecard-bowl-grid").each((_, row) => {
    const $row = $(row);
    const name = playerNameOf($, $row);
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
      profileUrl: profileUrlOf($, $row),
    });
  });
  return out;
}

// Fall of Wickets is rendered as sibling `div.scorecard-fow-grid` rows, each section introduced by
// a bold header row ("Fall of Wickets", "Powerplays", "Partnerships"). Returns the row ELEMENTS,
// not their flattened text: the name cell holds the hidden per-player menu alongside the name span,
// so the caller has to run playerNameOf over it rather than read it as text.
function fowGridRows($, $content, header) {
  const out = [];
  let inSection = false;
  $content.find("div.scorecard-fow-grid").each((_, el) => {
    const head = $(el).children().first().text().trim();
    if (new RegExp(`^${header}$`, "i").test(head)) { inSection = true; return; }
    if (/^(Fall of Wickets|Powerplays|Partnerships)$/i.test(head)) {
      if (inSection) { inSection = false; }
      return;
    }
    if (inSection) out.push(el);
  });
  return out;
}

// "19(31)" -> ["19", "31"]. The figure is embedded in a cell that also carries the player's name
// and menu labels, so it is matched out rather than read as the cell's whole text.
function runsAndBalls(text) {
  const m = /(\d+)\s*\(\s*(\d+)\s*\)/.exec(String(text || ""));
  return m ? [m[1], m[2]] : ["", ""];
}

function parseFallOfWickets($, $content) {
  return fowGridRows($, $content, "Fall of Wickets")
    .map((el) => $(el).children())
    .filter((cells) => cells.length >= 3)
    .map((cells, idx) => {
      // cells = [name span + hidden menu, "score-wicketNumber" (e.g. "11-1"), over (e.g. "1.6")]
      const scoreCell = txt($, cells[1]);
      const [runs, wkt] = scoreCell.split("-");
      return {
        score: (runs || scoreCell).trim(),
        overs: txt($, cells[2]),
        batsman: playerNameOf($, $(cells[0])),
        number: Number(wkt) || idx + 1,
      };
    });
}

// Partnerships are NOT fow-grid rows. They are `div.scorecard-pship-grid`, which is why the section
// walk above found none of them and `partnerships` reached every device empty. Three cells:
// [player 1 + their "19(31)", partnership "37(64)", "18(33)" + player 2].
function parsePartnerships($, $content) {
  return $content
    .find("div.scorecard-pship-grid")
    .toArray()
    .map((el) => $(el).children())
    .filter((cells) => cells.length >= 3)
    .map((cells) => {
      const [runs, balls] = runsAndBalls(txt($, cells[1]));
      // Runs only: PremiumScorecardTab reads contribution1/2 with toIntOrNull, which "19(31)"
      // fails, giving a silent 0 rather than a missing figure.
      return {
        player1: playerNameOf($, $(cells[0])),
        player2: playerNameOf($, $(cells[2])),
        runs,
        balls,
        contribution1: runsAndBalls(txt($, cells[0]))[0],
        contribution2: runsAndBalls(txt($, cells[2]))[0],
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

/**
 * Terminal markers, mirroring `TERMINAL` in the device's `MatchStatusClassifier.kt`.
 *
 * Kept as escaped pattern STRINGS rather than regex literals so `MatchStatusParityTest` can lift
 * them out of this file and run them against the same fixtures the Kotlin list is run against —
 * `cricbuzz.js` is a declared Gradle test input, so editing one side alone fails the build. A
 * mirrored rule that nothing compares is not a pinned rule, which this repo has now learned twice.
 *
 * Two properties are load-bearing and neither is obvious:
 *  - **Precedence, not position.** A terminal marker anywhere in the string wins, however much
 *    in-progress prose surrounds it. "Match drawn, England 2nd innings 340/6" is over.
 *  - **Word boundaries, and never a bare `won`.** "India won the toss and elected to bat" is the
 *    START of a match. "tea" must not fire inside "team", "draw" not inside "drawing".
 */
const TERMINAL_STATUS_PATTERNS = [
  "\\bwon\\s+by\\b",
  "\\bwon\\s+the\\s+(match|series|game)\\b",
  "\\bbeat\\b",
  "\\bdrawn\\b",
  "\\bdraw\\b",
  "\\btied\\b",
  "\\bno\\s+result\\b",
  "\\babandon(ed)?\\b",
  "\\bcomplete[d]?\\b",
  "\\bwins\\b",
  "\\bwin\\s+by\\b",
  "\\bconcede[ds]?\\b",
  "\\bforfeit(ed)?\\b",
  "\\belected\\s+not\\s+to\\s+bat\\b",
];

const TERMINAL_STATUS_RE = TERMINAL_STATUS_PATTERNS.map((p) => new RegExp(p, "i"));

/**
 * Whether a Cricbuzz status line says the match is finished.
 *
 * This is what lets match-sync write one last detail and scorecard row before the match leaves
 * `live-matches.json` for good. Without it the row froze wherever the 15-minute slow tier last
 * left it and stayed frozen, which is what made the match-detail screen disagree with the Recent
 * list about the score.
 */
export function isTerminalStatus(status) {
  if (!status) return false;
  const text = String(status);
  return TERMINAL_STATUS_RE.some((re) => re.test(text));
}

// Parse the live page into the device-shaped match-live-detail payload,
// driven by the flight-payload miniscore object.
export function parseLiveDetail(liveHtml) {
  const ms = findMiniscore(collectPayloads(liveHtml));
  const { balls: recentBalls, over: recentBallsOver } = parseRecentBallsNext(liveHtml);
  if (!ms) {
    return {
      batsmen: [],
      bowler: null,
      recentBalls,
      recentBallsOver,
      score: null,
      team1Score: null,
      team1Overs: null,
      team1Name: null,
      team1Id: null,
      team2Score: null,
      team2Overs: null,
      team2Name: null,
      team2Id: null,
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
  const sides = teamInningsSides(isl, ms.matchScoreDetails?.matchFormat);
  const batScore = ms.batTeam ? `${ms.batTeam.teamScore}/${ms.batTeam.teamWkts}` : null;
  const live = liveOverOf(ms);

  return {
    batsmen,
    bowler: mapBowler(ms.bowlerStriker),
    recentBalls: live ? live.balls : recentBalls,
    recentBallsOver: live ? live.over : recentBallsOver,
    score: batScore ? `${batScore} (${s(ms.overs, "")})` : null,
    team1Score: sides[0]?.score ?? null,
    team1Overs: sides[0]?.overs ?? null,
    team1Name: sides[0]?.name ?? null,
    team1Id: sides[0]?.id ?? null,
    team2Score: sides[1]?.score ?? null,
    team2Overs: sides[1]?.overs ?? null,
    team2Name: sides[1]?.name ?? null,
    team2Id: sides[1]?.id ?? null,
    currentRunRate: ms.currentRunRate != null ? String(ms.currentRunRate) : null,
    requiredRunRate: ms.requiredRunRate != null ? String(ms.requiredRunRate) : null,
    status: ms.status ? String(ms.status) : null,
    situation: liveSituation(ms, isl),
  };
}

/** Ten wickets ends an innings; a completed multi-day innings is then written as runs alone. */
const ALL_OUT_WICKETS = 10;

/** The formats in which a side bats twice, so its score is two innings joined. */
const MULTI_DAY_FORMATS = new Set(["TEST", "FC"]);

/**
 * The two batting sides, each with every innings it has batted joined: "266 & 83/2".
 *
 * `inningsScoreList` is a flat list of innings, so reading it as [0]/[1] is per-team only in a
 * limited-overs match. A Test has up to four entries and the third and fourth were dropped on the
 * floor, so a side that had batted twice reached the device holding one of its two innings.
 *
 * The side order is first-innings side first, matching the team1/team2 convention the rest of this
 * file uses; `name` is emitted so the device can align that on its own team order rather than
 * assuming the first-innings side is the match's team1.
 */
export function teamInningsSides(isl, matchFormat) {
  const multiDay = MULTI_DAY_FORMATS.has(String(matchFormat ?? "").toUpperCase());
  const order = [];
  const byName = new Map();
  for (const entry of isl) {
    const name = entry.batTeamName ? String(entry.batTeamName) : "";
    if (!byName.has(name)) {
      order.push(name);
      byName.set(name, []);
    }
    byName.get(name).push(entry);
  }
  return order.slice(0, 2).map((name) => {
    const entries = byName.get(name);
    const last = entries[entries.length - 1];
    return {
      name: name || null,
      // The id is what the device aligns on: `batTeamName` is a short code ("EZONE", "RSAA") that
      // matches no full team name, while this is the same id Cricbuzz puts on matchInfo.team1/2.
      id: last.batTeamId != null ? String(last.batTeamId) : null,
      score: entries
        .map((e) =>
          multiDay && Number(e.wickets) >= ALL_OUT_WICKETS ? `${e.score}` : `${e.score}/${e.wickets}`
        )
        .join(" & "),
      overs: s(last.overs, ""),
    };
  });
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
export function liveSituation(ms, isl) {
  const num = (v) => (typeof v === "number" && Number.isFinite(v) ? v : null);
  const current = isl.find((e) => e.inningsId === ms.inningsId) ?? isl[isl.length - 1];
  if (!current) return null;

  const ballsBowled = num(current.ballNbr);
  // A chase is the LAST innings of the match with a target, not innings 2. Hardcoding 2 meant a
  // Test's fourth innings — the only innings of a Test that is a run chase — never carried a target
  // or a runs-needed, so the one position a Test model most needs to read arrived empty.
  const isChase = num(ms.target) != null && current.inningsId === lastInningsId(ms);
  const battingTeamName = current.batTeamName ? String(current.batTeamName) : null;

  return {
    inningsId: num(ms.inningsId),
    matchFormat: ms.matchScoreDetails?.matchFormat ? String(ms.matchScoreDetails.matchFormat) : null,
    battingTeamName,
    // Match on team name, not innings id: a Test has four innings entries, two of them belonging
    // to the side currently batting, so "some other innings" picks the wrong team.
    bowlingTeamName: isl.find((e) => e.batTeamName && e.batTeamName !== current.batTeamName)?.batTeamName ?? null,
    // Team identity, not innings identity. A Test side bats twice, so comparing innings ids called
    // the third innings "not team1" even when the first-innings side was the one batting — and the
    // only consumer flips the win probability on this, so it landed against the wrong team for half
    // of every Test. Innings ids remain the fallback for a miniscore carrying no batTeamId.
    battingTeamIsTeam1: current.batTeamId != null && isl[0]?.batTeamId != null
      ? String(current.batTeamId) === String(isl[0].batTeamId)
      : current.inningsId === isl[0]?.inningsId,
    // Which side "team1" means here, as an id the device can compare against its own team order.
    // The name cannot serve: Cricbuzz writes it as a short code that matches no full team name.
    firstInningsTeamId: isl[0]?.batTeamId != null ? String(isl[0].batTeamId) : null,
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
    long: longSituation(ms, isl, current, battingTeamName),
  };
}

/** The innings the match will finish in, so a fourth-innings Test chase is recognised as one. */
function lastInningsId(ms) {
  const list = ms.matchScoreDetails?.inningsScoreList ?? [];
  return list.reduce((max, e) => Math.max(max, Number(e.inningsId) || 0), 0);
}

/**
 * Multi-day state for the three-outcome model, or null when this is not a multi-day match.
 *
 * Everything here comes from `inningsScoreList`, which a Test populates with up to four entries.
 * The quantities the model wants are match-level rather than innings-level — total balls bowled by
 * both sides, the running lead, how many innings each side has used — and none of them are in the
 * miniscore as single fields; they only exist as a fold over that list.
 */
function longSituation(ms, isl, current, battingTeamName) {
  const format = String(ms.matchScoreDetails?.matchFormat ?? "").toUpperCase();
  if (format !== "TEST" && format !== "FC") return null;
  if (!battingTeamName) return null;

  let ballsBowled = 0;
  let battingRuns = 0;
  let bowlingRuns = 0;
  let ownInningsUsed = 0;
  let oppInningsUsed = 0;
  for (const entry of isl) {
    ballsBowled += Number(entry.ballNbr) || 0;
    const runs = Number(entry.score) || 0;
    if (entry.batTeamName === battingTeamName) {
      battingRuns += runs;
      ownInningsUsed++;
    } else {
      bowlingRuns += runs;
      oppInningsUsed++;
    }
  }

  return {
    // Position within the match, zero-based, so innings four is index 3 — the value the model's
    // isFourthInnings feature keys off.
    inningsIndex: Math.max(0, (Number(current.inningsId) || 1) - 1),
    lead: battingRuns - bowlingRuns,
    ballsBowled,
    ownInningsUsed,
    oppInningsUsed,
    // Cricbuzz flags the follow-on on the innings it applies to.
    followedOn: isl.some((e) => e.isFollowOn === true),
    declared: isl.some((e) => e.isDeclared === true),
    // A Test is five days; a first-class match four. Cricbuzz publishes no scheduled-days field, and
    // the format is the only thing that distinguishes them.
    scheduledDays: format === "TEST" ? 5 : 4,
  };
}
