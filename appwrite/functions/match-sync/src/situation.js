// Match-situation lines: one sentence about the state of the game, written only when something
// happened — a side takes the match back, a win is all but secured, a batter hits a run of
// boundaries, a bowler strikes twice in an over, a collapse.
//
// These exist because deliveries whose source is the scorer's header alone are now written plainly
// in code (`plainLine` in main.js), and a feed of plain lines needs a voice somewhere. The voice
// goes here, where there are facts to speak from: every figure a line may use is one match-sync
// already holds as a number, and the line is checked against those figures in code before it is
// stored. There is deliberately no fixed interval — a quiet match gets no lines.

import { classifyEvent } from "./llm.js";
import { parseBallOutcome } from "./cricbuzz.js";

export const SIX_STREAK = 2;
export const BOUNDARY_STREAK = 3;
export const BOWLER_STRIKES = 2;
export const COLLAPSE_WICKETS = 3;
export const COLLAPSE_BALLS = 18;
/** Win-probability movement, over at most [SWING_WINDOW_MS], that counts as the match turning. */
export const SWING = 0.15;
export const SWING_WINDOW_MS = 15 * 60 * 1000;
export const SECURED = 0.9;
/** Legal deliveries between two lines for one match, so one passage of play gets one line. */
export const COOLDOWN_BALLS = 12;
/** A delivery older than this is a backlog republish, not something happening now. */
export const MAX_TRIGGER_AGE_MS = 10 * 60 * 1000;
const RECENT_MAX = 36;

const HEADER_RE = /^(.+?) to ([^,]+),/;
const REBOWLED = new Set(["WIDE", "NOBALL"]);

/** A delivery as the triggers read it, or null when the source has no scorer's header. */
export function deliveryOf(clean, { ball, innings, ts }) {
  const m = String(clean ?? "").match(HEADER_RE);
  if (!m) return null;
  const over = /^\d+\.\d$/.test(String(ball)) ? Number(String(ball).split(".")[0]) : null;
  return {
    bowler: m[1].trim(),
    batter: m[2].trim(),
    event: classifyEvent(clean),
    legal: !REBOWLED.has(parseBallOutcome(clean).kind),
    runOut: /\brun out\b/i.test(clean),
    ball: String(ball),
    over,
    innings,
    ts,
  };
}

export function newSituationState() {
  return { recent: [], probs: [], secured: false, ballsSinceLine: COOLDOWN_BALLS, pending: null };
}

const isBoundary = (d) => d.event === "FOUR" || d.event === "SIX";

/** How many of [batter]'s most recent legal balls, newest first, satisfy [test] without a break. */
function runOf(recent, d, test) {
  let n = 0;
  for (let i = recent.length - 1; i >= 0; i--) {
    const r = recent[i];
    if (r.innings !== d.innings) break;
    if (r.batter !== d.batter || !r.legal) continue;
    if (!test(r)) break;
    n++;
  }
  return n;
}

function deliveryTrigger(recent, d) {
  if (d.event === "WICKET") {
    const sameOver = recent.filter((r) => r.innings === d.innings && r.over === d.over && r.over !== null);
    const strikes = sameOver.filter((r) => r.event === "WICKET" && r.bowler === d.bowler && !r.runOut).length;
    if (!d.runOut && strikes === BOWLER_STRIKES) return { type: "strike", player: d.bowler, wickets: strikes };
    const window = recent.filter((r) => r.innings === d.innings && r.legal).slice(-COLLAPSE_BALLS);
    const fallen = window.filter((r) => r.event === "WICKET").length;
    if (fallen === COLLAPSE_WICKETS) return { type: "collapse", wickets: fallen, balls: window.length };
    return null;
  }
  if (d.event === "SIX" && runOf(recent, d, (r) => r.event === "SIX") === SIX_STREAK) {
    return { type: "streak", player: d.batter, sixes: SIX_STREAK };
  }
  if (isBoundary(d) && runOf(recent, d, isBoundary) === BOUNDARY_STREAK) {
    return { type: "streak", player: d.batter, boundaries: BOUNDARY_STREAK };
  }
  return null;
}

/**
 * Feed this run's new deliveries (oldest first) and the current win probability into [state] and
 * return the one trigger worth a line, or null. Mutates [state].
 *
 * `prob` is team1's win probability, or null when there is none (no detail tier, or a Test, whose
 * third outcome makes "one side gained" the wrong reading of a move).
 */
export function detectTrigger(state, deliveries, { prob = null, team1 = null, team2 = null, now = Date.now() } = {}) {
  let found = null;
  for (const d of deliveries) {
    state.recent.push(d);
    if (state.recent.length > RECENT_MAX) state.recent.shift();
    if (d.legal) state.ballsSinceLine++;
    if (now - d.ts > MAX_TRIGGER_AGE_MS) continue;
    const t = deliveryTrigger(state.recent, d);
    if (t) found = { ...t, innings: d.innings, over: d.over, ts: d.ts };
  }
  const last = deliveries.at(-1) ?? state.recent.at(-1);
  const where = last ? { innings: last.innings, over: last.over, ts: last.ts } : null;

  let probTrigger = null;
  if (prob !== null && team1 && team2 && where) {
    const seeded = state.probs.length > 0;
    state.probs = state.probs.filter((p) => now - p.ts <= SWING_WINDOW_MS);
    const leader = prob >= SECURED ? team1 : prob <= 1 - SECURED ? team2 : null;
    if (leader && !state.secured) {
      state.secured = true;
      // A match first seen already decided has not just been secured.
      if (seeded) probTrigger = { type: "secured", team: leader, ...where };
    } else if (state.probs.length) {
      const moved = prob - state.probs[0].prob;
      if (Math.abs(moved) >= SWING) {
        probTrigger = { type: "turn", team: moved > 0 ? team1 : team2, ...where };
        state.probs = [];
      }
    }
    if (!leader) state.secured = false;
    state.probs.push({ ts: now, prob });
  }

  // A secured match outranks everything and ignores the cooldown; the rest wait their turn.
  if (probTrigger?.type === "secured") return probTrigger;
  const trigger = found ?? probTrigger;
  if (!trigger || state.ballsSinceLine < COOLDOWN_BALLS) return null;
  return trigger;
}

const EVENT_TEXT = {
  streak: (t) => (t.sixes
    ? `${t.player} has hit sixes off the last ${t.sixes} balls faced`
    : `${t.player} has hit boundaries off the last ${t.boundaries} balls faced`),
  strike: (t) => `${t.player} has taken ${t.wickets} wickets in this over`,
  collapse: (t) => `${t.wickets} wickets have fallen in the last ${t.balls} balls`,
  turn: (t) => `${t.team} have swung the match their way`,
  secured: (t) => `${t.team} are now all but certain to win`,
};

/** The facts a line may use, as the text the writer reads and the checker checks against. */
export function situationFacts(trigger, detail) {
  const s = detail?.situation ?? {};
  const facts = [`Event: ${EVENT_TEXT[trigger.type](trigger)}.`];
  if (s.battingTeamName && detail?.score) facts.push(`Batting: ${s.battingTeamName} ${detail.score}.`);
  if (s.bowlingTeamName) facts.push(`Bowling: ${s.bowlingTeamName}.`);
  if (s.target != null && s.runsNeeded != null) facts.push(`Target ${s.target}; ${s.runsNeeded} more needed.`);
  if (trigger.player) {
    const batter = (detail?.batsmen ?? []).find((b) => b?.name === trigger.player);
    if (batter?.runs != null && batter?.balls != null) facts.push(`${trigger.player}: ${batter.runs} runs off ${batter.balls} balls.`);
    const bowler = detail?.bowler?.name === trigger.player ? detail.bowler : null;
    if (bowler?.wickets != null && bowler?.runs != null) facts.push(`${trigger.player}: ${bowler.wickets} wickets for ${bowler.runs} runs.`);
  }
  return facts.join("\n");
}

export const SITUATION_SYSTEM_INSTRUCTION =
  "You are a cricket broadcaster. In ONE sentence of at most 30 words, react to the Event for viewers, in the present tense.\n" +
  "Use only the names and numbers given. Add no other player, team, number, venue, milestone or prediction.\n" +
  "Do not begin with a ball number. Output only the sentence.";

const NUMBER_WORDS = {
  one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10, eleven: 11, twelve: 12,
};
// Claims a figure cannot back: none of these is ever in the facts, so none may be in the line.
const UNBACKED_RE = /\b(fift(?:y|ies)|half[- ]centur(?:y|ies)|centur(?:y|ies)|hundreds?|maidens?|hat[- ]tricks?|records?|debut|career|fastest|highest|first ever)\b/i;
// Capitalised words that are not names. A word outside this list and outside the facts fails the
// line, so the list errs short: a rejected line costs nothing, a stored invention does.
const NOT_NAMES = new Set(
  ("The A An And But What That This These Those It Its With After From Back Now Suddenly Here There Two Three Four " +
    "Six Just Another Game Match Momentum Pressure Advantage Wickets Wicket Boundaries Boundary Sixes Fours Runs Over " +
    "Overs Balls Chase Target Victory Win No Not Can Is Are Have Has They He She In On At For As").split(" ")
);

/**
 * The line as it may be stored, or null. Every number, every counted quantity and every name in it
 * must come from [facts]; [mustMention] is the player or team the trigger is about.
 */
export function validSituationLine(raw, facts, mustMention) {
  const line = String(raw ?? "").replace(/\s+/g, " ").trim();
  if (line.length < 20 || line.length > 240 || /^\d|^Ball\s+\d/i.test(line)) return null;
  if (UNBACKED_RE.test(line)) return null;
  const factNumbers = new Set(facts.match(/\d+(?:\.\d+)?/g) ?? []);
  for (const n of line.match(/\d+(?:\.\d+)?/g) ?? []) if (!factNumbers.has(n)) return null;
  for (const [word, value] of Object.entries(NUMBER_WORDS)) {
    // "four" and "six" are also things a batter hits; only a counted quantity is a number claim.
    const counted = new RegExp(`\\b${word}\\s+(?:wickets?|sixes|fours|boundaries|balls|runs|overs)\\b`, "i");
    if (counted.test(line) && !factNumbers.has(String(value))) return null;
  }
  const factWords = new Set(facts.split(/[^\p{L}'’-]+/u).filter(Boolean));
  for (const word of line.match(/\b\p{Lu}[\p{L}'’-]*/gu) ?? []) {
    const bare = word.replace(/['’]s$/, "");
    if (!factWords.has(bare) && !NOT_NAMES.has(bare)) return null;
  }
  const surname = String(mustMention ?? "").trim().split(/\s+/).pop();
  if (surname && !line.includes(surname)) return null;
  return line;
}

export const mustMentionOf = (trigger) => trigger.player ?? trigger.team ?? null;
