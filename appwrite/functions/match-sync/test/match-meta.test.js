import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { metaPresence, parseMatchMeta } from "../src/cricbuzz.js";

// Captured live pages — the same markup the Kotlin commentary tests pin.
const fixture = (name) =>
  readFileSync(new URL(`../../../../android/app/src/test/resources/${name}`, import.meta.url), "utf8");

test("a one-day page yields the venue, the toss and the in-match state", () => {
  const meta = parseMatchMeta(fixture("cricbuzz_commentary_overball.html"));
  assert.deepEqual(meta.venue, {
    id: "399",
    name: "Roseworth Terrace",
    city: "Gosforth",
    country: "England",
    timezone: "+01:00",
    lat: 55.00398,
    lon: -1.617574,
  });
  assert.deepEqual(meta.toss, { winnerId: "141", winnerName: "Durham", decision: "Bowling" });
  assert.equal(meta.dayNight, false);
  assert.equal(meta.matchType, "domestic");
  assert.equal(meta.startMs, 1785319200000);
  assert.deepEqual(meta.teams, [
    { id: "141", name: "Durham", shortName: "DUR" },
    { id: "149", name: "Glamorgan", shortName: "GLAM" },
  ]);
  assert.deepEqual(meta.partnership, { runs: 86, balls: 52 });
  assert.deepEqual(meta.last5, { runs: 52, wickets: 0 });
  assert.deepEqual(meta.last10, { runs: 94, wickets: 1 });
  assert.equal(meta.lastWicket, "Kasey Aldridge run out (Hope-Bell/Alex Horton) 78(79) - 242/7 in 40.5 ov.");
  assert.equal(meta.strikerId, "1428724");
  assert.equal(meta.otherBowlerId, "13779");
  assert.equal(meta.revisedTarget, null);
});

test("The Hundred sends no recent-overs figures, and they stay unknown rather than zero", () => {
  const meta = parseMatchMeta(fixture("cricbuzz_commentary_hundred.html"));
  assert.equal(meta.last5, null);
  assert.equal(meta.last10, null);
  assert.deepEqual(meta.partnership, { runs: 51, balls: 30 });
  assert.equal(meta.venue.name, "Sophia Gardens");
  assert.equal(meta.teams[1].name, "Trent Rockets");
});

test("presence counts a venue only with coordinates and teams only as a pair", () => {
  const meta = parseMatchMeta(fixture("cricbuzz_commentary_hundred_innings_end.html"));
  const present = metaPresence(meta);
  assert.equal(present.venue, true);
  assert.equal(present.toss, true);
  assert.equal(present.teams, true);
  assert.equal(present.last5, false);

  assert.equal(metaPresence({ ...meta, venue: { ...meta.venue, lat: null } }).venue, false);
  assert.equal(metaPresence({ ...meta, teams: meta.teams.slice(0, 1) }).teams, false);
});

test("a page with no flight payload yields every field empty", () => {
  const meta = parseMatchMeta("<html><body>nothing</body></html>");
  assert.equal(meta.venue, null);
  assert.equal(meta.toss, null);
  assert.deepEqual(meta.teams, []);
  assert.equal(meta.partnership, null);
  assert.ok(Object.values(metaPresence(meta)).every((v) => v === false));
});

test("presence is tallied per match state, so a break's missing partnership is not a play-time rate", async () => {
  const { metaStateOf, metaTallyLines, tallyMeta } = await import("../src/main.js");
  assert.equal(metaStateOf("Innings Break"), "break");
  assert.equal(metaStateOf("Rain stops play"), "interrupted");
  assert.equal(metaStateOf("Durham need 12 runs in 9 balls"), "play");

  const meta = parseMatchMeta(fixture("cricbuzz_commentary_overball.html"));
  const tally = {};
  tallyMeta(tally, meta, "Durham need 12 runs");
  tallyMeta(tally, { ...meta, partnership: null }, "Durham need 12 runs");
  tallyMeta(tally, { ...meta, partnership: null }, "Innings Break");
  assert.equal(tally.play.matches, 2);
  assert.equal(tally.play.partnership, 1);
  assert.equal(tally.break.partnership, 0);
  assert.match(metaTallyLines(tally)[0], /^\[META\] play n=2: venue 2, toss 2, .*partnership 1,/);
});
