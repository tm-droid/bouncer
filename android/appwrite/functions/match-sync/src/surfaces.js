/**
 * The two pieces of prose about a match as a whole: a preview before it starts and a summary
 * once it is over.
 *
 * Both are written from a list of facts built here in code — the head-to-head record, what sides
 * usually make at the ground, the forecast, the scores — and both are held to it. A model writes
 * the sentences; `validSurface` then checks every sentence, and anything that fails is replaced
 * by `*Template`, the same facts set down plainly with no model in the loop. So a surface is
 * either checked prose or plain facts, never unchecked prose and never missing.
 *
 * Nothing here predicts. The pre-match chance is the win-probability model's own number, stated
 * as that; the forecast is stated as a forecast.
 */

import { groundedIn } from "./situation.js";

export const SURFACE_SYSTEM_INSTRUCTION =
  "You are a cricket writer. Using ONLY the Facts, write two or three plain sentences for a reader about to follow this match.\n" +
  "Every name and every number you use must appear in the Facts, exactly as given. Add no player, venue, record, milestone, opinion on who will win, or anything else the Facts do not state.\n" +
  "A forecast is a forecast: never say what the weather is. No headings, no lists, no quotation marks. Output only the sentences.";

const FORMAT_WORDS = { T20: "T20", ODI: "one-day", HUNDRED: "Hundred", TEST: "Test" };
/** Fewer recorded meetings than this is an anecdote, not a record. */
const MIN_MEETINGS = 3;
const FORECAST_HOURS = 8;

const decisionWords = (decision) => (/bat/i.test(String(decision)) ? "bat" : /bowl|field/i.test(String(decision)) ? "bowl" : null);

/** The pieces of a preview, each present only when the context can back it. */
function previewPieces(context, chance, now) {
  const [a, b] = context?.teams ?? [];
  if (!a?.name || !b?.name) return null;
  const format = FORMAT_WORDS[context.format] ?? null;
  const pieces = { a: a.name, b: b.name, format, venue: context.venue?.name ?? null };

  const h = context.h2h;
  if (h && h.games >= MIN_MEETINGS && format) pieces.meetings = { games: h.games, aWins: h.firstWins, bWins: h.secondWins };

  const v = context.venue;
  if (v?.typicalFirstInnings && format) pieces.ground = { typical: v.typicalFirstInnings, matches: v.matches };

  const toss = context.toss;
  const choice = decisionWords(toss?.decision);
  if (toss?.winnerName && choice) pieces.toss = { winner: toss.winnerName, choice };

  const hours = (context.forecast?.hours ?? []).filter((x) => x.t >= now - 3_600_000).slice(0, FORECAST_HOURS);
  const rain = hours.map((x) => x.rainChance).filter((x) => typeof x === "number");
  if (hours.length && rain.length && typeof hours[0].tempC === "number") {
    pieces.forecast = { hours: hours.length, rain: Math.round(Math.max(...rain)), temp: Math.round(hours[0].tempC) };
  }

  if (typeof chance === "number" && context.format !== "TEST") {
    const pa = Math.round(chance * 100);
    pieces.chance = { a: pa, b: 100 - pa };
  }
  return pieces;
}

/**
 * The facts a preview may use, or null when there is too little to say. A preview needs at least
 * two things beyond who is playing where, or it is the fixture list restated.
 */
export function previewFacts(context, { chance = null, now = Date.now() } = {}) {
  const p = previewPieces(context, chance, now);
  if (!p) return null;
  const substance = ["meetings", "ground", "forecast", "chance"].filter((k) => p[k]).length;
  if (substance < 2) return null;

  const facts = [`Match: ${p.a} against ${p.b}${p.format ? `, ${p.format}` : ""}${p.venue ? `, at ${p.venue}` : ""}.`];
  if (p.meetings) {
    facts.push(`Recorded ${p.format} meetings between them: ${p.meetings.games}. ${p.a} won ${p.meetings.aWins}; ${p.b} won ${p.meetings.bWins}.`);
  }
  if (p.ground) facts.push(`Usual first-innings score in the ${p.ground.matches} recorded ${p.format} matches at ${p.venue}: ${p.ground.typical}.`);
  if (p.toss) facts.push(`Toss: ${p.toss.winner} won it and chose to ${p.toss.choice} first.`);
  if (p.forecast) facts.push(`Forecast for the next ${p.forecast.hours} hours: up to a ${p.forecast.rain} per cent chance of rain, about ${p.forecast.temp} degrees.`);
  if (p.chance) facts.push(`Win-probability model before the start: ${p.a} ${p.chance.a} per cent, ${p.b} ${p.chance.b} per cent.`);
  return { facts: facts.join("\n"), mustMention: [p.a, p.b], pieces: p };
}

/** The same facts as plain sentences, for when no written version passes. */
export function previewTemplate({ pieces: p }) {
  const out = [`${p.a} play ${p.b}${p.venue ? ` at ${p.venue}` : ""}.`];
  if (p.meetings) out.push(`In ${p.meetings.games} recorded ${p.format} meetings, ${p.a} have won ${p.meetings.aWins} and ${p.b} ${p.meetings.bWins}.`);
  if (p.ground) out.push(`Sides batting first here usually make about ${p.ground.typical} (${p.ground.matches} recorded ${p.format} matches).`);
  if (p.toss) out.push(`${p.toss.winner} won the toss and chose to ${p.toss.choice} first.`);
  if (p.forecast) out.push(`The forecast for the next ${p.forecast.hours} hours gives up to a ${p.forecast.rain} per cent chance of rain, at about ${p.forecast.temp} degrees.`);
  if (p.chance) out.push(`The win-probability model has ${p.a} at ${p.chance.a} per cent and ${p.b} at ${p.chance.b} per cent before the start.`);
  return out.join(" ");
}

const num = (v) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};

/** The pieces of a summary, from a finished match's scorecard. */
function summaryPieces(detail, context) {
  const innings = detail?.scorecard?.innings ?? [];
  if (!detail?.status || innings.length === 0) return null;
  const list = innings.map((inn) => {
    const batters = (inn.batsmen ?? []).map((b) => ({ name: b.name, runs: num(b.runs), balls: num(b.balls) })).filter((b) => b.name && b.runs !== null);
    const bowlers = (inn.bowlers ?? []).map((b) => ({ name: b.name, wickets: num(b.wickets), runs: num(b.runs) })).filter((b) => b.name && b.wickets !== null && b.runs !== null);
    const top = batters.sort((x, y) => y.runs - x.runs)[0] ?? null;
    const best = bowlers.sort((x, y) => y.wickets - x.wickets || x.runs - y.runs)[0] ?? null;
    return { team: inn.teamName, score: inn.score, top, best: best && best.wickets > 0 ? best : null };
  }).filter((i) => i.team && i.score);
  if (list.length === 0) return null;
  const v = context?.venue;
  const format = FORMAT_WORDS[context?.format] ?? null;
  const ground = v?.typicalFirstInnings && format ? { typical: v.typicalFirstInnings, matches: v.matches, venue: v.name, format } : null;
  return { result: String(detail.status).replace(/\s+/g, " ").trim().replace(/[.!]+$/, ""), innings: list.slice(0, 4), ground };
}

export function summaryFacts(detail, context = null) {
  const p = summaryPieces(detail, context);
  if (!p) return null;
  const facts = [`Result: ${p.result}.`];
  for (const i of p.innings) {
    facts.push(`${i.team} innings: ${i.score}.`);
    if (i.top) facts.push(`Top scorer in that innings: ${i.top.name}, ${i.top.runs}${i.top.balls !== null ? ` off ${i.top.balls} balls` : ""}.`);
    if (i.best) facts.push(`Best bowling against them: ${i.best.name}, ${i.best.wickets} for ${i.best.runs}.`);
  }
  if (p.ground) facts.push(`Usual first-innings score in the ${p.ground.matches} recorded ${p.ground.format} matches at ${p.ground.venue}: ${p.ground.typical}.`);
  return { facts: facts.join("\n"), mustMention: [], pieces: p };
}

export function summaryTemplate({ pieces: p }) {
  const out = [`${p.result}.`];
  for (const i of p.innings) {
    let line = `${i.team} made ${i.score}`;
    if (i.top) line += `, ${i.top.name} top-scoring with ${i.top.runs}${i.top.balls !== null ? ` off ${i.top.balls} balls` : ""}`;
    if (i.best) line += `; ${i.best.name} took ${i.best.wickets} for ${i.best.runs}`;
    out.push(`${line}.`);
  }
  if (p.ground) out.push(`Sides batting first at ${p.ground.venue} usually make about ${p.ground.typical}.`);
  return out.join(" ");
}

/** Sentences, split where a full stop ends one — not inside "4.5" or after an initial. */
const sentencesOf = (text) => text.split(/(?<=[.!?])\s+(?=\p{Lu})/u).map((s) => s.trim()).filter(Boolean);

export const SURFACE_MAX_CHARS = 520;

/**
 * [raw] as it may be stored, or null. Every sentence must be grounded in [facts] on its own, and
 * every name in [mustMention] must appear somewhere.
 */
export function validSurface(raw, facts, mustMention = []) {
  const text = String(raw ?? "").replace(/["“”]/g, "").replace(/\s+/g, " ").trim();
  if (text.length < 60 || text.length > SURFACE_MAX_CHARS) return null;
  const sentences = sentencesOf(text);
  if (sentences.length < 2 || sentences.length > 4) return null;
  if (!sentences.every((s) => groundedIn(s, facts))) return null;
  if (!mustMention.every((name) => text.includes(name))) return null;
  // A forecast must be called one. "It is raining" from a forecast is the claim this module exists
  // to make impossible.
  if (/\b(rain|degrees|humid)/i.test(text) && !/forecast/i.test(text)) return null;
  return text;
}

/**
 * Write one surface: a model's prose if it passes the code check and the fact-checker, the plain
 * template otherwise. Never throws and never returns nothing.
 *
 * @param built    `previewFacts(...)` or `summaryFacts(...)`
 * @param template `previewTemplate` or `summaryTemplate`
 * @param write    `(facts) => Promise<{ text, provider } | null>`
 * @param check    `(facts, text) => Promise<boolean>` — true only for an explicit pass
 */
export async function writeSurface({ built, template, write, check }) {
  const plain = { text: template(built), provider: "template" };
  try {
    const written = await write(built.facts);
    const text = written ? validSurface(written.text, built.facts, built.mustMention) : null;
    if (!text) return plain;
    return (await check(built.facts, text)) ? { text, provider: written.provider } : plain;
  } catch {
    return plain;
  }
}
