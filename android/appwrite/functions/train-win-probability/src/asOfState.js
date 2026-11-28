/**
 * Everything known about teams, grounds and players BEFORE a given match.
 *
 * The corpus is walked once in date order. For each match `contextFor` is read first and
 * `update` is called after its rows are built, so a row can never see its own result — the same
 * leak the temporal split exists to stop, closed here per match instead of per window. The state
 * left at the end of the walk is what ships: it is the same quantity, measured on more matches.
 */

import { ELO_SCALE, ELO_START, h2hAdvantage, pairKey, playerRating, sideContext, venueView, xiStrength } from "./contextmath.js";
import { OUT_BOWLER_CREDIT, outIndex } from "./corpus.js";
import { inningsTotals, isLimitedOvers, parRunRateFor, shrunkWinRate, teamKey, unpackDelivery } from "./training.js";

export const ELO_K = 120;

/** Margin of a decided limited-overs result in [0, ~1]: runs / first total batting first; (wickets in hand + balls left) / 2 chasing; 0.3 when unknown. Mirrors the lab's `marginNorm`. */
export function marginNorm(match) {
  if (!match.winner || !isLimitedOvers(match.formatClass) || match.innings.length < 2 || !match.ballsBudget) return 0.3;
  const a = inningsTotals(match.innings[0]);
  const b = inningsTotals(match.innings[1]);
  if (match.winner === match.innings[0].team) return Math.max(0, (a.runs - b.runs) / Math.max(a.runs, 1));
  if (match.winner === match.innings[1].team) return (0.5 * (10 - b.wickets)) / 10 + (0.5 * Math.max(match.ballsBudget - b.legalBalls, 0)) / match.ballsBudget;
  return 0.3;
}

/** The lab's "mov120" multiplier: log margin, damped when the winner was already the favourite. */
export function eloMargin(scoreA, a, b, match) {
  const lead = scoreA === 0.5 ? 0 : scoreA === 1 ? a.elo - b.elo : b.elo - a.elo;
  return (Math.log(1 + marginNorm(match)) * 2.2) / (lead * 0.001 + 2.2);
}
/** Weight of the newest result in a team's form; about the last eight matches. */
export const FORM_ALPHA = 0.25;
/** Per-innings decay of a player's recent figures; about the last ten innings. */
export const RECENT_DECAY = 0.8;

const round = (v, places) => Number(v.toFixed(places));

function bump(map, key, init) {
  let v = map.get(key);
  if (!v) {
    v = init();
    map.set(key, v);
  }
  return v;
}

const newGlobal = () => ({
  matches: 0, firstRatioSum: 0, firstRatioN: 0, chases: 0, chaseWins: 0, draws: 0,
  batRuns: 0, batBalls: 0, outs: 0, bowlRuns: 0, bowlBalls: 0, bowlWickets: 0,
});
const newTeam = () => ({ elo: ELO_START, games: 0, wins: 0, form: 0.5 });
const newVenue = () => ({ matches: 0, firstRatioSum: 0, firstRatioN: 0, chases: 0, chaseWins: 0, draws: 0, decided: 0 });
const newPlayer = () => ({
  runs: 0, balls: 0, outs: 0, innings: 0, rRuns: 0, rBalls: 0, rOuts: 0,
  bowlRuns: 0, bowlBalls: 0, wickets: 0,
});

/** Format means used when the corpus has not yet seen enough to supply its own. */
const SEED = Object.freeze({ runsPerBall: 1.2, runsPerOut: 24, wicketsPerBall: 0.045, chaseWin: 0.5, firstRatio: 0.85, draw: 0.3 });

/** The format means every rating is expressed against, as of now. */
export function globalMeans(g) {
  return {
    runsPerBall: g && g.batBalls > 5000 ? g.batRuns / g.batBalls : SEED.runsPerBall,
    runsPerOut: g && g.outs > 300 ? g.batRuns / g.outs : SEED.runsPerOut,
    bowlRunsPerBall: g && g.bowlBalls > 5000 ? g.bowlRuns / g.bowlBalls : SEED.runsPerBall,
    wicketsPerBall: g && g.bowlBalls > 5000 ? g.bowlWickets / g.bowlBalls : SEED.wicketsPerBall,
    chaseWin: g && g.chases > 50 ? g.chaseWins / g.chases : SEED.chaseWin,
    firstRatio: g && g.firstRatioN > 50 ? g.firstRatioSum / g.firstRatioN : SEED.firstRatio,
    draw: g && g.matches > 50 ? g.draws / g.matches : SEED.draw,
  };
}

export class AsOfState {
  constructor() {
    this.globals = new Map();
    this.teams = new Map();
    this.h2h = new Map();
    this.venues = new Map();
    this.venueVisits = new Map();
    this.players = new Map();
    // Who a registry id is: every name the archive writes them under, and the sides they have
    // played for with the date of the latest match — what a live team sheet is joined against.
    this.people = new Map();
  }

  sliceKey(match) {
    return `${match.gender}|${match.formatClass}`;
  }

  pairKey(match) {
    return pairKey(match.teams[0], match.teams[1], match.gender, match.formatClass);
  }

  playerKey(match, idx) {
    return `${match.people[idx]}|${match.formatClass}`;
  }

  /** What was known going into [match]. Read before `update(match)`. */
  contextFor(match) {
    const slice = this.sliceKey(match);
    const means = globalMeans(this.globals.get(slice));
    const [first, second] = match.teams;

    const team = (name) => {
      const t = this.teams.get(teamKey(name, match.gender, match.formatClass));
      return {
        elo: t?.elo ?? ELO_START,
        games: t?.games ?? 0,
        form: t?.form ?? 0.5,
        winRate: shrunkWinRate(t?.wins ?? 0, t?.games ?? 0),
      };
    };

    const pair = this.h2h.get(this.pairKey(match));
    const firstWins = pair?.wins.get(first) ?? 0;
    const secondWins = pair?.wins.get(second) ?? 0;
    const pairGames = pair?.decided ?? 0;

    const vKey = match.venueKey ? `${match.venueKey}|${slice}` : null;
    const v = vKey ? this.venues.get(vKey) : null;
    const visits = (name) => (match.venueKey ? this.venueVisits.get(`${match.venueKey}|${name}|${match.gender}`) ?? 0 : 0);

    const ratings = match.people.map((_, idx) => playerRating(this.players.get(this.playerKey(match, idx)), means));
    const xi = {};
    for (const name of match.teams) {
      const members = match.xi?.[name] ?? [];
      xi[name] = members.length >= 9 ? xiStrength(members.map((i) => ratings[i])) : null;
    }

    return {
      means,
      teams: { [first]: team(first), [second]: team(second) },
      // Positive favours the first-named side.
      h2h: { games: pairGames, firstAdvantage: h2hAdvantage(firstWins, secondWins, pairGames) },
      venue: { ...venueView(v, means), visits: { [first]: visits(first), [second]: visits(second) } },
      ratings,
      xi,
    };
  }

  /** Fold [match] into the state. Call after its rows have been built. */
  update(match) {
    const slice = this.sliceKey(match);
    const g = bump(this.globals, slice, newGlobal);
    const [first, second] = match.teams;
    const drawn = !match.winner && /^(draw|tie)$/i.test(String(match.result ?? ""));
    const decided = Boolean(match.winner) || drawn;

    g.matches += 1;
    if (drawn) g.draws += 1;

    if (decided) {
      const a = bump(this.teams, teamKey(first, match.gender, match.formatClass), newTeam);
      const b = bump(this.teams, teamKey(second, match.gender, match.formatClass), newTeam);
      const scoreA = match.winner === first ? 1 : match.winner === second ? 0 : 0.5;
      const expectedA = 1 / (1 + 10 ** ((b.elo - a.elo) / ELO_SCALE));
      const delta = ELO_K * eloMargin(scoreA, a, b, match) * (scoreA - expectedA);
      a.elo += delta;
      b.elo -= delta;
      a.games += 1;
      b.games += 1;
      if (scoreA === 1) a.wins += 1;
      if (scoreA === 0) b.wins += 1;
      a.form += FORM_ALPHA * (scoreA - a.form);
      b.form += FORM_ALPHA * (1 - scoreA - b.form);

      const pair = bump(this.h2h, this.pairKey(match), () => ({ decided: 0, wins: new Map() }));
      pair.decided += 1;
      if (match.winner) pair.wins.set(match.winner, (pair.wins.get(match.winner) ?? 0) + 1);
    }

    if (match.venueKey) {
      const v = bump(this.venues, `${match.venueKey}|${slice}`, newVenue);
      v.matches += 1;
      if (drawn) v.draws += 1;
      for (const name of match.teams) {
        const key = `${match.venueKey}|${name}|${match.gender}`;
        this.venueVisits.set(key, (this.venueVisits.get(key) ?? 0) + 1);
      }
      if (isLimitedOvers(match.formatClass) && match.innings.length >= 1) {
        const parTotal = (parRunRateFor(match.formatClass) * match.ballsBudget) / 6;
        const firstRatio = inningsTotals(match.innings[0]).runs / Math.max(parTotal, 1);
        v.firstRatioSum += firstRatio;
        v.firstRatioN += 1;
        g.firstRatioSum += firstRatio;
        g.firstRatioN += 1;
        if (match.winner && match.innings.length >= 2) {
          const won = match.winner === match.innings[1].team ? 1 : 0;
          v.chases += 1;
          v.chaseWins += won;
          g.chases += 1;
          g.chaseWins += won;
        }
      }
    }

    this.updatePlayers(match, g);
  }

  updatePlayers(match, g) {
    for (const team of match.teams) {
      const roster = `${team}|${match.gender}`;
      for (const idx of match.xi?.[team] ?? []) {
        const person = bump(this.people, match.people[idx], () => ({ names: new Set(), teams: new Map() }));
        person.names.add(match.peopleNames[idx]);
        if (match.date) person.teams.set(roster, match.date);
      }
    }
    for (const inning of match.innings) {
      const bat = new Map();
      const bowl = new Map();
      const line = (map, idx) => bump(map, idx, () => ({ runs: 0, balls: 0, outs: 0, wickets: 0 }));
      for (let i = 0; i < inning.balls.length; i++) {
        const d = unpackDelivery(inning.balls[i]);
        const b = line(bat, inning.batter[i]);
        b.runs += inning.batRuns[i];
        if (d.legal) b.balls += 1;
        const w = line(bowl, inning.bowler[i]);
        w.runs += d.runs;
        if (d.legal) w.balls += 1;
        const dismissed = outIndex(inning.out[i]);
        if (dismissed >= 0) {
          line(bat, dismissed).outs += 1;
          if (inning.out[i] & OUT_BOWLER_CREDIT) w.wickets += 1;
        }
      }
      for (const [idx, b] of bat) {
        const p = bump(this.players, this.playerKey(match, idx), newPlayer);
        p.runs += b.runs;
        p.balls += b.balls;
        p.outs += b.outs;
        p.innings += 1;
        p.rRuns = p.rRuns * RECENT_DECAY + b.runs;
        p.rBalls = p.rBalls * RECENT_DECAY + b.balls;
        p.rOuts = p.rOuts * RECENT_DECAY + b.outs;
        g.batRuns += b.runs;
        g.batBalls += b.balls;
        g.outs += b.outs;
      }
      for (const [idx, w] of bowl) {
        const p = bump(this.players, this.playerKey(match, idx), newPlayer);
        p.bowlRuns += w.runs;
        p.bowlBalls += w.balls;
        p.wickets += w.wickets;
        g.bowlRuns += w.runs;
        g.bowlBalls += w.balls;
        g.bowlWickets += w.wickets;
      }
    }
  }

  // ── what ships ─────────────────────────────────────────────────────────────

  /** Elo, form and win rate per `team|gender|format`, as they stand after the last match. */
  exportTeams() {
    const out = {};
    for (const [key, t] of this.teams) {
      out[key] = {
        winRate: round(shrunkWinRate(t.wins, t.games), 4),
        matchesPlayed: t.games,
        elo: round(t.elo, 1),
        form: round(t.form, 3),
      };
    }
    return out;
  }

  /** The format means every published rating and ground is expressed against. */
  exportMeans() {
    const out = {};
    for (const [slice, g] of this.globals) {
      const m = globalMeans(g);
      out[slice] = Object.fromEntries(Object.entries(m).map(([k, v]) => [k, round(v, 5)]));
    }
    return out;
  }

  /** `pairKey → [decided, wins of the side that sorts first, wins of the other]`. */
  exportH2h() {
    const out = {};
    for (const [key, pair] of this.h2h) {
      const [first, rest] = key.split("|||");
      const second = rest.split("|")[0];
      out[key] = [pair.decided, pair.wins.get(first) ?? 0, pair.wins.get(second) ?? 0];
    }
    return out;
  }

  /**
   * Grounds by name: `name → [{ city, slices: { "gender|format": counts }, visits: { "team|gender": n } }]`.
   * A name maps to a list because one name can be several grounds; the reader picks by city.
   */
  exportVenues() {
    const byKey = new Map();
    const at = (venueKey) => bump(byKey, venueKey, () => ({ slices: {}, visits: {} }));
    for (const [key, v] of this.venues) {
      const cut = key.indexOf("|");
      at(key.slice(0, cut)).slices[key.slice(cut + 1)] = [
        v.matches, round(v.firstRatioSum, 3), v.firstRatioN, v.chases, v.chaseWins, v.draws,
      ];
    }
    for (const [key, n] of this.venueVisits) {
      const cut = key.indexOf("|");
      at(key.slice(0, cut)).visits[key.slice(cut + 1)] = n;
    }
    const out = {};
    for (const [venueKey, entry] of byKey) {
      const [name, city] = venueKey.split("@");
      (out[name] ??= []).push({ city, ...entry });
    }
    return out;
  }

  /**
   * Everyone who has played since [since], with their names, sides and ratings per format:
   * `[{ id, names, teams: ["team|gender"], ratings: { format: [avg, strike, recent, bowl, batBalls, bowlBalls, runs, outs, innings, wickets] } }]`.
   *
   * The first six are what the model reads. The last four are the plain record behind them —
   * runs, dismissals, innings batted and wickets in the archive's matches — so that a line of
   * commentary can state a figure rather than a ratio.
   *
   * Ratings are taken against the means of the player's own gender, read off the sides they have
   * played for — a registry id is one person, so one gender.
   */
  exportPlayers(since) {
    const formats = new Map();
    for (const key of this.players.keys()) {
      const cut = key.lastIndexOf("|");
      bump(formats, key.slice(0, cut), () => []).push(key.slice(cut + 1));
    }
    const out = [];
    for (const [id, person] of this.people) {
      const teams = [...person.teams].filter(([, date]) => date >= since).map(([team]) => team);
      if (teams.length === 0) continue;
      const gender = teams[0].slice(teams[0].lastIndexOf("|") + 1);
      const ratings = {};
      for (const format of formats.get(id) ?? []) {
        const p = this.players.get(`${id}|${format}`);
        const r = playerRating(p, globalMeans(this.globals.get(`${gender}|${format}`)));
        ratings[format] = [
          round(r.avg, 3), round(r.strike, 3), round(r.recent, 3), round(r.bowl, 3), r.batBalls, r.bowlBalls,
          p.runs, p.outs, p.innings, p.wickets,
        ];
      }
      out.push({ id, names: [...person.names], teams, ratings });
    }
    return out;
  }
}

export { playerRating, xiStrength };

/**
 * [ctx] as the model reads it from [side]'s point of view.
 *
 * `xiOf` lets the caller substitute an eleven — the trainer uses it to withhold players whose
 * names would not have joined, so the model is fitted on elevens as incomplete as the live ones.
 */
export function sideContextOf(ctx, match, side, xiOf = (name) => ctx.xi[name]) {
  const other = match.teams.find((t) => t !== side) ?? "";
  const of = (name) => ({ ...ctx.teams[name], visits: ctx.venue.visits[name], xi: xiOf(name) });
  return sideContext({
    own: of(side),
    opp: of(other),
    h2hOwn: side === match.teams[0] ? ctx.h2h.firstAdvantage : -ctx.h2h.firstAdvantage,
    venue: ctx.venue,
  });
}
