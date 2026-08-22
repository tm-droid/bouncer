// Name → Cricbuzz profile id, built from Cricbuzz's own team squad pages.
//
// WHY AN INDEX AND NOT A SEARCH CALL
// Cricbuzz has no working player search any more. Every candidate 404s or is a stub:
//   /api/search/results?q=…&type=player  → 404   (and its JSON shape was Cricinfo's, never Cricbuzz's)
//   /search?q=…                          → 404
//   /api/html/search?q=…                 → 200 application/json, zero bytes, for every parameter shape
// Profile pages themselves are fine (/profiles/1413/virat-kohli → 200), which is the whole problem:
// the app can render a player it cannot look up.
//
// Cricinfo's search does work, but it answers with Cricinfo ids (/cricketers/virat-kohli-253802).
// `PlayerProfileScraper.buildProfileUrl` builds `/profiles/{id}/{slug}` against Cricbuzz, so a
// Cricinfo id resolves to nothing — swapping the id namespace would trade a dead search for a dead
// profile screen. Squad pages keep us in the Cricbuzz namespace.
//
// `/cricket-team` lists ~38 teams; each `/cricket-team/{slug}/{id}/players` carries ~37 anchors of
// the form `<a href="/profiles/11808/shubman-gill" title="Shubman Gill">`. That is the corpus.

import { CRICBUZZ_BASE, fetchHtml } from "./cricbuzz-http.js";

export const INDEX_FILE_ID = "player-index";

/** Rebuild when the cached index is older than this. Squads change slowly. */
export const INDEX_TTL_MS = 7 * 24 * 3600 * 1000;

/**
 * Team pages walked per build. All ~38 fit inside the function timeout at the shared 350 ms
 * spacing (~13 s of spacing plus the fetches), and a partial index is worse than a slow one:
 * a missing team reads to the user as "that player does not exist".
 */
export const MAX_TEAM_PAGES = 40;

const TEAM_LIST_URL = `${CRICBUZZ_BASE}/cricket-team`;

/** `/cricket-team/{slug}/{id}` — the teams whose squads we walk. */
export function parseTeamLinks(html) {
  const out = [];
  const seen = new Set();
  const re = /href="\/cricket-team\/([a-z0-9-]+)\/(\d+)"/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    if (seen.has(m[2])) continue;
    seen.add(m[2]);
    out.push({ slug: m[1], id: m[2] });
  }
  return out;
}

/**
 * `/profiles/{id}/{slug}` anchors on a squad page.
 *
 * The name comes from `title`, not the anchor text: the text sits several nested divs deep and is
 * styled with `text-ellipsis`, so reading it risks a visually truncated name becoming the stored one.
 */
export function parsePlayerLinks(html, country) {
  const out = [];
  const seen = new Set();
  const re = /href="\/profiles\/(\d+)\/[^"]*"([^>]*)>/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    const id = m[1];
    if (seen.has(id)) continue;
    const title = /title="([^"]+)"/.exec(m[2])?.[1];
    if (!title) continue;
    const name = decodeEntities(title.trim());
    if (name.length < 2) continue;
    seen.add(id);
    out.push({ id, name, country });
  }
  return out;
}

function decodeEntities(text) {
  return text
    .replace(/&#x27;|&#39;|&apos;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/&#x26;|&#38;|&amp;/g, "&")
    .replace(/&nbsp;/g, " ");
}

/** Title-case a URL slug: "west-indies" → "West Indies". */
export function countryFromSlug(slug) {
  return slug.split("-").map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
}

/**
 * Walk the team list and every squad page. Returns whatever it gathered.
 *
 * A page that fails is skipped rather than aborting the build — one unavailable team should cost
 * that team, not the whole index. A *block* is different and does abort: continuing would just
 * collect more 403s and deepen the backoff. The caller keeps the previous index in that case.
 */
export async function buildIndex(log) {
  const teams = parseTeamLinks(await fetchHtml(TEAM_LIST_URL, 12_000)).slice(0, MAX_TEAM_PAGES);
  const byId = new Map();
  let failures = 0;

  for (const team of teams) {
    try {
      const html = await fetchHtml(`${CRICBUZZ_BASE}/cricket-team/${team.slug}/${team.id}/players`, 12_000);
      for (const p of parsePlayerLinks(html, countryFromSlug(team.slug))) {
        if (!byId.has(p.id)) byId.set(p.id, p);
      }
    } catch (e) {
      if (e?.blocked) throw e;
      failures++;
      log?.(`player-index: team ${team.slug} failed: ${e?.message}`);
    }
  }

  log?.(`player-index: ${teams.length} teams, ${failures} failed, ${byId.size} players`);
  return { players: [...byId.values()], teamsWalked: teams.length, failures };
}

/**
 * Rank index entries against a query.
 *
 * Tiers mirror `SearchRepository.fuzzyMatchScore` on the device so the two agree on what "best
 * match" means — exact, then prefix, then any-word prefix, then substring. `scrapeProfile` takes
 * `first()`, so the ordering here decides which profile a player screen loads.
 */
export function rankPlayers(players, query, limit = 10) {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const scored = [];
  for (const p of players) {
    const name = p.name.toLowerCase();
    let score = 0;
    if (name === q) score = 4;
    else if (name.startsWith(q)) score = 3;
    else if (name.split(/[\s-]+/).some((w) => w.startsWith(q))) score = 2;
    else if (name.includes(q)) score = 1;
    if (score > 0) scored.push({ p, score, len: name.length });
  }
  // Shorter names first within a tier: "Virat Kohli" should beat "Virat Kohli Jr" for "virat".
  scored.sort((a, b) => b.score - a.score || a.len - b.len || a.p.name.localeCompare(b.p.name));
  return scored.slice(0, limit).map(({ p }) => ({
    id: p.id,
    name: p.name,
    country: p.country ?? "",
    imageUrl: "",
  }));
}
