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
 * Record: {ts, t (over*10+ball), ev (bit 1 four, 2 six, 4 wicket), bat ["", striker's name from the line], bowl [id,name],
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
  const wicket = events.includes("wicket");
  let { runs, kind } = parseOutcome(text);
  // A wicket line reads "out ... Run Out!! 1 run completed": the outcome clause is "out", which the
  // parser scores 0, but the batters ran. (A line with no "completed" is a plain dismissal, 0.)
  if (wicket && !kind) {
    const done = text.match(/\b(\d{1,2}) runs? completed\b/i);
    if (done) runs = Number(done[1]);
  }
  const ev = (events.includes("four") ? 1 : 0) | (events.includes("six") ? 2 : 0) | (wicket ? 4 : 0);
  const who = (d) => [d?.playerId ? String(d.playerId) : "", String(d?.playerName ?? "")];
  // The STRIKER is the name in the line's own "Bowler to Batter," lead. `batsmanDetails` is the
  // batter at the crease AFTER the ball (the other one after an odd run or an over's last ball, and
  // empty on a wicket), so it is not who faced it: measured on 17685-17687, where reading it
  // misattributed 5-10 runs per batter. The id is resolved later against the scorecard's batsmen.
  const lead = text.match(/^\s*.+?\s+to\s+(.+?),/);
  return {
    ts: Number(item.timestamp),
    t,
    ev,
    bat: ["", lead ? lead[1].trim() : ""],
    nx: item.batsmanDetails?.playerId ? String(item.batsmanDetails.playerId) : "",
    bowl: who(item.bowlerDetails),
    runs,
    kind,
    wk: wicket ? dismissalOf(text) : null,
  };
}

const keyOf = (r) => `${r.ts}:${r.t}`;

/**
 * Walks one innings backwards from the newest item. `st` is the innings' persisted state and is
 * mutated in place: `{cursor, recs, cp, done}`. One `fetchPage(cursor)` per loop turn returns the raw
 * items older than `cursor`, newest first. Stops (done) on an empty page, a page with nothing new,
 * or a cursor that did not move; stops (not done) when `canRequest()` says the budget is spent.
 * A throw from `fetchPage` (a block) propagates with `st` already holding everything fetched so far.
 *
 * PAGING SEMANTICS (measured on 17685-17687): the endpoint is one match-wide commentary stream, newest
 * first, and `inningsId` in the URL only picks where the stream starts. Walking an innings back past
 * its first ball runs straight on into the OTHER innings (older), so every item is filtered on its own
 * `inningsId` (`innings`), and a non-empty page with none of ours ends the walk. The cursor is
 * exclusive (items strictly older), and `timestamp` is unique per item, so it is the dedupe key
 * (over.ball is not: a wide and the ball bowled again share a label).
 * `st.cp[n]` = runs on the board after `n` overs, from the over separators, for diagnostics only.
 */
export async function crawlInnings({ fetchPage, st, innings = null, canRequest = () => true, parseOutcome = parseBallOutcome }) {
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
    const ours = innings == null ? items : items.filter((i) => i?.inningsId == null || Number(i.inningsId) === Number(innings));
    if (ours.length === 0) {
      st.done = 1;
      return "foreign";
    }
    let fresh = 0;
    for (const item of ours) {
      if (item?.commType === "over-separator") {
        const n = Number(item.overSeparator?.overNumber);
        const score = Number.parseInt(String(item.overSeparator?.batTeamObj?.teamScore ?? ""), 10);
        if (Number.isFinite(n) && Number.isFinite(score)) (st.cp ??= {})[n] = score;
        continue;
      }
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
    if (fresh === 0 && ours.every((i) => i?.ballMetric == null)) {
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

const normName = (n) =>
  String(n ?? "").toLowerCase().replace(/\([^)]*\)/g, "").replace(/[^a-z ]/g, "").replace(/\s+/g, " ").trim();
const profileId = (url) => String(url ?? "").match(/\/profiles\/(\d+)/)?.[1] ?? "";

// Commentary on some (mostly associate) matches gives initials: "J Mohammed" for the scorecard's
// "Jason Mohammed" (measured on 18284/18286), "S Thompson" for "Stuart Thompson" (16940).
const initialled = (rn) => {
  const t = rn.split(" ");
  if (t.length < 2) return [];
  const last = t.at(-1);
  const ini = t.slice(0, -1).map((w) => w[0]);
  return [`${ini.join("")} ${last}`, `${ini.join(" ")} ${last}`, `${ini[0]} ${last}`];
};

/** The scorecard's batsmen as [{id, name}], ids from their profile links. */
const rosterOf = (card) =>
  (card?.batsmen ?? []).map((b) => ({ id: profileId(b.profileUrl), name: String(b.name ?? "").replace(/\s*\([^)]*\)/g, "").trim() }));

/**
 * A commentary name ("Shahzad", "Asghar Stanikzai", "J Mohammed") -> the roster batsman: the exact name,
 * else the one name it prefixes or suffixes or abbreviates to initials. When several fit ("Shahzad" in a
 * side with two), `hintId` breaks the tie: the previous ball's `nx`, the batter Cricbuzz put at the
 * crease after it. `voted` (normalized name -> id, from `votedIds`) comes before the fuzzy rules: it
 * settles nicknames and short forms no name rule can ("Andy Balbirnie" for "Andrew Balbirnie").
 */
function resolveBatter(pair, roster, hintId = "", voted = null) {
  if (pair[0] || !pair[1] || !roster?.length) return pair;
  const n = normName(pair[1]);
  const exact = roster.filter((r) => normName(r.name) === n);
  const id = voted?.get(n);
  const byId = exact.length === 0 && id ? roster.filter((r) => r.id === id) : [];
  const pool =
    exact.length > 0
      ? exact
      : byId.length === 1
        ? byId
        : roster.filter((r) => {
            const rn = normName(r.name);
            return (
              rn.endsWith(` ${n}`) || n.endsWith(` ${rn}`) || rn.startsWith(`${n} `) || n.startsWith(`${rn} `) ||
              initialled(rn).includes(n)
            );
          });
  const hit = pool.length === 1 ? pool[0] : (pool.find((r) => hintId && r.id === hintId) ?? null);
  return hit ? [hit.id, hit.name] : pair;
}

/**
 * Commentary name -> player id, voted from the balls after which the striker cannot have changed (no
 * wicket, even runs, not an over's last ball): there `nx`, the batter on strike after the ball, is the
 * one who faced it. `batsmanDetails` gives the profile name ("Andrew Balbirnie") where the lead says
 * "Andy Balbirnie" (measured on 19316), so the id, not its name, is what joins them. A name needs two
 * votes and a two-thirds majority; a misread ball or a lead the parser got wrong cannot carry it.
 */
function votedIds(recs) {
  const tally = new Map();
  for (const r of recs) {
    if (!r.nx || !r.bat[1] || r.wk || r.runs === null || r.runs % 2 !== 0 || r.t % 10 >= 6) continue;
    const n = normName(r.bat[1]);
    if (!tally.has(n)) tally.set(n, new Map());
    const c = tally.get(n);
    c.set(r.nx, (c.get(r.nx) ?? 0) + 1);
  }
  const out = new Map();
  for (const [n, c] of tally) {
    const total = [...c.values()].reduce((a, b) => a + b, 0);
    const [id, top] = [...c].sort((a, b) => b[1] - a[1])[0];
    if (top >= 2 && top * 3 >= total * 2) out.set(n, id);
  }
  return out;
}

/**
 * Records of one innings as `{p, b, extras...}`: deliveries in bowling order, people appended to the
 * shared `people` (a Map "id|name" -> index). The non-striker is inferred from who faced before,
 * because Cricbuzz names only the striker and bowler per ball.
 */
export function deliveriesOf(recs, people, roster = null) {
  const idx = (pair) => {
    if (!pair[0] && !pair[1]) return -1;
    const k = `${pair[0]}|${pair[1]}`;
    if (!people.has(k)) people.set(k, people.size);
    return people.get(k);
  };
  const sorted = [...recs].sort((a, b) => a.ts - b.ts || a.t - b.t);
  const voted = votedIds(sorted);
  const out = [];
  let last = -1;
  let partner = -1;
  let lastWicket = false;
  let hint = "";
  for (const r of sorted) {
    const s = idx(resolveBatter(r.bat, roster, hint, voted));
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
    hint = r.nx ?? "";
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
 * Per-batter runs off the bat must equal the scorecard's `batsmen[].runs`. Joined on the Cricbuzz
 * player id (the scorecard's profileUrl), else on the name without "(c)" / "(wk)". `people` is the
 * shared list [[name, id], ...]. Returns problem strings.
 */
export function batterProblems(deliveries, card, people) {
  const batsmen = card?.batsmen;
  if (!Array.isArray(batsmen) || batsmen.length === 0 || !people) return [];
  // One player can sit under several people entries (the same id as a bowler "Nabi" and a batter
  // "Mohammad Nabi"), so runs are summed per player key: the id, else the normalized name.
  const keyOfPerson = (p) => (p?.[1] ? `id:${p[1]}` : `n:${normName(p?.[0])}`);
  const got = new Map();
  for (const { d } of deliveries) if (d[2] >= 0) got.set(keyOfPerson(people[d[2]]), (got.get(keyOfPerson(people[d[2]])) ?? 0) + d[5]);
  const out = [];
  const matched = new Set();
  for (const b of batsmen) {
    const want = firstInt(b.runs);
    if (want === null) continue;
    const id = profileId(b.profileUrl);
    const key = id ? `id:${id}` : `n:${normName(b.name)}`;
    matched.add(key);
    // A batter known only by name (no id on a ball) still counts under the id's player.
    const byName = id ? (got.get(`n:${normName(b.name)}`) ?? 0) : 0;
    if (byName) matched.add(`n:${normName(b.name)}`);
    const have = (got.get(key) ?? 0) + byName;
    if (have !== want) out.push(`${normName(b.name)} ${have} != ${want}`);
  }
  for (const [key, runs] of got) if (runs > 0 && !matched.has(key)) out.push(`${key.slice(key.indexOf(":") + 1)} ${runs} not on the scorecard`);
  return out.length ? [`batter runs differ: ${out.slice(0, 4).join(", ")}${out.length > 4 ? ` (+${out.length - 4})` : ""}`] : [];
}

/**
 * WHERE a failing innings is wrong, for the run log: balls whose label never appears in an over
 * (up to the highest label seen, in the last over) (Cricbuzz's stream omits some), and overs whose runs differ from the over
 * separators' score checkpoints (`cp[n]` = runs after n overs).
 */
function whereWrong(deliveries, cp) {
  const labels = new Set(deliveries.map(({ d }) => `${d[0]}.${d[1]}`));
  const overs = new Set(deliveries.map(({ d }) => d[0]));
  const lastOver = Math.max(-1, ...overs);
  const missing = [];
  for (const o of [...overs].sort((a, b) => a - b)) {
    // The last over is only checked up to its highest label: it may be unfinished.
    const top = o < lastOver ? 6 : Math.max(...deliveries.filter(({ d }) => d[0] === o).map(({ d }) => d[1]));
    for (let b = 1; b <= top; b++) if (!labels.has(`${o}.${b}`)) missing.push(`${o}.${b}`);
  }
  const out = [];
  if (missing.length) out.push(`no commentary for ball ${missing.slice(0, 8).join(" ")}${missing.length > 8 ? " ..." : ""}`);
  const perOver = [];
  for (const { d } of deliveries) perOver[d[0]] = (perOver[d[0]] ?? 0) + d[5] + d[6];
  let cum = 0;
  let prevDiff = 0;
  const off = [];
  for (let n = 1; n <= perOver.length; n++) {
    cum += perOver[n - 1] ?? 0;
    if (cp?.[n] === undefined) continue;
    const diff = cum - cp[n];
    if (diff !== prevDiff) off.push(`over ${n}: ${diff - prevDiff > 0 ? "+" : ""}${diff - prevDiff}`);
    prevDiff = diff;
  }
  if (off.length) out.push(`runs vs board by over: ${off.slice(0, 6).join(", ")}`);
  return out;
}

/**
 * Whether an innings' deliveries add up to its scorecard line `{score, overs}` (and, with `ctx.people`,
 * its batsmen). Returns the list of problems; empty means complete. Legal balls = not a wide, not a
 * no-ball. `ctx = {people, cp}`; both optional.
 */
export function inningsProblems(deliveries, card, ctx = {}) {
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
  problems.push(...batterProblems(deliveries, card, ctx.people));
  if (problems.length) problems.push(...whereWrong(deliveries, ctx.cp));
  return problems;
}

const personOf = (k) => {
  const cut = k.indexOf("|");
  return [k.slice(cut + 1), k.slice(0, cut)];
};

/**
 * The `balls` block of a match file from every innings' records.
 * `inns` = [{id, recs}], `cards` = the scorecard's `innings` in the same order.
 * Returns `{balls, problems}`; `problems` is for the run log, the file only carries `partial: 1`.
 */
export function ballsBlock(inns, cards) {
  const people = new Map();
  const problems = [];
  const innings = inns.map(({ id, recs, cp }, i) => {
    const ds = deliveriesOf(recs, people, rosterOf(cards?.[i]));
    const bad = inningsProblems(ds, cards?.[i], { people: [...people.keys()].map(personOf), cp });
    const inn = { i: id, b: ds.map((o) => o.d) };
    if (bad.length) {
      inn.partial = 1;
      problems.push(`innings ${id}: ${bad.join("; ")}`);
    }
    return inn;
  });
  const p = [...people.keys()].map(personOf);
  return { balls: { v: 1, p, inn: innings }, problems };
}
