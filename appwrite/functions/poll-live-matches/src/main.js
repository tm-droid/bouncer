import { Client, Storage } from "node-appwrite";
import { InputFile } from "node-appwrite/file";
import { createSign, createHash } from "crypto";
import { seriesCategory, isWomensSeries } from "./series.js";
import { parseJsonFile } from "./storageJson.js";
import { CricbuzzBlockedError, fetchHtml } from "./cricbuzz-http.js";

const VERCEL_BASE = process.env.VERCEL_API_BASE ?? "https://api-iota-jade.vercel.app";
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const FILE_IDS = { live: "live-matches", recent: "recent-matches", upcoming: "upcoming-matches" };

// Tracks the last-written content hash per type so an unchanged list is not rewritten every
// minute. One small download replaces three large uploads.
const STATE_FILE_ID = "poll-state";

// A skipped write freezes the file's `fetchedAt`, and devices reject a file older than their own
// staleness bound — so every type must still be refreshed periodically even when unchanged.
//
// Defaults are deliberately conservative at 120s, which is inside the 150s bound that ALREADY
// SHIPPED clients apply to every type. Android updates roll out over days, so raising the
// upcoming/recent floor before that release has propagated would make old installs reject those
// files and fall through to the Vercel API — trading an Appwrite bill for a Vercel one.
//
// Once the release carrying CacheConfig.matchStorageMaxAgeMs (live 180s / others 900s) is out,
// raise the static floor to 600000 via the function's env vars. No redeploy needed.
const REFRESH_FLOOR_LIVE_MS = Number(process.env.REFRESH_FLOOR_LIVE_MS) || 120_000;
const REFRESH_FLOOR_STATIC_MS = Number(process.env.REFRESH_FLOOR_STATIC_MS) || 120_000;
const REFRESH_FLOOR_MS = {
  live: REFRESH_FLOOR_LIVE_MS,
  recent: REFRESH_FLOOR_STATIC_MS,
  upcoming: REFRESH_FLOOR_STATIC_MS,
};

const hashOf = (data) => createHash("sha256").update(JSON.stringify(data)).digest("hex");

// Cricbuzz's own schedule page, which carries a `matchInfo` block per fixture including
// `matchFormat`. One page answers every upcoming fixture at once.
const CRICBUZZ_SCHEDULE_URL = "https://www.cricbuzz.com/cricket-match/live-scores/upcoming-matches";
const SERIES_LIST_URL = "https://www.cricbuzz.com/cricket-schedule/series";
// Archive pages, walked a few at a time so the corpus covers seasons that are over. `active-series`
// alone meant "World Cup" matched nothing whenever no World Cup was in progress.
const SERIES_ARCHIVE_URL = (year) => `https://www.cricbuzz.com/cricket-scorecard-archives/${year}`;
// Bound the remembered formats so the state file cannot grow without limit.
const MAX_REMEMBERED_FORMATS = 400;

export default async ({ req, res, log, error }) => {
  if (!process.env.APPWRITE_API_KEY) {
    return res.json({ error: "Missing APPWRITE_API_KEY" }, 500);
  }
  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY);
  const storage = new Storage(client);

  // Previous content hashes. Missing/corrupt state just means every type is rewritten this run.
  let prevState = {};
  try {
    prevState = parseJsonFile(await storage.getFileDownload(BUCKET_ID, STATE_FILE_ID)) ?? {};
  } catch { /* first run */ }

  // Fetch all three types in parallel to minimize total write-time spread
  const types = ["live", "recent", "upcoming"];
  const now = Date.now();
  const formatCache = { ...(prevState.formats ?? {}) };
  const settled = await Promise.allSettled(types.map(async (type) => {
    const resp = await fetch(`${VERCEL_BASE}/matches?type=${type}`, { signal: AbortSignal.timeout(10_000) });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const data = await resp.json();

    // Upcoming fixtures carry their real format, so match-sync does not have to guess it.
    //
    // The upstream list is {id, title, status} and nothing else, and most titles name no format —
    // "Scotland vs Canada, 124th Match" is an ODI, "Durham vs Middlesex, Group B" is a 50-over
    // One-Day Cup game. match-sync used to probe T20/ODI/HUNDRED/TEST and take the first slice both
    // teams appeared in, which answers "where do these teams have a record", not "what is this
    // match". On 2026-08-07 that scored 9 of 13 fixtures on T20 form for ODI matches, and on
    // Scotland vs Canada it reversed the favourite.
    if (type === "upcoming") await attachFormats(data, formatCache, log, error);

    // Hash the DATA only. The stored payload embeds `fetchedAt`, so hashing it would differ on
    // every run and this comparison would never skip anything. Hashed AFTER enrichment, so a
    // newly-resolved format is a change and gets written rather than sitting in a skipped file.
    const hash = hashOf(data);
    const prev = prevState[type];
    const ageMs = prev?.writtenAt ? now - prev.writtenAt : Infinity;
    const unchanged = prev?.hash === hash;
    if (unchanged && ageMs < REFRESH_FLOOR_MS[type]) {
      log(`Skipped ${type} — unchanged, ${Math.round(ageMs / 1000)}s old`);
      return { type, data, hash, writtenAt: prev.writtenAt, skipped: true };
    }

    const payload = JSON.stringify({ data, fetchedAt: now });
    const buffer = Buffer.from(payload, "utf8");
    await storage.deleteFile(BUCKET_ID, FILE_IDS[type]).catch(() => {});
    await storage.createFile(BUCKET_ID, FILE_IDS[type], InputFile.fromBuffer(buffer, `${type}-matches.json`), ["read(\"any\")"]);
    log(unchanged ? `Refreshed ${type} (floor elapsed)` : `Updated ${type}`);
    return { type, data, hash, writtenAt: now, skipped: false };
  }));

  const results = {};
  const nextState = { ...prevState, formats: formatCache };
  for (let i = 0; i < types.length; i++) {
    const s = settled[i];
    if (s.status === "fulfilled") {
      results[types[i]] = s.value.skipped ? "skipped" : "ok";
      nextState[types[i]] = { hash: s.value.hash, writtenAt: s.value.writtenAt };
    } else {
      error(`Failed ${types[i]}: ${s.reason?.message}`);
      results[types[i]] = "error";
      // Leave this type's previous state untouched so a transient failure does not make the next
      // run believe the file is current.
    }
  }

  if (JSON.stringify(nextState) !== JSON.stringify(prevState)) {
    try {
      await storage.deleteFile(BUCKET_ID, STATE_FILE_ID).catch(() => {});
      await storage.createFile(
        BUCKET_ID, STATE_FILE_ID,
        InputFile.fromBuffer(Buffer.from(JSON.stringify(nextState), "utf8"), `${STATE_FILE_ID}.json`),
        ["read(\"any\")"]
      );
    } catch (e) {
      // Losing the state file only costs a redundant rewrite next run — never fail the whole run.
      error(`poll-state write failed: ${e?.message}`);
    }
  }

  // Check if live match count changed and send FCM topic message if so
  const liveResult = settled[0]; // "live" is index 0
  if (liveResult.status === "fulfilled") {
    try {
      // Use in-memory data directly — no self-read needed
      const liveData = liveResult.value?.data;
      const newLiveCount = liveData?.matches?.length ?? liveData?.count ?? 0;

      // Stored as JSON because the bucket's allowedFileExtensions is ["json"]. It was previously
      // written as `live-count.txt`, which Appwrite rejected; the throw was swallowed by the catch
      // below, so prevCount was ALWAYS 0 and this branch fired an FCM topic push every single
      // minute that any match was live.
      let prevCount = 0;
      try {
        const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, "live-count"));
        // Tolerate a bare number in case a legacy body ever landed.
        prevCount = (typeof parsed === "number" ? parsed : parsed?.count) || 0;
      } catch { /* first run, or legacy non-JSON body */ }

      if (newLiveCount !== prevCount) {
        log(`Live match count changed: ${prevCount} → ${newLiveCount}`);

        // Send FCM BEFORE writing live-count so a timeout can't update count without notifying
        const saJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
        const projectId = process.env.FIREBASE_PROJECT_ID;
        if (saJson && projectId) {
          await sendFcmTopicMessage(
            "cricket-live-updates",
            { type: newLiveCount > prevCount ? "MATCH_STARTED" : "MATCH_ENDED", liveCount: String(newLiveCount) },
            saJson, projectId, error
          );
        }

        await storage.deleteFile(BUCKET_ID, "live-count").catch(() => {});
        await storage.createFile(BUCKET_ID, "live-count",
          InputFile.fromBuffer(Buffer.from(JSON.stringify({ count: newLiveCount }), "utf8"), "live-count.json"),
          ["read(\"any\")"]
        );
      }
    } catch (e) {
      error(`FCM state-change check failed: ${e?.message}`);
    }
  }

  // Series sync: scrape Cricbuzz at most once per 24h server-side (replaces N per-device daily scrapes)
  try {
    let prevJson = null;
    try {
      prevJson = parseJsonFile(await storage.getFileDownload(BUCKET_ID, "active-series"));
    } catch { /* missing or parse error — treat as stale */ }
    const seriesFresh = (Date.now() - (prevJson?.fetchedAt ?? 0)) < 24 * 3600 * 1000;

    if (!seriesFresh) {
      const active = await fetchActiveSeries();
      const merged = await mergeArchiveSeries(active, prevJson, log, error);
      if (merged.series.length > 0) {
        const payload = JSON.stringify({
          data: merged.series,
          fetchedAt: Date.now(),
          archiveCursor: merged.cursor,
        });
        await storage.deleteFile(BUCKET_ID, "active-series").catch(() => {});
        await storage.createFile(
          BUCKET_ID, "active-series",
          InputFile.fromBuffer(Buffer.from(payload, "utf8"), "active-series.json"),
          ["read(\"any\")"]
        );
        log(`Updated active-series (${merged.series.length} series, archive cursor ${merged.cursor})`);
      }
    }
  } catch (e) {
    error(`Series sync failed: ${e?.message}`);
  }

  return res.json(results);
};

/**
 * Stamp each upcoming fixture with Cricbuzz's `matchFormat`, scraping only when something is new.
 *
 * A match's format never changes, so a fixture only needs looking up once ever — [cache] carries
 * across runs in the state file and most runs therefore make no request at all. A failed or blocked
 * scrape is not an error: fixtures already in the cache still get stamped, and anything unresolved
 * simply arrives without the field, which is exactly the input match-sync's existing probe already
 * handles. Never throws — this must not be able to fail the match list it is decorating.
 */
async function attachFormats(data, cache, log, error) {
  const matches = data?.matches;
  if (!Array.isArray(matches) || matches.length === 0) return;

  const missing = matches.filter((m) => m?.id && !cache[String(m.id)]);
  if (missing.length > 0) {
    try {
      // Through the shared throttle layer, not a bare fetch: this shares one request budget and
      // one 403 backoff window with every other Cricbuzz call in the container.
      const found = parseFormats(await fetchHtml(CRICBUZZ_SCHEDULE_URL, 10_000));
      let added = 0;
      for (const m of missing) {
        const format = found[String(m.id)];
        if (format) { cache[String(m.id)] = format; added++; }
      }
      log(`Formats: ${added} of ${missing.length} new fixtures resolved (${Object.keys(found).length} on page)`);
    } catch (e) {
      // Degrades to the downstream probe, which is what shipped before this existed.
      error(`Format lookup failed (${e?.message}) — ${missing.length} fixtures keep their unknown format`);
    }
  }

  for (const m of matches) {
    const format = cache[String(m?.id)];
    if (format) m.matchFormat = format;
  }

  // Keep only ids still on the list, oldest dropped first if it somehow still overflows.
  const live = new Set(matches.map((m) => String(m?.id)));
  for (const id of Object.keys(cache)) if (!live.has(id)) delete cache[id];
  const keys = Object.keys(cache);
  if (keys.length > MAX_REMEMBERED_FORMATS) {
    for (const id of keys.slice(0, keys.length - MAX_REMEMBERED_FORMATS)) delete cache[id];
  }
}

/**
 * `{ matchId: matchFormat }` from a Cricbuzz schedule page.
 *
 * The page embeds its data as a Next.js flight payload with escaped quotes, so the JSON is
 * unescaped before matching. Pairing is anchored inside one `matchInfo` object — `[^{}]` cannot
 * cross an object boundary — so a fixture that omits `matchFormat` cannot pick up its neighbour's.
 */
export function parseFormats(html) {
  const unescaped = String(html ?? "").replace(/\\"/g, '"');
  const out = {};
  const re = /"matchId":(\d+)[^{}]{0,4000}?"matchFormat":"([A-Z_0-9]+)"/g;
  let m;
  while ((m = re.exec(unescaped)) !== null) {
    if (!out[m[1]]) out[m[1]] = m[2];
  }
  return out;
}

async function fetchActiveSeries() {
  const html = await fetchHtml(SERIES_LIST_URL, 10_000);

  const nextDataMatch = html.match(/<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/);
  if (nextDataMatch) {
    try {
      const pageProps = JSON.parse(nextDataMatch[1])?.props?.pageProps;
      const seriesData = pageProps?.seriesMapProto ?? pageProps?.content;
      if (seriesData) {
        const result = [];
        for (const group of seriesData) {
          for (const s of (group.series ?? [])) {
            const id = String(s.id ?? s.seriesId ?? "");
            const name = s.name ?? s.seriesName ?? "";
            if (!id || !name || name.length < 3) continue;
            result.push({ id, name, category: seriesCategory(name), isWomens: isWomensSeries(name), startDate: s.startDt ?? "", endDate: s.endDt ?? "" });
          }
        }
        if (result.length > 0) return result;
      }
    } catch {}
  }

  // Regex fallback if __NEXT_DATA__ missing or parse failed
  const result = [];
  const seen = new Set();
  const re = /href="\/cricket-series\/(\d+)\/[^"]*">([^<]+)<\/a>/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    const id = m[1], name = m[2].trim();
    if (!name || name.length < 3 || seen.has(id)) continue;
    seen.add(id);
    result.push({ id, name, category: seriesCategory(name), isWomens: isWomensSeries(name), startDate: "", endDate: "" });
  }
  return result;
}

/**
 * Number of archive years walked per run. One page is one Cricbuzz request; the corpus fills in
 * over successive runs rather than in one burst.
 */
const ARCHIVE_PAGES_PER_RUN = 2;
/** How far back the archive walk goes before wrapping to the newest year again. */
const ARCHIVE_OLDEST_YEAR = 2015;

/**
 * Widen the active-series list with archived seasons, a couple of pages at a time.
 *
 * `active-series` only ever held series that were in progress, so a query for a competition out of
 * season — "World Cup" for most of any four-year cycle — matched nothing anywhere in the app. The
 * archive pages carry the same `/cricket-series/{id}/{slug}` anchors, so the same parser works.
 *
 * Three bounds, because spacing alone does not make an unbounded walk safe:
 *  - [ARCHIVE_PAGES_PER_RUN] pages per run, resumed from `archiveCursor` in the previous file, so
 *    the crawl is spread across runs instead of hammering the host once;
 *  - a block aborts the walk and keeps everything gathered so far, including the previous file's
 *    entries — a truncated corpus must never overwrite a good one;
 *  - one Sentry-visible `error()` per throttled run rather than one per page.
 *
 * Never throws: this decorates the active list and must not be able to fail it.
 *
 * [fetchPage] is injectable so the bounds above are testable without a network — the abort path is
 * the one that most needs proving and the one hardest to trigger on demand against the real host.
 */
export async function mergeArchiveSeries(active, prevJson, log, error, fetchPage = fetchHtml) {
  const byId = new Map();
  // Previous entries first so a re-crawl cannot shrink the corpus, then this run's live list wins
  // on conflict — an in-progress series has fresher dates than an archived copy of itself.
  for (const s of (prevJson?.data ?? [])) if (s?.id) byId.set(String(s.id), s);
  for (const s of active) if (s?.id) byId.set(String(s.id), s);

  const startYear = Number(prevJson?.archiveCursor) || new Date().getFullYear();
  let year = startYear;
  let blocked = null;
  let walked = 0;

  for (let i = 0; i < ARCHIVE_PAGES_PER_RUN; i++) {
    try {
      const html = await fetchPage(SERIES_ARCHIVE_URL(year), 12_000);
      for (const s of parseSeriesAnchors(html)) {
        if (!byId.has(s.id)) byId.set(s.id, s);
      }
      walked++;
    } catch (e) {
      // A block ends the walk for this run. The cursor is not advanced past the page we failed on,
      // so the next run retries it rather than leaving a hole in the corpus.
      if (e instanceof CricbuzzBlockedError || e?.blocked) { blocked = e; break; }
      error(`Series archive ${year} failed: ${e?.message}`);
    }
    year = year - 1 < ARCHIVE_OLDEST_YEAR ? new Date().getFullYear() : year - 1;
  }

  if (blocked) {
    error(
      `Series archive walk throttled after ${walked} page(s) at ${year}: ${blocked.message}` +
      (blocked.bodySnippet ? ` — body: ${blocked.bodySnippet}` : "")
    );
  }
  log(`Series archive: walked ${walked} page(s), corpus ${byId.size}`);
  return { series: [...byId.values()], cursor: year };
}

/**
 * The `/cricket-series/{id}/{slug}` anchors an archive or schedule page carries.
 *
 * Two shapes, both live on cricbuzz.com right now:
 *  - schedule pages put the name straight in the anchor text;
 *  - archive pages wrap a `<div>` and put the name in `title`, suffixed with
 *    " Matches 17 Dec 2023 - 31 Dec 2023".
 * Reading only the first shape parsed 10 of 219 series on an archive page, which would have looked
 * like a working crawl.
 */
export function parseSeriesAnchors(html) {
  const out = [];
  const seen = new Set();
  const re = /href="\/cricket-series\/(\d+)\/[^"]*"([^>]*)>\s*(?:<[^>]*>\s*)*([^<]{0,120})/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    const id = m[1];
    if (seen.has(id)) continue;
    const name = nameFromAnchor(m[2], m[3]);
    if (!name || name.length < 3) continue;
    seen.add(id);
    out.push({ id, name, category: seriesCategory(name), isWomens: isWomensSeries(name), startDate: "", endDate: "" });
  }
  return out;
}

/** Prefer the `title` attribute — it is the complete name; the inline text is often truncated. */
function nameFromAnchor(attrs, innerText) {
  const title = /title="([^"]+)"/.exec(attrs)?.[1];
  if (title) {
    // Drop the " Matches <start> - <end>" tail the archive appends to every title.
    const stripped = title.replace(/\s+Matches\s+\d{1,2}\s+\w{3}\s+\d{4}\s*-.*$/, "").trim();
    if (stripped.length >= 3) return decodeEntities(stripped);
  }
  return decodeEntities(innerText.trim());
}

function decodeEntities(text) {
  return text
    .replace(/&#x27;|&#39;|&apos;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/&#x26;|&#38;|&amp;/g, "&")
    .replace(/&nbsp;/g, " ")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

async function getFirebaseAccessToken(serviceAccountJson) {
  const sa = JSON.parse(serviceAccountJson);
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })).toString("base64url");
  const sigInput = `${header}.${payload}`;
  const sign = createSign("RSA-SHA256");
  sign.update(sigInput);
  const sig = sign.sign(sa.private_key, "base64url");
  const jwt = `${sigInput}.${sig}`;

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
    signal: AbortSignal.timeout(8_000),
  });
  if (!tokenResp.ok) throw new Error(`Token exchange HTTP ${tokenResp.status}`);
  const tokenData = await tokenResp.json();
  return tokenData.access_token;
}

async function sendFcmTopicMessage(topic, data, serviceAccountJson, projectId, errorFn) {
  try {
    const accessToken = await getFirebaseAccessToken(serviceAccountJson);
    const resp = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": `Bearer ${accessToken}` },
        body: JSON.stringify({ message: { topic, data } }),
        signal: AbortSignal.timeout(8_000),
      }
    );
    if (!resp.ok) errorFn?.(`FCM v1 send failed: HTTP ${resp.status}`);
  } catch (e) {
    errorFn?.(`FCM v1 send error: ${e?.message}`);
  }
}
