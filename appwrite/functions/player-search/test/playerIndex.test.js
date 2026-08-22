import assert from "node:assert/strict";
import { test } from "node:test";
import {
  countryFromSlug,
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
