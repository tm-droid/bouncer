import { InputFile } from "node-appwrite/file";
import { createHash } from "crypto";
import { seriesCategory, isWomensSeries } from "./series.js";
import { parseJsonFile } from "./storageJson.js";
import { CricbuzzBlockedError, fetchHtml } from "./cricbuzz-http.js";
import { parseMatchList } from "./matchlist.js";
import { classifyStatus } from "./matchstate.js";
import { fcmConfigured, matchTopic, sendTopicMessage } from "./fcm.js";

/**
 * Cricbuzz's own list pages, one per tab.
 *
 * This function used to read `${VERCEL_API_BASE}/matches?type=…` — a scraper hosted separately,
 * whose code is in another repository, and which answered `{id, title, status}` and nothing else.
 * It was the third independent parser of the match list (the device scrapes these same pages for
 * the phone UI, and this function already fetches Cricbuzz directly for the schedule, the series
 * list and the archives), and the thinnest of the three fed everything server-side: `match-sync`,
 * the widgets, Android Auto and `ScorePollingWorker`.
 *
 * Its output shape is what forced the rest. With no team objects the widget had to split the title
 * on " vs " to name the sides, Android Auto had no score to show at all, and nothing downstream
 * carried Cricbuzz's team ids — the only key the live payload can be aligned on.
 */
/**
 * How many matches each file may carry.
 *
 * Storage bandwidth is the binding constraint on this bucket — measured at 2.91 GB over 30 days
 * against a 2 GB cap — and every device downloads these files. Now that a summary carries both
 * sides, their scores and their ids it is ~450 bytes rather than ~95, so an uncapped list is the
 * difference between a 1.3 KB file and a 40 KB one: measured on the live Recent page, which listed
 * 90 finished matches against the 14 the old upstream returned.
 *
 * The caps are per file because the pages differ in kind. `live` is bounded by how much cricket is
 * being played (18 on the day this was measured; `match-sync` slices its own work to 12 anyway), so
 * 40 is a ceiling against a freak day rather than a working limit. `recent` and `upcoming` grow
 * with the calendar and have no natural bound, so theirs do bind — and the phone's own Recent and
 * Upcoming tabs are scraped on the device, not read from here, so the cap reaches Android Auto and
 * the widgets and nothing else.
 *
 * Page order is newest first, so a cap drops the oldest.
 */
export const LIST_CAPS = { live: 40, recent: 20, upcoming: 30 };

const LIST_URLS = {
  live: "https://www.cricbuzz.com/cricket-match/live-scores",
  recent: "https://www.cricbuzz.com/cricket-match/live-scores/recent-matches",
  upcoming: "https://www.cricbuzz.com/cricket-match/live-scores/upcoming-matches",
};
const BUCKET_ID = process.env.MATCH_CACHE_BUCKET_ID ?? "cricket-stats";
const FILE_IDS = { live: "live-matches", recent: "recent-matches", upcoming: "upcoming-matches" };

// Tracks the last-written content hash per type so an unchanged list is not rewritten every
// minute. One small download replaces three large uploads.
const STATE_FILE_ID = "poll-state";
let stateMemo = null; // warm-container copy of the current state — authoritative while warm
let persistedMemo = null; // what the poll-state FILE holds, as far as this container knows
let persistedAt = 0;

// The per-list `{hash, writtenAt}` entries change on almost every run (the live list changes with
// every score), and they only matter to the next cold start, which at worst rewrites one list it
// did not need to. Persisting them every minute was ~1,300 poll-state delete+create pairs a day.
const STATE_HASH_PERSIST_MS = 600_000;
const LIST_TYPES = ["live", "recent", "upcoming"];

/**
 * Whether poll-state must be written now.
 *
 * Everything except the per-list hashes is durable — the live count, `startedIds` (whose loss
 * re-announces match starts), remembered formats, the series cursor — and is written the run it
 * changes. The hashes alone are written at most every {@link STATE_HASH_PERSIST_MS}.
 */
export function shouldPersistState({ persisted, next, persistedAt: at, now }) {
  const withoutHashes = (st) => {
    const copy = { ...(st ?? {}) };
    for (const t of LIST_TYPES) delete copy[t];
    return JSON.stringify(copy);
  };
  if (withoutHashes(persisted) !== withoutHashes(next)) return true;
  const hashesChanged = LIST_TYPES.some(
    (t) => JSON.stringify(persisted?.[t] ?? null) !== JSON.stringify(next?.[t] ?? null)
  );
  return hashesChanged && now - at >= STATE_HASH_PERSIST_MS;
}

// A skipped write freezes the file's `fetchedAt`, and devices reject a file older than their own
// staleness bound — so every type must still be refreshed periodically even when unchanged.
//
// Live stays at 120s, inside the device's 180s bound (CacheConfig.LIVE_STORAGE_MAX_AGE_MS). The
// static floor is 600s, inside the 900s bound every current build applies to recent/upcoming; no
// shipped build falls back to the Vercel API any more, so the older 150s bound no longer binds.
// Each unchanged rewrite is a delete plus a multipart create, which is most of this bucket's POST
// bandwidth.
const REFRESH_FLOOR_LIVE_MS = Number(process.env.REFRESH_FLOOR_LIVE_MS) || 120_000;
const REFRESH_FLOOR_STATIC_MS = Number(process.env.REFRESH_FLOOR_STATIC_MS) || 600_000;
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

/**
 * One poll: Cricbuzz's live, recent and upcoming lists → the three Storage files, match-start
 * alerts, the live-count broadcast and the daily series walk.
 *
 * This was the whole of the `bouncer-poll-live-matches` function, scheduled every minute on its
 * own. It now runs at the top of every `bouncer-match-sync` run instead, which hands match-sync
 * the live list in memory: two schedules on the same minute cost ~1,440 extra executions a day,
 * and match-sync then downloaded the `live-matches` file its sibling had just uploaded. The files
 * are still written — the widgets, Android Auto, `ScorePollingWorker` and the Upcoming fallback
 * read them.
 *
 * @returns `{ results, lists, started }` — per-type outcome, each type's parsed data (`null` for a
 *   type whose fetch failed this run, in which case the caller reads the file instead), and the ids
 *   seen live.
 */
export async function runPoll({ storage, log, error }) {

  // Previous content hashes. Missing/corrupt state just means every type is rewritten this run.
  //
  // This function is poll-state's only writer, so a warm container reuses what it last wrote and
  // downloads the file only on a cold start.
  let prevState = stateMemo;
  if (!prevState) {
    prevState = {};
    try {
      prevState = parseJsonFile(await storage.getFileDownload(BUCKET_ID, STATE_FILE_ID)) ?? {};
    } catch { /* first run */ }
    persistedMemo = prevState;
    persistedAt = 0;
  }

  // Fetch all three types in parallel to minimize total write-time spread
  const types = LIST_TYPES;
  const now = Date.now();
  const formatCache = { ...(prevState.formats ?? {}) };
  const settled = await Promise.allSettled(types.map(async (type) => {
    const data = await fetchMatchList(type);

    // Upcoming fixtures carry their real format, so match-sync does not have to guess it.
    //
    // The upstream list is {id, title, status} and nothing else, and most titles name no format —
    // "Scotland vs Canada, 124th Match" is an ODI, "Durham vs Middlesex, Group B" is a 50-over
    // One-Day Cup game. match-sync used to probe T20/ODI/HUNDRED/TEST and take the first slice both
    // teams appeared in, which answers "where do these teams have a record", not "what is this
    // match". On 2026-08-07 that scored 9 of 13 fixtures on T20 form for ODI matches, and on
    // Scotland vs Canada it reversed the favourite.
    if (type === "upcoming") await attachFormats(data, formatCache, log, error);

    // An empty parse is Cricbuzz's answer being unreadable, not an answer that no match exists.
    // Overwriting a good file with an empty one takes every server-side surface dark at once, and
    // the previous file is always the better of the two. Same rule the series walk already applies
    // to a `CricbuzzBlockedError`.
    if (data.matches.length === 0 && prevState[type]?.writtenAt) {
      error(`Parsed 0 ${type} matches — keeping the previous file rather than emptying it`);
      return { type, data, hash: prevState[type].hash, writtenAt: prevState[type].writtenAt, skipped: true, keptPrevious: true };
    }

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
      //
      // The count now rides in poll-state, which this run has already downloaded. Reading the
      // separate `live-count` file every minute was a second download per run for one integer; it
      // is read only once, to migrate a state file written before `liveCount` existed.
      let prevCount = 0;
      if (typeof prevState.liveCount === "number") {
        prevCount = prevState.liveCount;
      } else {
        try {
          const parsed = parseJsonFile(await storage.getFileDownload(BUCKET_ID, "live-count"));
          // Tolerate a bare number in case a legacy body ever landed.
          prevCount = (typeof parsed === "number" ? parsed : parsed?.count) || 0;
        } catch { /* first run, or legacy non-JSON body */ }
      }
      if (newLiveCount !== prevCount) {
        log(`Live match count changed: ${prevCount} → ${newLiveCount}`);

        // Send FCM BEFORE recording the count so a timeout or throw can't update it without notifying
        await sendTopicMessage(
          "cricket-live-updates",
          { type: newLiveCount > prevCount ? "MATCH_STARTED" : "MATCH_ENDED", liveCount: String(newLiveCount) },
          { onError: error }
        );
      }
      nextState.liveCount = newLiveCount;

      // Per-match start alerts, to the topic a device subscribes to when it follows a match.
      const { starts, started } = matchStarts({
        matches: liveData?.matches ?? [],
        previous: prevState.startedIds,
        now,
      });
      nextState.startedIds = started;
      if (!fcmConfigured()) {
        if (starts.length) log(`FCM not configured — ${starts.length} match start alert(s) not sent`);
      } else {
        await Promise.all(starts.map((m) => sendTopicMessage(matchTopic(m.id), startAlert(m), { onError: error })));
      }
    } catch (e) {
      error(`FCM state-change check failed: ${e?.message}`);
    }
  }

  await syncSeries({ storage, prevState, nextState, now, log, error });

  if (shouldPersistState({ persisted: persistedMemo, next: nextState, persistedAt, now })) {
    try {
      await storage.deleteFile(BUCKET_ID, STATE_FILE_ID).catch(() => {});
      await storage.createFile(
        BUCKET_ID, STATE_FILE_ID,
        InputFile.fromBuffer(Buffer.from(JSON.stringify(nextState), "utf8"), `${STATE_FILE_ID}.json`),
        ["read(\"any\")"]
      );
      persistedMemo = nextState;
      persistedAt = now;
    } catch (e) {
      // Losing the state file only costs a redundant rewrite next run — never fail the whole run.
      error(`poll-state write failed: ${e?.message}`);
    }
  }
  // Memory is authoritative for the life of the container even if the upload failed; the file
  // exists for the next cold start.
  stateMemo = nextState;

  const lists = {};
  // Null where this run has no list the caller should trust: a failed fetch, or an empty parse that
  // kept the previous file — handing back that empty list would read as "nothing is live".
  types.forEach((t, i) => {
    const v = settled[i].status === "fulfilled" ? settled[i].value : null;
    lists[t] = v && !v.keptPrevious ? v.data : null;
  });
  // `started` is every match this function has seen live in the last six days. match-sync uses it
  // to tell a match it followed from one that was already over when it first looked.
  return { results, lists, started: nextState.startedIds ?? null };
}

const SERIES_REFRESH_MS = 24 * 3600 * 1000;
// A failed or empty scrape retries after this, not on the next one-minute run: each retry downloads
// the whole corpus again for the archive merge.
const SERIES_RETRY_MS = 3600 * 1000;

/**
 * Series sync: scrape Cricbuzz at most once per 24h server-side (replaces N per-device daily scrapes).
 *
 * Freshness is decided from `poll-state`, never by downloading `active-series`. That file is ~58 KB
 * and used to be downloaded on every one-minute run purely to read its `fetchedAt` — about 83 MB a
 * day, roughly half of the project's whole bandwidth meter, to answer "no" 1,439 times out of 1,440.
 * The corpus is downloaded only when a refresh is actually due, because the archive merge needs it.
 * Never throws.
 */
export async function syncSeries({ storage, prevState, nextState, now, log, error, fetchActive = fetchActiveSeries, fetchPage = fetchHtml }) {
  const nextAttemptAt = Math.max(
    (prevState.seriesFetchedAt ?? 0) + SERIES_REFRESH_MS,
    prevState.seriesRetryAt ?? 0,
  );
  // A state file from before these fields existed has neither, so the first run falls through,
  // downloads once, and adopts the file's own `fetchedAt` below.
  const hasClock = prevState.seriesFetchedAt != null || prevState.seriesRetryAt != null;
  if (hasClock && now < nextAttemptAt) return "fresh";

  try {
    let prevJson = null;
    try {
      prevJson = parseJsonFile(await storage.getFileDownload(BUCKET_ID, "active-series"));
    } catch { /* missing or parse error — treat as stale */ }

    if (prevJson?.fetchedAt && now - prevJson.fetchedAt < SERIES_REFRESH_MS) {
      nextState.seriesFetchedAt = prevJson.fetchedAt;
      delete nextState.seriesRetryAt;
      return "adopted";
    }

    const active = await fetchActive();
    const merged = await mergeArchiveSeries(active, prevJson, log, error, fetchPage);
    if (merged.series.length === 0) {
      nextState.seriesRetryAt = now + SERIES_RETRY_MS;
      return "empty";
    }
    const payload = JSON.stringify({
      data: merged.series,
      fetchedAt: now,
      archiveCursor: merged.cursor,
    });
    await storage.deleteFile(BUCKET_ID, "active-series").catch(() => {});
    await storage.createFile(
      BUCKET_ID, "active-series",
      InputFile.fromBuffer(Buffer.from(payload, "utf8"), "active-series.json"),
      ["read(\"any\")"]
    );
    nextState.seriesFetchedAt = now;
    delete nextState.seriesRetryAt;
    log(`Updated active-series (${merged.series.length} series, archive cursor ${merged.cursor})`);
    return "updated";
  } catch (e) {
    nextState.seriesRetryAt = now + SERIES_RETRY_MS;
    error(`Series sync failed: ${e?.message}`);
    return "error";
  }
}

/**
 * Stamp each upcoming fixture with Cricbuzz's `matchFormat`, scraping only when something is new.
 *
 * A match's format never changes, so a fixture only needs looking up once ever — [cache] carries
 * across runs in the state file and most runs therefore make no request at all. A failed or blocked
 * scrape is not an error: fixtures already in the cache still get stamped, and anything unresolved
 * simply arrives without the field, which is exactly the input match-sync's existing probe already
 * handles. Never throws — this must not be able to fail the match list it is decorating.
 */
/**
 * One tab's matches, straight off Cricbuzz.
 *
 * Returns the same `{ matches, count }` envelope the Vercel proxy did, so every consumer of the
 * stored file is unaffected by where the list came from — the matches inside it are simply richer.
 */
async function fetchMatchList(type) {
  const html = await fetchHtml(LIST_URLS[type], 12_000);
  const matches = parseMatchList(html).filter((m) => belongsOn(type, m)).slice(0, LIST_CAPS[type]);
  return { matches, count: matches.length };
}

/**
 * Whether a parsed match belongs on [type]'s list.
 *
 * A Cricbuzz list page carries the site-wide live ticker as well as its own tab's fixtures, so the
 * page a match was found on does not decide which tab it is for. Classified on `state` alone,
 * which is the same field and the same rule `CricBuzzScraper.filterMatches` applies on the device;
 * classifying `status` as well would put the two parsers on different rules.
 *
 * Live stays the catch-all: a state neither list recognises surfaces there rather than vanishing
 * from every tab.
 */
export function belongsOn(type, match) {
  const signal = classifyStatus(match.state);
  if (type === "upcoming") return signal === "PRE_MATCH";
  if (type === "recent") return signal === "TERMINAL";
  return signal !== "PRE_MATCH" && signal !== "TERMINAL";
}

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

// A started id is remembered this long, so a match that drops off the live list and comes back
// (Cricbuzz does this across a rain delay or an innings break) is not announced twice.
const STARTED_RETENTION_MS = 6 * 24 * 3600 * 1000;

/**
 * Live matches not seen live before. [previous] is `poll-state.startedIds` ({id: firstSeenMs}); when
 * it is absent — the first run after this shipped — every current match is recorded and none is
 * announced, or a deploy would push a start alert for every match already in progress.
 */
export function matchStarts({ matches, previous, now }) {
  const started = {};
  for (const [id, at] of Object.entries(previous ?? {})) {
    if (now - at < STARTED_RETENTION_MS) started[id] = at;
  }
  const starts = [];
  for (const m of matches) {
    const id = String(m?.id ?? "");
    if (!id || started[id] !== undefined) continue;
    started[id] = now;
    if (previous) starts.push(m);
  }
  return { starts, started };
}

/** The push for a match going live. */
export function startAlert(m) {
  const sides = (m.teams ?? []).map((t) => t?.name).filter(Boolean);
  const matchTitle = sides.length === 2 ? `${sides[0]} vs ${sides[1]}` : String(m.title ?? "").split(",")[0].trim();
  return {
    type: "match_start",
    match_id: String(m.id),
    title: matchTitle || "Match started",
    body: m.status || "Match is live",
    match_title: matchTitle,
  };
}
