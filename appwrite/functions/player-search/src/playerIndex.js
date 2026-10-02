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

/**
 * How old an index may get before it counts as stale. Squads change slowly. The schedule rebuilds
 * well inside this; a search that finds an older one still answers from it (see `indexPlan`).
 */
export const INDEX_TTL_MS = 7 * 24 * 3600 * 1000;

/**
 * What one execution does about the index: `"serve"`, `"build-then-serve"` or `"rebuild"`.
 *
 * A search never rebuilds an index it can answer from, however old. The rebuild used to run
 * inline in whichever search found the index past its TTL, and a build is ~15 s (measured 15.19 s
 * on 2026-09-28) against the 10 s read timeout of the Android SDK's OkHttp client, which sets
 * none of its own. That search timed out on the device, which drew "Online player search is
 * unavailable" and reported nothing — a SocketTimeoutException is filtered as connectivity. So
 * the rebuild belongs to the schedule, and age deliberately plays no part in this decision.
 *
 * With no index at all (first run, or the file deleted) there is nothing to serve, so that one
 * search builds inline and the device may time out on it; the build still completes and the next
 * search is answered from the file.
 */
export function indexPlan({ index, scheduled }) {
  if (scheduled) return "rebuild";
  return Array.isArray(index?.players) ? "serve" : "build-then-serve";
}

/**
 * `built` laid over `previous` by player id: added and updated, never removed.
 *
 * The same rule `tools/update_logos.py` follows for teams. A rebuild used to replace the index
 * with whatever that one run gathered, so a squad page that failed — which `buildIndex` skips
 * rather than aborting on — took its whole team out of search until the next run, and a player
 * dropped from a squad became unfindable although `/profiles/{id}` still serves him. The walk
 * itself cannot be made incremental: Cricbuzz publishes no change feed, so finding what changed
 * means reading every squad page either way.
 */
export function mergePlayers(previous, built) {
  const byId = new Map();
  for (const p of previous ?? []) if (p?.id) byId.set(p.id, p);
  for (const p of built ?? []) if (p?.id) byId.set(p.id, p);
  return [...byId.values()];
}

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
 * Accent-folded, lowercased, punctuation-free, single-spaced.
 *
 * Mirrors `normalizeForSearch` in the app's `utils/SearchText.kt`. Both are pinned against
 * `test/fixtures/search-normalization.json` — the device re-scores these results with its own
 * scorer, so the two sides must agree on what a name is before they can agree on a ranking.
 */
export function normalizeForSearch(text) {
  return String(text ?? "")
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/[.'’`]/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim();
}

/** Optimal-string-alignment distance: Levenshtein plus adjacent transposition ("kolhi"). */
export function editDistance(a, b) {
  if (a === b) return 0;
  if (!a.length) return b.length;
  if (!b.length) return a.length;
  let prevPrev = new Array(b.length + 1).fill(0);
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j);
  let curr = new Array(b.length + 1).fill(0);
  for (let i = 1; i <= a.length; i++) {
    curr[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      let best = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
        best = Math.min(best, prevPrev[j - 2] + 1);
      }
      curr[j] = best;
    }
    [prevPrev, prev, curr] = [prev, curr, prevPrev];
  }
  return prev[b.length];
}

/** Permitted edits for a query word: roughly one per three characters, at least one. */
const editTolerance = (word) => Math.max(1, Math.ceil(word.length * 0.34));

/**
 * Every query word prefixes some name word ("kohli virat"), or — when [allowFuzzy] — is within its
 * edit tolerance of one ("virat kolhi"). Returns the share of query characters that needed no
 * correction (1 = all exact), or 0 when any word finds nothing.
 */
function tokenAgreement(tokens, words, allowFuzzy) {
  let weighted = 0;
  let total = 0;
  for (const token of tokens) {
    total += token.length;
    if (words.some((w) => w.startsWith(token))) {
      weighted += token.length;
      continue;
    }
    if (!allowFuzzy) return 0;
    const distance = Math.min(...words.map((w) => editDistance(token, w)));
    if (distance > editTolerance(token)) return 0;
    weighted += token.length * (1 - distance / token.length);
  }
  return weighted / total;
}

/**
 * Rank index entries against a query.
 *
 * Tiers mirror `SearchScore.of` on the device so the two agree on what "best match" means — exact,
 * prefix, any-word prefix, every word a prefix in any order, substring, then typo-tolerant. The
 * typo tier runs only when the exact tiers leave room under `limit`, so a query that already has
 * ten real answers never pays for a distance scan. `scrapeProfile` takes `first()` when it has no
 * id, so the ordering here decides which profile a name-only player screen loads.
 */
export function rankPlayers(players, query, limit = 10) {
  const q = normalizeForSearch(query);
  if (!q) return [];
  const tokens = q.split(" ");
  const scored = [];
  const unmatched = [];
  for (const p of players) {
    const name = normalizeForSearch(p.name);
    const words = name.split(" ");
    let score = 0;
    if (name === q) score = 4;
    else if (name.startsWith(q)) score = 3;
    else if (words.some((w) => w.startsWith(q))) score = 2;
    else if (tokens.length > 1 && tokenAgreement(tokens, words, false) === 1) score = 1.5;
    else if (name.includes(q)) score = 1;
    if (score > 0) scored.push({ p, score, len: name.length });
    else unmatched.push({ p, words, len: name.length });
  }
  if (scored.length < limit) {
    for (const { p, words, len } of unmatched) {
      const agreement = tokenAgreement(tokens, words, true);
      // Below 1: the exact tiers above already took every candidate that needed no correction.
      if (agreement > 0) scored.push({ p, score: agreement * 0.5, len });
    }
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
