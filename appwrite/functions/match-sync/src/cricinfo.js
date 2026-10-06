// ESPNcricinfo's live win prediction, read off its live-scores page.
//
// One page carries every live match in its `__NEXT_DATA__` block, each with a
// `liveInningPredictions` object — `{inningNumber, oversUnique, score, winProbability}` on the
// matches Cricinfo forecasts, null on the rest (most of them). It is captured to be set beside the
// model's own figure, never shown: `appwrite/tools/winprob-live-score.mjs` reads it back.
//
// Whether this page answers at all depends on where the request comes from — it has been 200 and
// 403 from the same laptop on different days — so every result says what came back, and nothing
// here throws.

export const CRICINFO_LIVE_URL = "https://www.espncricinfo.com/live-cricket-score";

/** Samples kept per match: one per poll, and a one-day match is about a hundred polls. */
export const CRICINFO_MAX_SAMPLES = 240;

const HEADERS = {
  "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
  Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "Accept-Language": "en",
};

/** Every distinct match object in the page data: any node that owns `liveInningPredictions`. */
export function matchesIn(nextData) {
  const byId = new Map();
  const walk = (node) => {
    if (!node || typeof node !== "object") return;
    if (Array.isArray(node)) {
      node.forEach(walk);
      return;
    }
    if (Object.hasOwn(node, "liveInningPredictions") && node.objectId != null) byId.set(node.objectId, node);
    for (const k of Object.keys(node)) walk(node[k]);
  };
  walk(nextData);
  return [...byId.values()];
}

/** One captured sample: the prediction as published, with enough of the match to join it later. */
export function rowFor(match, at) {
  return {
    at,
    cricinfoId: match.objectId,
    slug: match.slug ?? null,
    format: match.format ?? null,
    state: match.state ?? null,
    startTime: match.startTime ?? null,
    teams: (match.teams ?? []).map((t) => ({
      id: t.team?.objectId ?? null, name: t.team?.longName ?? null, score: t.score ?? null, scoreInfo: t.scoreInfo ?? null,
    })),
    liveInning: match.liveInning ?? null,
    liveOvers: match.liveOvers ?? null,
    statusText: match.statusText ?? null,
    winnerTeamId: match.winnerTeamId ?? null,
    predictions: match.liveInningPredictions,
  };
}

/** The page's matches and the rows that carry a prediction, or null when it has no data block. */
export function parseCricinfoPage(html, at) {
  const block = String(html ?? "").match(/<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
  if (!block) return null;
  let data;
  try {
    data = JSON.parse(block[1]);
  } catch {
    return null;
  }
  const matches = matchesIn(data);
  return {
    matches: matches.length,
    live: matches.filter((m) => m.state === "LIVE").length,
    rows: matches.filter((m) => m.liveInningPredictions != null).map((m) => rowFor(m, at)),
  };
}

/**
 * One poll. `ok` is false for a block, a redesigned page or a transport failure, and `reason`
 * says which; `status` and `bytes` are reported either way.
 */
export async function fetchCricinfoPredictions({ fetchImpl = fetch, now = Date.now(), timeoutMs = 8_000 } = {}) {
  try {
    const resp = await fetchImpl(CRICINFO_LIVE_URL, { headers: HEADERS, signal: AbortSignal.timeout(timeoutMs) });
    const body = await resp.text();
    const base = { status: resp.status, bytes: body.length };
    if (resp.status !== 200) return { ok: false, reason: `http ${resp.status}`, ...base, rows: [] };
    const parsed = parseCricinfoPage(body, now);
    if (!parsed) return { ok: false, reason: "no data block", ...base, rows: [] };
    return { ok: true, ...base, ...parsed };
  } catch (e) {
    return { ok: false, reason: e?.message ?? "fetch failed", status: null, bytes: 0, rows: [] };
  }
}

/** Stored samples plus a new row: one per innings and ball, oldest first, bounded. */
export function mergeCricinfo(stored, row) {
  const key = (r) => `${r?.predictions?.inningNumber}|${r?.predictions?.oversUnique}`;
  const byBall = new Map();
  for (const r of [...(Array.isArray(stored) ? stored : []), row]) if (r?.predictions) byBall.set(key(r), r);
  return [...byBall.values()].sort((a, b) => (a.at ?? 0) - (b.at ?? 0)).slice(-CRICINFO_MAX_SAMPLES);
}
