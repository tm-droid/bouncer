import assert from "node:assert/strict";
import { test } from "node:test";
import {
  countryFromSlug,
  editDistance,
  INDEX_TTL_MS,
  indexPlan,
  mergePlayers,
  normalizeForSearch,
  parsePlayerLinks,
  parseTeamLinks,
  rankPlayers,
} from "../src/playerIndex.js";

// Markup shapes captured from cricbuzz.com on 2026-08-21. The point of fixtures rather than a live
// fetch is that a test which silently depends on the network turns an upstream outage into a
// red build and, worse, an upstream *change* into a green one that ships a broken parser.

const TEAM_LIST_HTML = `
<div><a href="/cricket-team/india/2" class="x">India</a></div>
<div><a href="/cricket-team/west-indies/4">West Indies</a></div>
<div><a href="/cricket-team/india/2">India again</a></div>
<div><a href="/cricket-series/123/something">not a team</a></div>
`;

// The real page nests the name several divs deep and styles it `text-ellipsis`; `title` is the
// only place the untruncated name reliably appears.
const SQUAD_HTML = `
<a href="/profiles/11808/shubman-gill" title="Shubman Gill"><div class="flex"><div class="text-ellipsis">Shubman Gi...</div></div></a>
<a href="/profiles/1413/virat-kohli" title="Virat Kohli"><div><div class="text-ellipsis">Virat Kohli</div></div></a>
<a href="/profiles/1413/virat-kohli" title="Virat Kohli"><div>duplicate</div></a>
<a href="/profiles/265/ms-dhoni" title="MS Dhoni"><div>x</div></a>
<a href="/profiles/999/no-title-attr"><div>skipped</div></a>
`;

test("parseTeamLinks deduplicates by team id and ignores non-team links", () => {
  const teams = parseTeamLinks(TEAM_LIST_HTML);
  assert.deepEqual(teams, [
    { slug: "india", id: "2" },
    { slug: "west-indies", id: "4" },
  ]);
});

test("parsePlayerLinks reads the untruncated name from title, not the ellipsised text", () => {
  const players = parsePlayerLinks(SQUAD_HTML, "India");
  assert.deepEqual(players.map((p) => p.name), ["Shubman Gill", "Virat Kohli", "MS Dhoni"]);
  assert.equal(players[0].name.includes("..."), false);
});

test("parsePlayerLinks deduplicates by profile id and skips anchors with no title", () => {
  const players = parsePlayerLinks(SQUAD_HTML, "India");
  assert.equal(players.length, 3);
  assert.equal(players.some((p) => p.id === "999"), false);
});

test("parsePlayerLinks decodes HTML entities in names", () => {
  const players = parsePlayerLinks(`<a href="/profiles/1/x" title="Shafi&#x27;ullah Ghafari">`, "Afghanistan");
  assert.equal(players[0].name, "Shafi'ullah Ghafari");
});

test("parsePlayerLinks captures the real Cricbuzz id, which the profile URL depends on", () => {
  // /profiles/1413/virat-kohli is the URL PlayerProfileScraper.buildProfileUrl constructs and the
  // one that actually serves 200. A wrong id here is a dead player screen, not a failed search.
  const players = parsePlayerLinks(SQUAD_HTML, "India");
  assert.equal(players.find((p) => p.name === "Virat Kohli").id, "1413");
});

test("countryFromSlug title-cases multi-word slugs", () => {
  assert.equal(countryFromSlug("west-indies"), "West Indies");
  assert.equal(countryFromSlug("india"), "India");
});

test("rankPlayers puts exact match above prefix above word-prefix above substring", () => {
  const players = [
    { id: "1", name: "Kohli Substring Xyz", country: "" },
    { id: "2", name: "Virat Kohli", country: "India" },
    { id: "3", name: "Viratkumar Something", country: "" },
    { id: "4", name: "Virat", country: "" },
  ];
  assert.deepEqual(rankPlayers(players, "Virat", 4).map((p) => p.name), [
    "Virat",              // exact
    "Virat Kohli",        // prefix, shorter
    "Viratkumar Something", // prefix, longer
    "Kohli Substring Xyz", // no match on "virat" at all -> excluded
  ].slice(0, 3));
});

test("rankPlayers finds a player by surname alone via the word-prefix tier", () => {
  const players = [{ id: "1413", name: "Virat Kohli", country: "India" }];
  assert.deepEqual(rankPlayers(players, "kohli", 5).map((p) => p.id), ["1413"]);
});

test("rankPlayers returns nothing for a blank query rather than everything", () => {
  assert.deepEqual(rankPlayers([{ id: "1", name: "Virat Kohli" }], "   "), []);
});

test("rankPlayers honours the limit", () => {
  const players = Array.from({ length: 30 }, (_, i) => ({ id: String(i), name: `Test Player ${i}` }));
  assert.equal(rankPlayers(players, "test", 10).length, 10);
});

test("normalizeForSearch agrees with the shared fixture the app also pins", async () => {
  const { readFile } = await import("node:fs/promises");
  const fixture = JSON.parse(
    await readFile(new URL("./fixtures/search-normalization.json", import.meta.url), "utf8"),
  );
  for (const [input, expected] of fixture.cases) {
    assert.equal(normalizeForSearch(input), expected, `normalizeForSearch(${JSON.stringify(input)})`);
  }
});

test("rankPlayers matches a name typed surname-first", () => {
  const players = [
    { id: "1413", name: "Virat Kohli", country: "India" },
    { id: "2", name: "Virat Singh", country: "India" },
  ];
  assert.deepEqual(rankPlayers(players, "kohli virat", 5).map((p) => p.id), ["1413"]);
});

test("rankPlayers tolerates a transposed surname", () => {
  const players = [
    { id: "1413", name: "Virat Kohli", country: "India" },
    { id: "9", name: "Kieron Pollard", country: "West Indies" },
  ];
  assert.deepEqual(rankPlayers(players, "virat kolhi", 5).map((p) => p.id), ["1413"]);
  assert.deepEqual(rankPlayers(players, "kolhi", 5).map((p) => p.id), ["1413"]);
});

test("rankPlayers ranks every exact tier above any typo match", () => {
  const players = [
    { id: "1", name: "Kohler Test", country: "" },   // "kohli" is 2 edits from "kohler"
    { id: "2", name: "Kohli Mark", country: "" },    // exact word prefix
  ];
  assert.deepEqual(rankPlayers(players, "kohli", 5).map((p) => p.id), ["2", "1"]);
});

test("rankPlayers folds accents on both sides", () => {
  const players = [{ id: "5", name: "José Buttler", country: "England" }];
  assert.deepEqual(rankPlayers(players, "jose", 5).map((p) => p.id), ["5"]);
  assert.deepEqual(rankPlayers([{ id: "6", name: "Jose Smith" }], "José", 5).map((p) => p.id), ["6"]);
});

test("rankPlayers skips the typo scan when the exact tiers already fill the limit", () => {
  const players = [
    ...Array.from({ length: 10 }, (_, i) => ({ id: `p${i}`, name: `Kohli ${i}` })),
    { id: "typo", name: "Kolhi Zed" },
  ];
  assert.ok(!rankPlayers(players, "kohli", 10).some((p) => p.id === "typo"));
});

test("editDistance counts an adjacent swap as one edit", () => {
  assert.equal(editDistance("kolhi", "kohli"), 1);
  assert.equal(editDistance("kitten", "sitting"), 3);
  assert.equal(editDistance("", "abc"), 3);
});

// A search must never wait for a rebuild. The Android SDK's OkHttp client has no timeout set, so
// its read timeout is the 10 s default, and a build was measured at 15.19 s — the search that
// found the index expired timed out on the device, drew "unavailable", and reported nothing,
// because a SocketTimeoutException is filtered as connectivity. Rebuilding is the schedule's job.
const NOW = Date.parse("2026-10-06T00:00:00Z");
const indexAged = (ms) => ({ players: [{ id: "1413", name: "Virat Kohli" }], fetchedAt: NOW - ms });

test("indexPlan serves a fresh index on demand", () => {
  assert.equal(indexPlan({ index: indexAged(60_000), scheduled: false }), "serve");
});

test("indexPlan serves an index past its TTL on demand rather than rebuilding inline", () => {
  assert.equal(indexPlan({ index: indexAged(INDEX_TTL_MS + 1), scheduled: false }), "serve");
  assert.equal(indexPlan({ index: indexAged(30 * INDEX_TTL_MS), scheduled: false }), "serve");
});

test("indexPlan builds inline only when there is no index to answer from", () => {
  assert.equal(indexPlan({ index: null, scheduled: false }), "build-then-serve");
  // A file that parsed to something without a player list is not an index.
  assert.equal(indexPlan({ index: { fetchedAt: NOW }, scheduled: false }), "build-then-serve");
});

test("indexPlan rebuilds on every scheduled run, whatever is cached", () => {
  assert.equal(indexPlan({ index: indexAged(60_000), scheduled: true }), "rebuild");
  assert.equal(indexPlan({ index: null, scheduled: true }), "rebuild");
});

// Merge, never shrink — the rule `tools/update_logos.py` follows for teams. A rebuild used to
// replace the index with whatever that one run gathered, so a squad page that failed took its
// whole team out of search until the next run, and a player dropped from a squad became
// unfindable although his profile page still exists.
const KOHLI = { id: "1413", name: "Virat Kohli", country: "India", imageUrl: "" };
const GILL = { id: "11808", name: "Shubman Gill", country: "India", imageUrl: "" };
const ROOT = { id: "8019", name: "Joe Root", country: "England", imageUrl: "" };

test("mergePlayers keeps a player the new build did not see", () => {
  assert.deepEqual(mergePlayers([KOHLI, ROOT], [KOHLI]), [KOHLI, ROOT]);
});

test("mergePlayers adds a player the previous index did not have", () => {
  assert.deepEqual(mergePlayers([KOHLI], [KOHLI, GILL]), [KOHLI, GILL]);
});

test("mergePlayers takes the new build's fields for a player both hold", () => {
  const moved = { ...ROOT, country: "Yorkshire" };
  assert.deepEqual(mergePlayers([KOHLI, ROOT], [moved]), [KOHLI, moved]);
});

test("mergePlayers has nothing to merge onto on a first build", () => {
  assert.deepEqual(mergePlayers(null, [KOHLI]), [KOHLI]);
  assert.deepEqual(mergePlayers(undefined, [KOHLI, GILL]), [KOHLI, GILL]);
});
