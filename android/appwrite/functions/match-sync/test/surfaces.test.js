import test from "node:test";
import assert from "node:assert/strict";

import {
  previewFacts, previewTemplate, summaryFacts, summaryTemplate, validSurface, writeSurface,
} from "../src/surfaces.js";

const NOW = 1_791_040_000_000;
const context = {
  format: "ODI",
  teams: [{ name: "India" }, { name: "West Indies" }],
  venue: { name: "Eden Gardens", known: true, matches: 20, typicalFirstInnings: 271 },
  h2h: { games: 59, firstWins: 40, secondWins: 18 },
  toss: { winnerName: "India", decision: "Batting" },
  forecast: { hours: Array.from({ length: 12 }, (_, i) => ({ t: NOW + i * 3_600_000, tempC: 29.4, rainChance: i === 3 ? 45 : 10 })) },
};

test("a preview's facts are the record, the ground, the toss, the forecast and the model's chance", () => {
  const built = previewFacts(context, { chance: 0.684, now: NOW });
  assert.equal(built.facts, [
    "Match: India against West Indies, one-day, at Eden Gardens.",
    "Recorded one-day meetings between them: 59. India won 40; West Indies won 18.",
    "Usual first-innings score in the 20 recorded one-day matches at Eden Gardens: 271.",
    "Toss: India won it and chose to bat first.",
    "Forecast for the next 8 hours: up to a 45 per cent chance of rain, about 29 degrees.",
    "Win-probability model before the start: India 68 per cent, West Indies 32 per cent.",
  ].join("\n"));
  assert.deepEqual(built.mustMention, ["India", "West Indies"]);
});

test("too little to say is no preview, and a Test quotes no two-way chance", () => {
  const bare = { format: "T20", teams: [{ name: "A" }, { name: "B" }], venue: { name: "Somewhere" } };
  assert.equal(previewFacts(bare, { chance: 0.5, now: NOW }), null, "a chance alone is the fixture list restated");
  assert.equal(previewFacts({ ...bare, teams: [{ name: "A" }] }, { now: NOW }), null);
  const test5 = previewFacts({ ...context, format: "TEST" }, { chance: 0.6, now: NOW });
  assert.equal(/Win-probability/.test(test5.facts), false);
  const thin = previewFacts({ ...context, h2h: { games: 2, firstWins: 2, secondWins: 0 } }, { chance: 0.6, now: NOW });
  assert.equal(/meetings/.test(thin.facts), false, "two meetings is not a record");
});

test("the template says exactly what the facts say", () => {
  const built = previewFacts(context, { chance: 0.684, now: NOW });
  const text = previewTemplate(built);
  assert.match(text, /^India play West Indies at Eden Gardens\. In 59 recorded one-day meetings, India have won 40 and West Indies 18\./);
  assert.match(text, /forecast for the next 8 hours gives up to a 45 per cent chance of rain/);
  for (const n of text.match(/\d+/g)) assert.ok(built.facts.includes(n), `${n} is not in the facts`);
});

test("written prose is kept only when every sentence is grounded and both sides are named", () => {
  const built = previewFacts(context, { chance: 0.684, now: NOW });
  const good = "India have won 40 of their 59 recorded one-day meetings with West Indies. Sides batting first at Eden Gardens usually make 271, and the forecast gives up to a 45 per cent chance of rain.";
  assert.equal(validSurface(good, built.facts, built.mustMention), good);

  const wrongNumber = good.replace("40", "41");
  assert.equal(validSurface(wrongNumber, built.facts, built.mustMention), null);
  const invented = "India have won 40 of 59 meetings with West Indies. Virat Kohli will be the key man at Eden Gardens tonight.";
  assert.equal(validSurface(invented, built.facts, built.mustMention), null, "a name the facts do not carry");
  const oneSide = "India have won 40 of their 59 recorded meetings. Sides batting first at Eden Gardens usually make 271 in these games.";
  assert.equal(validSurface(oneSide, built.facts, built.mustMention), null, "the other side is never named");
  const weather = "India meet West Indies at Eden Gardens with a 45 per cent chance of rain. It is 29 degrees and India chose to bat.";
  assert.equal(validSurface(weather, built.facts, built.mustMention), null, "weather stated without calling it a forecast");
  assert.equal(validSurface("India play West Indies.", built.facts, built.mustMention), null, "one short sentence is not a preview");
});

const finished = {
  status: "India won by 4 wkts",
  scorecard: { innings: [
    { teamName: "West Indies", score: "352/7", batsmen: [{ name: "Shai Hope", runs: "120", balls: "110" }, { name: "Keacy Carty", runs: "64", balls: "70" }], bowlers: [{ name: "Kuldeep Yadav", wickets: "3", runs: "55" }, { name: "Prasidh Krishna", wickets: "3", runs: "71" }] },
    { teamName: "India", score: "353/6", batsmen: [{ name: "Virat Kohli", runs: "135", balls: "120" }], bowlers: [{ name: "Alzarri Joseph", wickets: "0", runs: "60" }] },
  ] },
};

test("a summary's facts are the result, each innings, its top scorer and its best bowler", () => {
  const built = summaryFacts(finished, context);
  assert.equal(built.facts, [
    "Result: India won by 4 wkts.",
    "West Indies innings: 352/7.",
    "Top scorer in that innings: Shai Hope, 120 off 110 balls.",
    "Best bowling against them: Kuldeep Yadav, 3 for 55.",
    "India innings: 353/6.",
    "Top scorer in that innings: Virat Kohli, 135 off 120 balls.",
    "Usual first-innings score in the 20 recorded one-day matches at Eden Gardens: 271.",
  ].join("\n"));
  assert.equal(/Alzarri/.test(built.facts), false, "nobody took a wicket, so nobody has best bowling");
  assert.match(summaryTemplate(built), /^India won by 4 wkts\. West Indies made 352\/7, Shai Hope top-scoring with 120 off 110 balls; Kuldeep Yadav took 3 for 55\./);
  assert.equal(summaryFacts({ status: "India won", scorecard: { innings: [] } }, context), null);
  assert.equal(summaryFacts({ scorecard: finished.scorecard }, context), null, "no result line, no summary");
});

test("a surface is checked prose or the plain template, never unchecked prose", async () => {
  const built = previewFacts(context, { chance: 0.684, now: NOW });
  const good = "India have won 40 of their 59 recorded one-day meetings with West Indies. Sides batting first at Eden Gardens usually make 271, and the forecast gives up to a 45 per cent chance of rain.";
  const run = (write, check) => writeSurface({ built, template: previewTemplate, write, check });

  assert.deepEqual(await run(async () => ({ text: good, provider: "gemini-flash-lite" }), async () => true), { text: good, provider: "gemini-flash-lite" });
  const plain = previewTemplate(built);
  assert.deepEqual(await run(async () => ({ text: good, provider: "x" }), async () => false), { text: plain, provider: "template" }, "the checker said no");
  assert.deepEqual(await run(async () => ({ text: good.replace("40", "44"), provider: "x" }), async () => true), { text: plain, provider: "template" }, "the code check said no");
  assert.deepEqual(await run(async () => null, async () => true), { text: plain, provider: "template" }, "no writer answered");
  assert.deepEqual(await run(async () => { throw new Error("down"); }, async () => true), { text: plain, provider: "template" });
  assert.deepEqual(await run(async () => ({ text: good, provider: "x" }), async () => { throw new Error("down"); }), { text: plain, provider: "template" });
});

test("a preview is written once per match and stored on its context", async () => {
  const { ensurePreview, ensureSummary, __setSurfaceIo } = await import("../src/main.js");
  let writes = 0;
  const previous = __setSurfaceIo({ writer: async () => { writes++; return null; }, checker: async () => true });
  try {
    const stored = [];
    const contexts = { patch: async (id, fields) => { stored.push([id, Object.keys(fields)]); return { ...context, ...fields, rev: 2 }; } };
    const io = { log: () => {}, error: () => {} };
    const budget = { surfaces: 1 };
    const withPreview = await ensurePreview({ contexts, matchId: "151543", context, chance: 0.684, budget, ...io });
    assert.equal(withPreview.preview.provider, "template");
    assert.equal(budget.surfaces, 0);
    await ensurePreview({ contexts, matchId: "151543", context: withPreview, chance: 0.684, budget: { surfaces: 1 }, ...io });
    await ensurePreview({ contexts, matchId: "9", context, chance: 0.684, budget: { surfaces: 0 }, ...io });
    assert.equal(writes, 1, "an existing preview and an empty budget each cost nothing");

    const withSummary = await ensureSummary({ contexts, matchId: "151543", context: withPreview, detail: finished, ...io });
    assert.match(withSummary.summary.text, /^India won by 4 wkts\./);
    assert.deepEqual(stored, [["151543", ["preview"]], ["151543", ["summary"]]]);
  } finally {
    __setSurfaceIo(previous);
  }
});
