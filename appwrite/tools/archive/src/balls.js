// The pure half of the ball-by-ball backfill (`backfill-balls.mjs`): Cricbuzz commentary items in,
// the history site's delivery arrays out. No network and no disk.
//
// NO COMMENTARY TEXT SURVIVES. `recordOf` reads `commText` once, for the runs, the extra's kind and
// the kind of dismissal, and returns numbers and short codes. Nothing downstream ever sees the text.
//
// Delivery shape (`.claude/rules/history-data.md`): [over, ball, batter, bowler, nonStriker,
// batRuns, extraRuns, extrasBits, wickets?]. batter/bowler/nonStriker index the match's people list
// `p` = [[name, cricbuzzPlayerId], ...] (-1 = unknown). `over` is 0-based; `ball` is the scorer's
// label, so a wide keeps the number of the ball it replaces. A wicket is [kind, outIdx].
// Bits: 1 wide, 2 no-ball, 4 bye, 8 leg-bye, 16 penalty, 32 non-boundary.

import { parseBallOutcome } from "./ball-outcome.js";

export const BITS = { WIDE: 1, NOBALL: 2, BYE: 4, LEGBYE: 8, PENALTY: 16, NON_BOUNDARY: 32 };

/** The host said 403/429. Callers stop the whole run and save state. */
export class CricbuzzBlockedError extends Error {
  constructor(status, url = "") {
    super(`Cricbuzz answered ${status}${url ? ` for ${url}` : ""}`);
    this.name = "CricbuzzBlockedError";
    this.status = status;
    this.blocked = true;
  }
}

export const commentaryUrl = (matchId, inningsId, ts) =>
  `https://www.cricbuzz.com/api/mcenter/commentary-pagination/${matchId}/${inningsId}/${ts}`;

// Dismissal kind, read from the line's own words. Order matters: "c & b" before "caught".
const DISMISSALS = [
  [/\bc ?& ?b\b|caught and bowled|caught & bowled/i, "caught and bowled"],
  [/\bstumped\b|\bst\b/i, "stumped"],
  [/\brun ?out\b/i, "run out"],
  [/\blbw\b|leg before/i, "lbw"],
  [/\bhit.?wicket\b/i, "hit wicket"],
  [/\bcaught\b/i, "caught"],
  [/\bbowled\b/i, "bowled"],
  [/\bretired\b/i, "retired out"],
  [/\bobstruct/i, "obstructing the field"],
  [/\bhandled\b/i, "handled the ball"],
  [/\btimed out\b/i, "timed out"],
];
const dismissalOf = (text) => DISMISSALS.find(([re]) => re.test(text))?.[1] ?? "wicket";

const tenths = (bm) => Math.round(Number(bm) * 10);

/**
 * One commentary item as a structured record, or null when it is not a delivery (an over separator,
 * a bowler-change note, the end-of-match report: none has a `ballMetric`).
 *
 * Record: {ts, t (over*10+ball), ev (bit 1 four, 2 six, 4 wicket), bat [id,name], bowl [id,name],
 * runs (total off the ball, null = unreadable), kind (WIDE|NOBALL|BYE|LEGBYE|null), wk (dismissal
 * kind or null)}.
 */
export function recordOf(item, parseOutcome = parseBallOutcome) {
  if (!item || item.commType === "over-separator" || item.ballMetric == null) return null;
  const t = tenths(item.ballMetric);
  if (!Number.isFinite(t) || t % 10 === 0 || t < 1) return null;
  const events = Array.isArray(item.event) ? item.event : [];
  // Cricbuzz marks the outcome up (`<b>FOUR</b>`); the parser reads plain text.
  const text = String(item.commText ?? "").replace(/<[^>]*>/g, "");
  const { runs, kind } = parseOutcome(text);
  const wicket = events.includes("wicket");
  const ev = (events.includes("four") ? 1 : 0) | (events.includes("six") ? 2 : 0) | (wicket ? 4 : 0);
  const who = (d) => [d?.playerId ? String(d.playerId) : "", String(d?.playerName ?? "")];
  return {
    ts: Number(item.timestamp),
    t,
    ev,
    bat: who(item.batsmanDetails),
    bowl: who(item.bowlerDetails),
    runs,
    kind,
    wk: wicket ? dismissalOf(text) : null,
  };
}

const keyOf = (r) => `${r.ts}:${r.t}`;

/**
 * Walks one innings backwards from the newest item. `st` is the innings' persisted state and is
 * mutated in place: `{cursor, recs, done}`. One `fetchPage(cursor)` per loop turn returns the raw
 * items older than `cursor`, newest first. Stops (done) on an empty page, a page with nothing new,
 * or a cursor that did not move; stops (not done) when `canRequest()` says the budget is spent.
 * A throw from `fetchPage` (a block) propagates with `st` already holding everything fetched so far.
 */
export async function crawlInnings({ fetchPage, st, canRequest = () => true, parseOutcome = parseBallOutcome }) {
  const seen = new Set((st.recs ?? []).map(keyOf));
  st.recs ??= [];
  while (!st.done) {
    if (!canRequest()) return "budget";
    const items = await fetchPage(st.cursor);
    const stamps = (items ?? []).map((i) => Number(i?.timestamp)).filter(Number.isFinite);
    if (stamps.length === 0) {
      st.done = 1;
      return "empty";
    }
    let fresh = 0;
    for (const item of items) {
      const r = recordOf(item, parseOutcome);
      if (!r || seen.has(keyOf(r))) continue;
      seen.add(keyOf(r));
      st.recs.push(r);
      fresh++;
    }
    const oldest = Math.min(...stamps);
    if (oldest >= st.cursor) {
      st.done = 1;
      return "stuck";
    }
    st.cursor = oldest;
    if (fresh === 0 && items.every((i) => i?.ballMetric == null)) {
      // A page of nothing but separators and notes: keep walking (the cursor moved). A page whose
      // balls were all seen already is a repeat, and the only way out is to stop.
      continue;
    }
    if (fresh === 0) {
      st.done = 1;
      return "duplicate";
    }
  }
  return "done";
}

/**
 * Records of one innings as `{p, b, extras...}`: deliveries in bowling order, people appended to the
 * shared `people` (a Map "id|name" -> index). The non-striker is inferred from who faced before,
 * because Cricbuzz names only the striker and bowler per ball.
 */
export function deliveriesOf(recs, people) {
  const idx = (pair) => {
    if (!pair[0] && !pair[1]) return -1;
    const k = `${pair[0]}|${pair[1]}`;
    if (!people.has(k)) people.set(k, people.size);
    return people.get(k);
  };
  const sorted = [...recs].sort((a, b) => a.ts - b.ts || a.t - b.t);
  const out = [];
  let last = -1;
  let partner = -1;
  let lastWicket = false;
  for (const r of sorted) {
    const s = idx(r.bat);
    if (last !== -1 && s !== last) {
      if (s === partner) partner = last;
      else if (!lastWicket) partner = last;
    }
    let extra = 0;
    let bat = 0;
    let bits = 0;
    const total = r.runs ?? 0;
    if (r.kind === "WIDE") [extra, bits] = [total, BITS.WIDE];
    else if (r.kind === "NOBALL") [extra, bat, bits] = [Math.min(1, total), Math.max(0, total - 1), BITS.NOBALL];
    else if (r.kind === "BYE") [extra, bits] = [total, BITS.BYE];
    else if (r.kind === "LEGBYE") [extra, bits] = [total, BITS.LEGBYE];
    else bat = total;
    // A 4 or 6 the batter ran is not a boundary.
    if ((bat === 4 && !(r.ev & 1)) || (bat === 6 && !(r.ev & 2))) bits |= BITS.NON_BOUNDARY;
    const d = [Math.floor(r.t / 10), r.t % 10, s, idx(r.bowl), partner, bat, extra, bits];
    if (r.wk) d.push([r.wk, s]);
    out.push({ d, unreadable: r.runs === null });
    last = s;
    lastWicket = Boolean(r.wk);
  }
  // Leading balls before a second batter appeared: the first different striker was the partner.
  const firstOther = out.find((o) => o.d[2] !== out[0]?.d[2])?.d[2];
  if (firstOther !== undefined) for (const o of out) if (o.d[4] === -1 && o.d[2] !== firstOther) o.d[4] = firstOther;
  return out;
}

const firstInt = (v) => {
  const n = Number.parseInt(String(v ?? "").trim(), 10);
  return Number.isFinite(n) ? n : null;
};
/** "19.2" -> 116 legal balls. */
export function ballsOfOvers(overs) {
  const m = String(overs ?? "").trim().match(/^(\d+)(?:\.(\d))?$/);
  return m ? Number(m[1]) * 6 + Number(m[2] ?? 0) : null;
}

/**
 * Whether an innings' deliveries add up to its scorecard line `{score, overs}`. Returns the list of
 * problems; empty means complete. Legal balls = not a wide, not a no-ball.
 */
export function inningsProblems(deliveries, card) {
  const problems = [];
  const unreadable = deliveries.filter((o) => o.unreadable).length;
  if (unreadable) problems.push(`${unreadable} deliveries with unreadable runs`);
  let runs = 0;
  let legal = 0;
  for (const { d } of deliveries) {
    runs += d[5] + d[6];
    if (!(d[7] & (BITS.WIDE | BITS.NOBALL))) legal++;
  }
  const total = firstInt(card?.score);
  if (total === null) problems.push("no scorecard total");
  else if (runs !== total) problems.push(`runs ${runs} != total ${total}`);
  const balls = ballsOfOvers(card?.overs);
  if (balls !== null && legal !== balls) problems.push(`legal balls ${legal} != overs ${card.overs}`);
  return problems;
}

/**
 * The `balls` block of a match file from every innings' records.
 * `inns` = [{id, recs}], `cards` = the scorecard's `innings` in the same order.
 * Returns `{balls, problems}`; `problems` is for the run log, the file only carries `partial: 1`.
 */
export function ballsBlock(inns, cards) {
  const people = new Map();
  const problems = [];
  const innings = inns.map(({ id, recs }, i) => {
    const ds = deliveriesOf(recs, people);
    const bad = inningsProblems(ds, cards?.[i]);
    const inn = { i: id, b: ds.map((o) => o.d) };
    if (bad.length) {
      inn.partial = 1;
      problems.push(`innings ${id}: ${bad.join("; ")}`);
    }
    return inn;
  });
  const p = [...people.keys()].map((k) => {
    const cut = k.indexOf("|");
    return [k.slice(cut + 1), k.slice(0, cut)];
  });
  return { balls: { v: 1, p, inn: innings }, problems };
}
