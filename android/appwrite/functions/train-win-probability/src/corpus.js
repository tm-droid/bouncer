/**
 * The Cricsheet match, compacted with everything the context features need.
 *
 * The trainer used to keep one byte per delivery — runs, wickets, legality — and dropped
 * the venue, the toss and every player. This keeps those too: who faced, who bowled, who was at
 * the other end, what the batter scored off the ball and who was dismissed, each as a one-byte
 * index into the match's own list of people. Five bytes a delivery takes the corpus from about
 * 14 MB to about 70 MB, which still fits beside the feature rows.
 */

import { venueKey } from "./contextmath.js";
import { formatClassOf, inningsBudgetBalls, matchDate, packDelivery } from "./training.js";

/** Dismissals the bowler is not credited with. */
const NOT_BOWLERS_WICKET = new Set(["run out", "retired hurt", "retired out", "obstructing the field", "retired not out", "timed out"]);

/** Bit set on an `out` byte when the bowler is credited with the dismissal. */
export const OUT_BOWLER_CREDIT = 0x80;
const OUT_INDEX_MASK = 0x7f;

/** The index (into `people`) of the player dismissed on a delivery, or -1. */
export function outIndex(code) {
  return (code & OUT_INDEX_MASK) - 1;
}

/**
 * Compact one Cricsheet match with its context, or null when it is not a format we model.
 *
 * People are identified by Cricsheet's registry id where the match carries one and by name
 * otherwise; the registry id is what makes "V Kohli" one person across a thousand files.
 */
export function richSkeletonOf(raw) {
  const info = raw?.info;
  const formatClass = formatClassOf(info);
  if (!formatClass || (info.teams?.length ?? 0) !== 2) return null;

  const registry = info.registry?.people ?? {};
  const people = [];
  const peopleNames = [];
  const indexByName = new Map();
  const indexOf = (name) => {
    if (!name) return 0;
    let idx = indexByName.get(name);
    if (idx === undefined) {
      idx = people.length;
      // 127 distinct people is far past any real match; the cap only keeps the byte honest.
      if (idx >= OUT_INDEX_MASK) return OUT_INDEX_MASK - 1;
      people.push(registry[name] ?? `name:${name}`);
      peopleNames.push(name);
      indexByName.set(name, idx);
    }
    return idx;
  };

  const xi = {};
  for (const team of info.teams) xi[team] = (info.players?.[team] ?? []).map(indexOf);

  const innings = [];
  for (const inning of raw?.innings ?? []) {
    const deliveries = [];
    for (const over of inning?.overs ?? []) {
      for (const delivery of over?.deliveries ?? []) deliveries.push(delivery);
    }
    const n = deliveries.length;
    const balls = new Uint8Array(n);
    const batter = new Uint8Array(n);
    const bowler = new Uint8Array(n);
    const nonStriker = new Uint8Array(n);
    const batRuns = new Uint8Array(n);
    const out = new Uint8Array(n);
    for (let i = 0; i < n; i++) {
      const d = deliveries[i];
      const extras = d.extras ?? {};
      const legal = extras.wides == null && extras.noballs == null;
      balls[i] = packDelivery(d.runs?.total ?? 0, d.wickets?.length ?? 0, legal);
      batter[i] = indexOf(d.batter);
      bowler[i] = indexOf(d.bowler);
      nonStriker[i] = indexOf(d.non_striker);
      batRuns[i] = Math.min(255, d.runs?.batter ?? 0);
      const wicket = d.wickets?.[0];
      if (wicket?.player_out) {
        const credit = NOT_BOWLERS_WICKET.has(wicket.kind) ? 0 : OUT_BOWLER_CREDIT;
        out[i] = ((indexOf(wicket.player_out) + 1) & OUT_INDEX_MASK) | credit;
      }
    }
    innings.push({ team: inning?.team, balls, batter, bowler, nonStriker, batRuns, out });
  }

  return {
    teams: info.teams,
    gender: info.gender ?? "male",
    formatClass,
    date: matchDate(info),
    // The schedule, as serving has it (cricbuzz.js): 5 for a Test, 4 otherwise. `dates.length` is
    // days played and gives a short finish away.
    scheduledDays: formatClass === "TEST" ? 5 : 4,
    ballsBudget: inningsBudgetBalls(info),
    winner: info.outcome?.winner ?? null,
    result: info.outcome?.result ?? null,
    // "D/L" and friends: the chase was to a revised target the file does not record.
    method: info.outcome?.method ?? null,
    venue: info.venue ?? null,
    city: info.city ?? null,
    venueKey: venueKey(info.venue, info.city),
    toss: info.toss?.winner ? { winner: info.toss.winner, decision: info.toss.decision ?? null } : null,
    people,
    peopleNames,
    xi,
    innings,
  };
}
