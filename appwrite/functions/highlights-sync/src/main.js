import { Client, Databases } from "node-appwrite";
import { initObservability, report, reportError, flushObservability } from "./observability.js";

// Appwrite auto-injects APPWRITE_FUNCTION_API_ENDPOINT and APPWRITE_FUNCTION_PROJECT_ID.
// APPWRITE_API_KEY must be set manually in the function's env vars.

// Hard ceiling on writes per run. The loop below previously iterated whatever the Highlightly API
// returned, unbounded — a large response meant a write burst with nothing to stop it.
const MAX_WRITES_PER_RUN = 50;

// Ids already written, kept in module scope so it survives warm invocations of the */15 cron.
// Highlightly re-returns the same clips every run, so without this every run rewrote all of them.
// Bounded so a long-lived container cannot grow it without limit.
const MAX_SEEN = 2000;
const seenIds = new Set();

const HIGHLIGHTLY_HOST = "cricket-highlights-api.p.rapidapi.com";

// The API rejects anything larger: `{"message":"limit must not be greater than 40"}`. This was
// previously unset, which took an unpinned server default.
const PAGE_LIMIT = 40;
const MAX_PAGES_PER_DATE = 2;

// ── Why this function looks back at all ──────────────────────────────────────
//
// It used to fetch only `today`. Highlightly publishes a clip hours to days after the match it
// covers, so by the time a clip appeared the calendar date had rolled and this function never
// asked again. Verified 2026-08-05: clips 16241 (2026-07-29 match) and 16287 (2026-08-01 match)
// were available upstream and absent from the table, permanently. A same-day-only window against a
// source with publication lag loses whatever the lag exceeds.
const BACKFILL_DAYS = 3;

// ── Why the run is slot-gated instead of the cron being changed ──────────────
//
// The Highlightly plan allows 100 requests per rolling window (`x-ratelimit-requests-limit: 100`,
// header verified live 2026-08-06, reset observed at ~12.5h rather than midnight). The schedule is
// `*/15` = 96 runs, so fetching even one date on every run consumes the entire budget and leaves
// no room for the look-back above — the fix would be shipped and never fire.
//
// Rather than edit the schedule, the run decides in code whether it is on a working slot:
//   - minute < 15  → the one run per hour that does anything      (24 runs/day)
//   - hour % 4 == 0 → that run also sweeps the back-window        (6 runs/day)
// Cost: 24 + 6*3 = 42 requests/day against a cap of 100.
//
// `minute < 15` rather than `minute === 0` because Appwrite schedules drift by seconds and
// observed start times are :00:04, :15:12, :30:03, :45:19. Exactly one run per hour lands in
// [0, 15), whatever the drift.
const WORKING_SLOT_MINUTE = 15;
const SWEEP_EVERY_HOURS = 4;

// Below this many remaining requests, a run stops after `today` and skips the back-window. The
// reserve exists so an unrelated consumer (a manual test, a redeploy probe) cannot be starved by
// the cron, and so the tail of a window degrades to fewer dates rather than to 429s.
const QUOTA_RESERVE = 10;

// Consecutive SWEEP runs that saw zero items across the whole four-date window before this is
// worth an event. A sweep runs every 4h, so 3 is ~12 hours of a completely empty back-window —
// well past anything upstream volume explains, and precisely the signal that did not exist on
// 2026-07-30. Counted on items RETURNED, not items written: in steady state everything returned is
// already in `seenIds` and writes are legitimately zero.
const DRY_SWEEP_ALERT = 3;
let consecutiveDrySweeps = 0;

function rememberSeen(id) {
  if (seenIds.size >= MAX_SEEN) seenIds.clear(); // cheap bound; a reset only costs one re-write pass
  seenIds.add(id);
}

const isoDate = (ms) => new Date(ms).toISOString().slice(0, 10);
const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The dates this run should fetch, newest first, or `[]` when the run is not on a working slot.
 *
 * Exported for tests: the slot arithmetic is the whole quota argument above, and getting it wrong
 * is either a silently dead back-window or a blown budget.
 */
export function planDates(nowMs) {
  const now = new Date(nowMs);
  if (now.getUTCMinutes() >= WORKING_SLOT_MINUTE) return [];
  const dates = [isoDate(nowMs)];
  if (now.getUTCHours() % SWEEP_EVERY_HOURS === 0) {
    for (let i = 1; i <= BACKFILL_DAYS; i++) dates.push(isoDate(nowMs - i * DAY_MS));
  }
  return dates;
}

/** True when this run's date list includes the back-window, not just today. */
export const isSweep = (dates) => dates.length > 1;

/**
 * `x-ratelimit-requests-remaining` off a fetch Response, or null when absent.
 *
 * Header name verified against a live RapidAPI response on 2026-08-06 rather than taken from the
 * docs, which do not name it. A null here must never read as "0 remaining" — that would latch the
 * back-window off permanently the moment RapidAPI renamed or dropped the header.
 */
export function parseRemaining(headers) {
  const raw = headers?.get?.("x-ratelimit-requests-remaining");
  if (raw == null || raw === "") return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

/**
 * Classify one Highlightly response body + status into a discriminated outcome.
 *
 * The old code returned a bare `[]` for a 429, a 5xx, an unrecognised body and a genuine empty day
 * alike, and the caller logged "Highlightly returned 0 highlights" for all four. That is the same
 * class of bug as the AI-commentary one: a failure wearing the shape of a successful zero.
 */
export function classifyResponse(status, body) {
  if (status === 429 || /quota|rate ?limit|too many requests/i.test(String(body?.message ?? ""))) {
    return { outcome: "quota", items: [], totalCount: 0 };
  }
  if (status < 200 || status >= 300) {
    return { outcome: "http_error", items: [], totalCount: 0 };
  }
  const extracted = extractHighlights(body);
  if (!extracted.matched) return { outcome: "unknown_shape", items: [], totalCount: 0, shape: "unknown" };
  return {
    outcome: "ok",
    items: extracted.items,
    shape: extracted.shape,
    // Absent pagination means "what you got is all there is" — not "there are zero".
    totalCount: Number(body?.pagination?.totalCount ?? extracted.items.length),
  };
}

/** Merge per-date results into one id-unique list. A clip can legitimately appear under two dates. */
export function dedupeById(items) {
  const byId = new Map();
  for (const h of items) if (!byId.has(h.id)) byId.set(h.id, h);
  return [...byId.values()];
}

export default async ({ req, res, log, error }) => {
  initObservability({ log });
  const HIGHLIGHTLY_API_KEY = process.env.HIGHLIGHTLY_API_KEY ?? "";
  const HIGHLIGHTS_DB_ID = process.env.APPWRITE_HIGHLIGHTS_DB_ID ?? "";
  const HIGHLIGHTS_COL_ID = process.env.APPWRITE_HIGHLIGHTS_COL_ID ?? "";
  const META_COL_ID = process.env.APPWRITE_HIGHLIGHTS_META_COL_ID ?? "";

  const finish = async (body) => {
    await flushObservability();
    return res.json(body);
  };

  const client = new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    .setKey(process.env.APPWRITE_API_KEY ?? "");
  const db = new Databases(client);

  const nowMs = Date.now();
  const today = isoDate(nowMs);

  // ── Slot gate ───────────────────────────────────────────────────────────
  // Three runs in four do nothing and spend nothing. See the constants above for why this is not a
  // schedule change.
  const dates = planDates(nowMs);
  if (dates.length === 0) {
    // Logged rather than returning silently: three runs in four take this path, and an execution
    // with an empty log reads the same as a deployment that died before reaching any code.
    log(`Off-slot run at ${new Date(nowMs).toISOString()} — no Highlightly requests`);
    return await finish({ skipped: true, reason: "off_slot" });
  }
  const sweep = isSweep(dates);

  if (!HIGHLIGHTLY_API_KEY) {
    error("HIGHLIGHTLY_API_KEY not set — cannot fetch highlights");
    report("highlights-sync has no Highlightly API key", { dates: dates.join(",") }, "error");
    return await finish({ skipped: true, reason: "missing_api_key" });
  }

  // ── Fetch the date window ───────────────────────────────────────────────
  const collected = [];
  let remaining = null;
  let fetchedDates = 0;
  for (const date of dates) {
    // `today` is always fetched; the back-window only while there is budget for it. `remaining`
    // is null until the first response, so the reserve cannot block the first date.
    if (date !== today && remaining !== null && remaining <= QUOTA_RESERVE) {
      log(`Quota reserve reached (${remaining} left) — skipping back-window from ${date}`);
      break;
    }

    const result = await fetchDate(date, HIGHLIGHTLY_API_KEY, log, error);
    if (result.remaining !== null) remaining = result.remaining;

    if (result.outcome === "quota") {
      // Expected at the tail of a window, and the one outcome that must never be mistaken for an
      // empty day — hence info, not warning, and hence its own response body.
      log(`Highlightly quota exhausted at ${date}`);
      report("Highlightly request quota exhausted", { date, fetchedDates }, "info");
      if (fetchedDates === 0) return await finish({ skipped: true, reason: "quota_exhausted" });
      break;
    }
    if (result.outcome === "http_error") {
      error(`Highlightly API returned ${result.status} for ${date}`);
      report("Highlightly returned a non-2xx", { date, status: result.status }, "warning");
      continue;
    }
    if (result.outcome === "unknown_shape") {
      error(`Highlightly response shape unknown for ${date} — top-level keys: ${result.keys}`);
      // The event that would catch a real API change, as opposed to a quiet day.
      report("Highlightly response shape unrecognised", { date, keys: result.keys }, "error");
      continue;
    }

    fetchedDates++;
    collected.push(...result.items);
    log(`Highlightly ${date}: ${result.items.length} items (shape=${result.shape}, remaining=${remaining ?? "?"})`);
  }

  const highlights = dedupeById(collected);

  // Only a sweep that actually reached every date counts. One truncated by the quota reserve saw
  // less than the back-window, so its emptiness says nothing about upstream.
  if (sweep && fetchedDates === dates.length) {
    consecutiveDrySweeps = highlights.length === 0 ? consecutiveDrySweeps + 1 : 0;
    if (consecutiveDrySweeps >= DRY_SWEEP_ALERT) {
      report(
        "highlights-sync back-window has been empty for consecutive sweeps",
        { sweeps: consecutiveDrySweeps, dates: dates.join(","), remaining: remaining ?? -1 },
        "warning"
      );
      // Reset so the next event needs another full run of dry sweeps rather than firing every 4h.
      consecutiveDrySweeps = 0;
    }
  }

  if (highlights.length === 0) {
    log(`Highlightly returned 0 highlights across ${fetchedDates} date(s)`);
    return await finish({ upserted: 0, dates: fetchedDates, sweep });
  }

  // ── Upsert each highlight into Appwrite DB ──────────────────────────────
  const now = Date.now();
  let upserted = 0;
  const failed = [];

  // Newest first, so if the cap truncates the run it sheds the oldest clips rather than the ones
  // users are most likely to want. startDate is an ISO string, so parse it — Number() yields NaN
  // and an NaN comparator leaves the order undefined.
  const startedAt = (h) => Date.parse(h.match?.startDate ?? "") || 0;
  // Skipping already-written ids means a later metadata change to an existing clip (say an
  // embed_url appearing) is not repushed until the container recycles. Accepted: Highlightly
  // clip metadata is effectively immutable once published, and the alternative was rewriting
  // every clip every run forever.
  const pending = highlights
    .filter((h) => !seenIds.has(h.id))
    .sort((a, b) => startedAt(b) - startedAt(a));
  const skipped = highlights.length - pending.length;

  const batch = pending.slice(0, MAX_WRITES_PER_RUN);
  if (pending.length > batch.length) {
    // Never let a cap truncate silently — a shorter list would otherwise read as "all synced".
    log(`Write cap hit: ${pending.length} new highlights, writing newest ${batch.length}`);
    report("highlights-sync write cap truncated a run", { pending: pending.length, written: batch.length });
  }

  for (const h of batch) {
    const docId = `hl-${h.id}`;
    const payload = buildPayload(h, now);
    try {
      // Create first: most rows in a run are new, so this is one write instead of a guaranteed
      // failed update followed by a create.
      await db.createDocument(HIGHLIGHTS_DB_ID, HIGHLIGHTS_COL_ID, docId, payload, ["read(\"any\")"]);
      upserted++;
      rememberSeen(h.id);
    } catch (e) {
      if (e?.code !== 409) {
        failed.push({ id: h.id, message: e?.message });
        error(`Failed to create highlight ${h.id}: ${e?.message}`);
        reportError(e, { stage: "create", highlightId: String(h.id) });
        continue;
      }
      try {
        await db.updateDocument(HIGHLIGHTS_DB_ID, HIGHLIGHTS_COL_ID, docId, payload);
        upserted++;
        rememberSeen(h.id);
      } catch (e2) {
        failed.push({ id: h.id, message: e2?.message });
        error(`Failed to upsert highlight ${h.id}: ${e2?.message}`);
        reportError(e2, { stage: "update", highlightId: String(h.id) });
      }
    }
  }

  // ── Persist fetch state for midnight reset tracking ─────────────────────
  if (META_COL_ID) {
    const metaPayload = { fetch_date: today, last_fetch_at: now };
    try {
      await db.updateDocument(HIGHLIGHTS_DB_ID, META_COL_ID, "fetch-state", metaPayload);
    } catch (_) {
      try {
        await db.createDocument(HIGHLIGHTS_DB_ID, META_COL_ID, "fetch-state", metaPayload);
      } catch (e3) {
        error(`Failed to persist fetch-state meta: ${e3?.message}`);
      }
    }
  }

  log(
    `Upserted ${upserted} highlights across ${fetchedDates} date(s); ` +
      `${skipped} already known, ${failed.length} failures, ${remaining ?? "?"} requests left`
  );
  return await finish({ upserted, skipped, failed: failed.length, dates: fetchedDates, sweep, date: today });
};

/**
 * Pull every highlight for one date, paging while the API says there are more.
 *
 * Returns the discriminated outcome from `classifyResponse`, plus the remaining-quota reading and
 * the HTTP status. A thrown fetch is `http_error` with status 0 — a network failure is a failure,
 * not an empty day.
 */
async function fetchDate(date, highlightlyApiKey, log, error) {
  const items = [];
  let remaining = null;
  let offset = 0;
  let shape = "data";

  for (let page = 0; page < MAX_PAGES_PER_DATE; page++) {
    const url =
      `https://${HIGHLIGHTLY_HOST}/highlights?date=${date}&limit=${PAGE_LIMIT}` +
      (offset ? `&offset=${offset}` : "");
    log(`Fetching: ${url}`);

    let response;
    try {
      response = await fetch(url, {
        headers: {
          "x-rapidapi-key": highlightlyApiKey,
          "x-rapidapi-host": HIGHLIGHTLY_HOST,
          "Content-Type": "application/json",
        },
      });
    } catch (e) {
      error(`Highlightly fetch failed for ${date}: ${e?.message}`);
      return { outcome: "http_error", items, status: 0, remaining, keys: "" };
    }

    remaining = parseRemaining(response.headers) ?? remaining;

    let body;
    try {
      body = await response.json();
    } catch {
      return { outcome: "unknown_shape", items, status: response.status, remaining, keys: "(unparseable)" };
    }

    const classified = classifyResponse(response.status, body);
    if (classified.outcome !== "ok") {
      return {
        ...classified,
        items,
        status: response.status,
        remaining,
        keys: Array.isArray(body) ? "[array]" : Object.keys(body ?? {}).join(", "),
      };
    }

    items.push(...classified.items);
    shape = classified.shape;
    offset += classified.items.length;
    // Stop on exhaustion, on an empty page (or `offset` never advances and this loops), and on the
    // page cap.
    if (classified.items.length === 0 || offset >= classified.totalCount) break;
  }

  return { outcome: "ok", items, status: 200, remaining, shape, keys: "" };
}

function extractHighlights(data) {
  if (Array.isArray(data)) return { matched: true, shape: "array", items: data };
  if (Array.isArray(data?.highlights)) return { matched: true, shape: "highlights", items: data.highlights };
  if (Array.isArray(data?.data)) return { matched: true, shape: "data", items: data.data };
  if (Array.isArray(data?.results)) return { matched: true, shape: "results", items: data.results };
  return { matched: false, shape: "unknown", items: [] };
}

function buildPayload(h, now) {
  return {
    highlight_id: String(h.id),
    type: h.type ?? "UNVERIFIED",
    img_url: h.imgUrl ?? "",
    title: h.title ?? "",
    url: h.url ?? "",
    embed_url: h.embedUrl?.trim() || null,
    channel: h.channel ?? "",
    source: h.source ?? "",
    match_home_team: h.match?.homeTeam?.name ?? "",
    match_away_team: h.match?.awayTeam?.name ?? "",
    match_home_abbr: h.match?.homeTeam?.abbreviation ?? "",
    match_away_abbr: h.match?.awayTeam?.abbreviation ?? "",
    match_league_name: h.match?.league?.name ?? "",
    match_league_season: h.match?.league?.season ?? 0,
    match_start_date: h.match?.startDate ?? "",
    match_end_date: h.match?.endDate ?? "",
    raw_match_id: h.match?.id ?? "",
    fetched_at: now,
  };
}
