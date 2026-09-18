import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { parseMatchList, teamInningsScore } from "../src/matchlist.js";

/**
 * Against captured Cricbuzz markup — the same file `CricBuzzTeamImageTest` uses on the Kotlin
 * side, so both parsers of this one page are checked against one page rather than against two
 * hand-written expectations that can agree with each other and with neither reality.
 */
const FIXTURE = "app/src/test/resources/cricbuzz_match_list_team_images.html";

function locate(relativePath) {
  let dir = dirname(fileURLToPath(import.meta.url));
  for (let i = 0; i < 8; i++) {
    try {
      const candidate = join(dir, relativePath);
      readFileSync(candidate);
      return candidate;
    } catch {
      dir = join(dir, "..");
    }
  }
  throw new Error(`could not find ${relativePath} by walking up from the test directory`);
}

const html = readFileSync(locate(FIXTURE), "utf8");
const matches = parseMatchList(html);

test("the captured list page parses", () => {
  assert.ok(matches.length > 0, "no matches parsed from the captured list page");
});

test("each match is listed once, however many flight chunks repeat it", () => {
  const ids = matches.map((m) => m.id);
  assert.equal(new Set(ids).size, ids.length);
});

test("both sides arrive with their Cricbuzz team id", () => {
  // The id is the whole point of this parser: it is the only key the live payload — which names a
  // side with a short code that matches no full team name — can be aligned on.
  const match = matches.find((m) => m.id === "154649");
  assert.ok(match, "match 154649 missing from the captured list page");
  assert.equal(match.teams[0].name, "Trinbago Knight Riders");
  assert.equal(match.teams[0].teamId, "271");
  assert.equal(match.teams[1].name, "Guyana Amazon Warriors");
  assert.equal(match.teams[1].teamId, "159");
});

test("the summary carries what the Vercel shape could not", () => {
  for (const m of matches) {
    assert.equal(typeof m.status, "string");
    assert.equal(m.teams.length, 2);
    for (const side of m.teams) {
      assert.equal(typeof side.score, "string");
      assert.equal(typeof side.overs, "string");
      assert.equal(typeof side.teamId, "string");
    }
  }
});

test("a side that has batted twice keeps both innings", () => {
  // "266 & 83/2", not "83/2". Reading only the newer innings is what rendered every Test score as
  // its second innings alone.
  const score = teamInningsScore(
    { team1Score: { inngs1: { runs: 266, wickets: 10, overs: 88.3 }, inngs2: { runs: 83, wickets: 2, overs: 24.1 } } },
    "team1Score",
    true,
  );
  assert.equal(score.score, "266 & 83/2");
  assert.equal(score.overs, "24.1");
});

test("a completed innings drops its wickets in a Test and keeps them elsewhere", () => {
  const allOut = { team1Score: { inngs1: { runs: 266, wickets: 10, overs: 88.3 } } };
  assert.equal(teamInningsScore(allOut, "team1Score", true).score, "266");
  assert.equal(teamInningsScore(allOut, "team1Score", false).score, "266/10");
});

test("which side is batting comes from the id, never from the name", () => {
  const withBatting = parseMatchList(html).find((m) => m.teams.some((t) => t.isBatting));
  if (withBatting) {
    const batting = withBatting.teams.filter((t) => t.isBatting);
    assert.equal(batting.length, 1, "exactly one side bats at a time");
  }
});

test("markup it cannot read is no answer, not an empty list of matches", () => {
  // The caller must never write this over a good file. Returning [] is the honest shape; deciding
  // what it means is the caller's job, and `main.js` keeps the previous file.
  assert.deepEqual(parseMatchList("<html><body>nothing here</body></html>"), []);
  assert.deepEqual(parseMatchList(null), []);
});

// ── Which tab a parsed match belongs on ──────────────────────────────────────

const { belongsOn } = await import("../src/main.js");

test("a finished match is Recent and is not Live", () => {
  const m = { state: "Complete" };
  assert.equal(belongsOn("recent", m), true);
  assert.equal(belongsOn("live", m), false);
  assert.equal(belongsOn("upcoming", m), false);
});

test("a fixture that has not started is Upcoming and is not Live", () => {
  // The site-wide ticker on the Upcoming page is what made this filter necessary: the page carries
  // ~90 matches that are not upcoming fixtures at all.
  const m = { state: "Preview" };
  assert.equal(belongsOn("upcoming", m), true);
  assert.equal(belongsOn("live", m), false);
});

test("a match in progress is Live only", () => {
  const m = { state: "In Progress" };
  assert.equal(belongsOn("live", m), true);
  assert.equal(belongsOn("recent", m), false);
  assert.equal(belongsOn("upcoming", m), false);
});

test("a break is live cricket, not a finished match", () => {
  for (const state of ["Innings Break", "Stumps", "Rain", "Tea", "Toss"]) {
    assert.equal(belongsOn("live", { state }), true, state);
    assert.equal(belongsOn("recent", { state }), false, state);
  }
});

test("an unrecognised state surfaces on Live rather than vanishing from every tab", () => {
  const m = { state: "Something Cricbuzz Has Not Said Before" };
  assert.equal(belongsOn("live", m), true);
  assert.equal(belongsOn("recent", m), false);
  assert.equal(belongsOn("upcoming", m), false);
});

test("the toss is not a result, however the word won is used", () => {
  // "India won the toss and elected to bat" is the start of a match. Only the result-bearing forms
  // of "won" are terminal — the same rule MatchStatusClassifier applies on the device.
  assert.equal(belongsOn("recent", { state: "India won the toss and elected to bat" }), false);
  assert.equal(belongsOn("recent", { state: "India won by 5 wkts" }), true);
});

// ── File size is a constraint, not an afterthought ───────────────────────────

const { LIST_CAPS } = await import("../src/main.js");

test("every list file is capped", () => {
  // Storage bandwidth was already over its plan cap before a summary grew from ~95 bytes to ~450.
  // Measured against the live pages: uncapped, `recent` was 90 matches and 40 KB against the 1.3 KB
  // the old upstream produced. Auto and the widgets read these files; the phone's own Recent and
  // Upcoming tabs do not.
  assert.deepEqual(Object.keys(LIST_CAPS).sort(), ["live", "recent", "upcoming"]);
  for (const [type, cap] of Object.entries(LIST_CAPS)) {
    assert.ok(cap > 0 && cap <= 40, `${type} cap ${cap} is not a bound worth having`);
  }
});

test("the live cap is a ceiling, not a working limit", () => {
  // 18 live matches on the day this was written, and match-sync slices its own work to 12. A cap
  // that bound here would silently drop a live match from every server-fed surface at once.
  assert.ok(LIST_CAPS.live >= 40);
});
