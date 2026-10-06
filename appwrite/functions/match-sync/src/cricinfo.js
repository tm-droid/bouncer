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

/** A page far larger than the real ones (600-950 KB) is not read to the end, let alone parsed. */
const MAX_PAGE_BYTES = 4_000_000;
// The real page is about 300,000 nodes, nested under twenty deep.
const MAX_WALK_NODES = 3_000_000;
const MAX_WALK_DEPTH = 64;
const MAX_LISTED = 80;

/**
 * A response body as text, or null once it passes [maxBytes]. The cap is applied while reading:
 * checked after `resp.text()` it would bound the parse and not the memory, and the length is a
 * third party's to choose.
 */
export async function readBounded(resp, maxBytes = MAX_PAGE_BYTES) {
  if (Number(resp.headers?.get?.("content-length")) > maxBytes) {
    await resp.body?.cancel?.().catch(() => {});
    return null;
  }
  if (!resp.body?.getReader) {
    const body = await resp.text();
    return body.length > maxBytes ? null : body;
  }
  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let body = "";
  let bytes = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    bytes += value.byteLength;
    if (bytes > maxBytes) {
      await reader.cancel().catch(() => {});
      return null;
    }
    body += decoder.decode(value, { stream: true });
  }
  return body + decoder.decode();
}

/**
 * Every distinct match object in the page data: any node that owns `liveInningPredictions`.
 * The walk stops at a node count, a depth and a match count, each checked as it goes — the
 * document's shape is a third party's, and a bound applied to the result would come after the work.
 */
export function matchesIn(nextData) {
  const byId = new Map();
  let nodes = 0;
  const walk = (node, depth) => {
    if (!node || typeof node !== "object" || depth > MAX_WALK_DEPTH || ++nodes > MAX_WALK_NODES || byId.size >= MAX_LISTED) return;
    if (Array.isArray(node)) {
      for (const child of node) walk(child, depth + 1);
      return;
    }
    if (Object.hasOwn(node, "liveInningPredictions") && node.objectId != null) byId.set(node.objectId, node);
    for (const k of Object.keys(node)) walk(node[k], depth + 1);
  };
  walk(nextData, 0);
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
    // Every match on the page, for joining to Cricbuzz's. Bounded: the page lists about thirty.
    list: matches.slice(0, MAX_LISTED).map(slimMatch).filter(Boolean),
  };
}

/**
 * One poll. `ok` is false for a block, a redesigned page or a transport failure, and `reason`
 * says which; `status` and `bytes` are reported either way.
 */
export async function fetchCricinfoPredictions({ fetchImpl = fetch, now = Date.now(), timeoutMs = 8_000 } = {}) {
  try {
    const resp = await fetchImpl(CRICINFO_LIVE_URL, { headers: HEADERS, signal: AbortSignal.timeout(timeoutMs) });
    const body = await readBounded(resp);
    if (body === null) return { ok: false, reason: "page too large", status: resp.status, bytes: MAX_PAGE_BYTES, rows: [] };
    const base = { status: resp.status, bytes: body.length };
    if (resp.status !== 200) return { ok: false, reason: `http ${resp.status}`, ...base, rows: [] };
    const parsed = parseCricinfoPage(body, now);
    if (!parsed) return { ok: false, reason: "no data block", ...base, rows: [] };
    return { ok: true, ...base, ...parsed };
  } catch (e) {
    return { ok: false, reason: e?.message ?? "fetch failed", status: null, bytes: 0, rows: [] };
  }
}

const SLUG = /^[a-z0-9-]{1,120}$/;
const DIGITS = /^\d{1,12}$/;

/** The tabs of a match page this function reads. */
export const CRICINFO_TABS = ["ball-by-ball-commentary", "match-statistics"];

/**
 * A match's page address, or null. The slugs and ids come off a third party's page and are on
 * their way into a URL, so each is checked against the only shape Cricinfo gives them.
 */
export function cricinfoMatchUrl(match, tab) {
  const series = match?.series ?? {};
  const parts = [series.slug, match?.slug];
  const ids = [series.objectId, match?.objectId].map((v) => String(v ?? ""));
  if (!CRICINFO_TABS.includes(tab) || !parts.every((p) => SLUG.test(String(p ?? ""))) || !ids.every((i) => DIGITS.test(i))) return null;
  return `https://www.espncricinfo.com/series/${parts[0]}-${ids[0]}/${parts[1]}-${ids[1]}/${tab}`;
}

/** A match page's `content` and `match` objects, or null when it has no data block. */
export function matchPageData(html) {
  const block = String(html ?? "").match(/<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
  if (!block) return null;
  try {
    const data = JSON.parse(block[1])?.props?.appPageProps?.data;
    return data?.content && typeof data.content === "object" ? { content: data.content, match: data.match ?? null } : null;
  } catch {
    return null;
  }
}

// ── Joining a Cricinfo match to a Cricbuzz one ───────────────────────────────
//
// The two sites share no id. A match is the same match when both sides have the same name and the
// start times agree; anything short of that is no join, and the match is simply not combined.

/** What the join and the page address need from a live-page match, bounded. */
export function slimMatch(match) {
  const id = String(match?.objectId ?? "");
  if (!DIGITS.test(id)) return null;
  return {
    objectId: id,
    slug: text(match.slug),
    series: { slug: text(match.series?.slug), objectId: String(match.series?.objectId ?? "") },
    state: text(match.state),
    startTime: text(match.startTime),
    hasCommentary: match.hasCommentary === true,
    // "feedback" on the matches a commentator is writing; empty where there is a scorer alone.
    ballByBallSource: text(match.ballByBallSource),
    teams: (Array.isArray(match.teams) ? match.teams : []).slice(0, 2).map((t) => ({
      name: text(t?.team?.longName), abbreviation: text(t?.team?.abbreviation),
    })),
  };
}

const NAME_ALIASES = [
  [/\bunited states of america\b/g, "usa"],
  [/\bunited arab emirates\b/g, "uae"],
  [/\bunder[- ]?19s?\b/g, "u19"],
  [/\bwomen's\b/g, "women"],
];

/** A team name as both sites would write it: lower case, aliases folded, punctuation gone. */
export function joinName(name) {
  let n = String(name ?? "").toLowerCase();
  for (const [from, to] of NAME_ALIASES) n = n.replace(from, to);
  return n.replace(/[^a-z0-9]+/g, " ").trim();
}

/** Start times further apart than this are different matches between the same two sides. */
const JOIN_START_WINDOW_MS = 12 * 60 * 60 * 1000;

/**
 * The Cricinfo match that is this Cricbuzz match, or null. Null too when two fit: a doubleheader
 * between the same sides is not a guess between two.
 *
 * @param cb   a poll summary: `{ startDate: epoch-ms string, teams: [{ name }] }`
 * @param list `slimMatch` rows from the live page
 */
export function joinCricinfoMatch(cb, list) {
  const want = (cb?.teams ?? []).map((t) => joinName(t?.name)).filter(Boolean).sort();
  const start = Number(cb?.startDate);
  if (want.length !== 2 || !Number.isFinite(start) || start <= 0) return null;
  const hits = (Array.isArray(list) ? list : []).filter((m) => {
    const have = (m?.teams ?? []).map((t) => joinName(t?.name)).filter(Boolean).sort();
    const at = Date.parse(m?.startTime ?? "");
    return have.length === 2 && have[0] === want[0] && have[1] === want[1]
      && Number.isFinite(at) && Math.abs(at - start) <= JOIN_START_WINDOW_MS;
  });
  return hits.length === 1 ? hits[0] : null;
}

// ── A match page's deliveries ────────────────────────────────────────────────

const MAX_BALL_TEXT = 500;
const MAX_BALLS_PER_PAGE = 40;
const MAX_OVERS_PER_INNINGS = 200;

/**
 * Third-party text made safe to stand inside a prompt, as DATA.
 *
 * This text is sent to the writer and the checker beside other deliveries, in a prompt whose
 * structure is line breaks, `[key]:` tags and `[#n]` / `Source:` / `Commentary:` labels, and whose
 * notes are delimited by square brackets. So nothing here may carry any of that: it comes out as
 * one line, with no bracket, brace, angle bracket or backtick, no control or invisible character,
 * and no label a prompt uses. What a model then makes of the words is the checker's to judge; what
 * this guarantees is that the words cannot pose as another delivery, another verdict or a note.
 */
export function promptSafe(value, max = MAX_BALL_TEXT) {
  // Bounded before the passes below, not after: each is linear, but in a third party's length.
  let out = String(value ?? "").slice(0, 8 * max);
  // To a fixed point. One pass is not enough when a pass can produce what an earlier one removes:
  // "&amp;#91;" decodes to "&#91;", which decodes to a bracket, and a compatibility character
  // normalises into a colon or an angle bracket. Each round shortens or keeps the text, so it ends.
  for (let round = 0; round < MAX_SAFE_ROUNDS; round++) {
    const before = out;
    out = out
      // First, so that every later pass sees the characters a model would.
      .normalize("NFKC")
      .replace(/&#x([0-9a-f]{1,6});|&#(\d{1,7});/gi, (_m, hex, dec) => {
        const code = hex ? parseInt(hex, 16) : Number(dec);
        return code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : " ";
      })
      .replace(/&amp;/gi, "&").replace(/&nbsp;/gi, " ").replace(/&rsquo;|&lsquo;|&apos;/gi, "'").replace(/&quot;|&ldquo;|&rdquo;/gi, '"')
      .replace(/&lt;/gi, "<").replace(/&gt;/gi, ">")
      .replace(/<[^>]*>/g, " ")
      // Controls, line and paragraph separators, zero-width and bidirectional marks.
      .replace(/[\u0000-\u001f\u007f-\u009f\u2028\u2029\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/g, " ")
      .replace(/[\[{<]/g, "(").replace(/[\]}>]/g, ")")
      .replace(/[`|#]/g, " ")
      .replace(/\s+/g, " ")
      // After the collapse, so a label cannot be hidden by what sat between its words.
      .replace(/\b(source|commentary|second report|scorer's note|system|assistant|user)\s*:/gi, "$1 -")
      .trim();
    if (out === before) break;
  }
  // Cut on a code point, and with no half of a pair left anywhere: a lone surrogate is not valid
  // UTF-8, the provider refuses the request, and the request is a batch of twelve deliveries.
  return Array.from(out).slice(0, max).join("").replace(LONE_SURROGATE_RE, "").trim();
}
const MAX_SAFE_ROUNDS = 6;
const LONE_SURROGATE_RE = /[\ud800-\udbff](?![\udc00-\udfff])|(?<![\ud800-\udbff])[\udc00-\udfff]/g;

const plainText = (items) =>
  promptSafe((Array.isArray(items) ? items : []).slice(0, 6).map((i) => (typeof i?.html === "string" ? i.html.slice(0, 4 * MAX_BALL_TEXT) : "")).join(" "));

// A header is two people's names and the word between them; a dismissal is a scorecard entry.
// Neither is prose, so each is held to its shape rather than merely cleaned.
const HEADER_RE = /^[\p{L}][\p{L} .'’-]{0,48} to [\p{L}][\p{L} .'’-]{0,48}$/u;
const DISMISSAL_RE = /^[\p{L}†(][\p{L}\d †()&.,'’/-]{0,78}$/u;
const HOW_RE = /^[a-z][a-z ]{1,30}$/;
const shaped = (value, shape, max) => {
  const v = promptSafe(value, max);
  return shape.test(v) ? v : null;
};

const int = (v) => (Number.isInteger(v) ? v : null);

/** One delivery off the page, bounded, or null when it has no place in an over. */
export function ballOf(comment) {
  const innings = int(comment?.inningNumber);
  const over = int(comment?.overNumber);
  const ballNumber = int(comment?.ballNumber);
  if (!innings || innings < 1 || !over || over < 1 || !ballNumber || ballNumber < 1 || ballNumber > 9) return null;
  const runs = int(comment.totalRuns);
  if (runs === null || runs < 0 || runs > 12) return null;
  // A delivery whose header is not two names is not one this can join or write from.
  const title = shaped(comment.title, HEADER_RE, 100);
  if (!title) return null;
  const extra = comment.wides > 0 ? "WIDE" : comment.noballs > 0 ? "NOBALL" : comment.legbyes > 0 ? "LEGBYE" : comment.byes > 0 ? "BYE" : null;
  return {
    innings,
    // Built from the two integers, not from `oversActual`: a float is not a label.
    label: `${over - 1}.${ballNumber}`,
    title,
    text: plainText(comment.commentTextItems),
    runs,
    four: comment.isFour === true,
    six: comment.isSix === true,
    wicket: comment.isWicket === true,
    dismissal: comment.isWicket === true ? shaped(comment.dismissalText?.long, DISMISSAL_RE, 80) : null,
    how: comment.isWicket === true ? shaped(comment.dismissalText?.short, HOW_RE, 32) : null,
    extra,
    at: Number.isFinite(Date.parse(comment.timestamp ?? "")) ? Date.parse(comment.timestamp) : null,
  };
}

/** Whether a delivery is re-bowled, and so shares its label with the ball after it. */
const rebowled = (extra) => extra === "WIDE" || extra === "NOBALL";

/** The key both sites' legal deliveries share. A wide or a no-ball has none. */
export function ballKey(innings, label, extra = null) {
  if (rebowled(extra) || !Number.isInteger(Number(innings)) || !/^\d{1,3}\.\d$/.test(String(label ?? ""))) return null;
  return `${Number(innings)}|${label}`;
}

/** One completed over as an over-history point, in the shape `mergeOverHistory` keeps. */
function overPoint(over, innings, team) {
  const number = int(over?.overNumber);
  if (!number || number < 1 || over.isComplete !== true) return null;
  return {
    innings, over: number, runs: int(over.overRuns), score: int(over.totalRuns), wickets: int(over.totalWickets), team,
  };
}

/**
 * What a ball-by-ball page carries: its window of deliveries (about three overs, newest first on
 * the page, oldest first here) and every completed over of every innings.
 */
export function parseCricinfoMatchPage(html) {
  const data = matchPageData(html);
  if (!data) return null;
  const { content } = data;
  const balls = (Array.isArray(content.comments) ? content.comments : [])
    .slice(0, MAX_BALLS_PER_PAGE).map(ballOf).filter(Boolean)
    .sort((a, b) => (a.at ?? 0) - (b.at ?? 0));
  const overs = [];
  for (const inn of (Array.isArray(content.innings) ? content.innings : []).slice(0, 4)) {
    const innings = int(inn?.inningNumber);
    if (!innings || innings < 1) continue;
    const team = shaped(inn.team?.abbreviation, /^[\p{L}\d-]{1,10}$/u, 10);
    for (const o of (Array.isArray(inn.inningOvers) ? inn.inningOvers : []).slice(0, MAX_OVERS_PER_INNINGS)) {
      const point = overPoint(o, innings, team);
      if (point) overs.push(point);
    }
  }
  return { balls, overs };
}

const nameWords = (name) => String(name ?? "").toLowerCase().split(/[^a-z]+/).filter((w) => w.length > 2);
// Cricinfo's header is a short name and not always the surname ("Arshdeep" for Arshdeep Singh,
// "Hosein" for Akeal Hosein), so one shared word is the test.
const sameName = (a, b) => nameWords(a).some((w) => nameWords(b).includes(w));

/**
 * Whether Cricinfo's delivery and Cricbuzz's are the same event, or the reason they are not.
 *
 * Two sites keyed to one ball label can still be describing different things: a correction one
 * has published and the other has not, or a label that drifted after an umpire's recount. A
 * second report that disagrees on who faced, on the runs or on a wicket is not a second report
 * of this ball, and is left out.
 *
 * @param cb `{ clean, runs, wicket }` — the Cricbuzz line and what its outcome clause says
 */
export function disagreement(cb, ball) {
  const header = String(cb?.clean ?? "").match(/^(.+?) to ([^,]+),/);
  const title = String(ball?.title ?? "").match(/^(.+?) to (.+)$/);
  if (!header || !title) return "unreadable header";
  if (!sameName(header[1], title[1])) return "bowler";
  if (!sameName(header[2], title[2])) return "batter";
  if (Boolean(cb.wicket) !== ball.wicket) return "wicket";
  // A dismissal's clause states no runs on Cricbuzz, so there is nothing to compare.
  if (!ball.wicket && cb.runs !== null && cb.runs !== ball.runs) return "runs";
  return null;
}

const runsWord = (n) => (n === 0 ? "no run" : n === 1 ? "1 run" : `${n} runs`);

/**
 * A Cricinfo delivery written the way Cricbuzz writes one — "<Bowler> to <Batter>, <outcome>,
 * <description>" — so everything downstream that reads the outcome clause reads this unchanged.
 * Legal deliveries only.
 */
export function asCricbuzzLine(ball) {
  if (!ball?.title || rebowled(ball.extra)) return null;
  let outcome;
  if (ball.wicket) outcome = `out ${ball.how ?? "dismissed"}${ball.dismissal ? `, ${ball.dismissal}` : ""}`;
  else if (ball.extra === "LEGBYE") outcome = `leg byes, ${ball.four ? "FOUR" : runsWord(ball.runs)}`;
  else if (ball.extra === "BYE") outcome = `byes, ${ball.four ? "FOUR" : runsWord(ball.runs)}`;
  else if (ball.six) outcome = "SIX";
  else if (ball.four) outcome = "FOUR";
  else outcome = runsWord(ball.runs);
  return `${ball.title}, ${outcome}${ball.text ? `, ${ball.text}` : ""}`;
}

const MAX_GAP_OVERS = 4;

/** The legal ball labels a hole spans: `after` and `before` are the labels either side of it. */
export function labelsInGap(after, before, ballsPerOver = 6) {
  const a = String(after ?? "").match(/^(\d{1,3})\.(\d)$/);
  const b = String(before ?? "").match(/^(\d{1,3})\.(\d)$/);
  if (!a || !b) return [];
  const [fromOver, fromBall, toOver, toBall] = [a[1], a[2], b[1], b[2]].map(Number);
  const out = [];
  // The tail of the source over is left alone, as `findCommentaryGaps` leaves it: an earlier run
  // may already hold it.
  if (toOver === fromOver) {
    for (let n = fromBall + 1; n < toBall; n++) out.push(`${toOver}.${n}`);
    return out;
  }
  // A hole wider than the window Cricinfo's page holds cannot be filled from it, and the two
  // labels are a third party's: the span is checked before anything is built from it.
  if (toOver < fromOver || toOver - fromOver > MAX_GAP_OVERS) return [];
  for (let o = fromOver + 1; o < toOver; o++) for (let n = 1; n <= ballsPerOver; n++) out.push(`${o}.${n}`);
  for (let n = 1; n < toBall; n++) out.push(`${toOver}.${n}`);
  return out;
}

/** One match page. `ok` false says why; nothing here throws. */
export async function fetchCricinfoMatch(match, { fetchImpl = fetch, timeoutMs = 8_000 } = {}) {
  const url = cricinfoMatchUrl(match, "ball-by-ball-commentary");
  if (!url) return { ok: false, reason: "no usable slug", status: null, bytes: 0 };
  try {
    const resp = await fetchImpl(url, { headers: HEADERS, signal: AbortSignal.timeout(timeoutMs) });
    const body = await readBounded(resp);
    if (body === null) return { ok: false, reason: "page too large", status: resp.status, bytes: MAX_PAGE_BYTES };
    const base = { status: resp.status, bytes: body.length };
    if (resp.status !== 200) return { ok: false, reason: `http ${resp.status}`, ...base };
    const parsed = parseCricinfoMatchPage(body);
    if (!parsed) return { ok: false, reason: "no data block", ...base };
    return { ok: true, ...base, ...parsed };
  } catch (e) {
    return { ok: false, reason: e?.message ?? "fetch failed", status: null, bytes: 0 };
  }
}

/**
 * Follow one live match from the live page to each tab this function reads, and say what came
 * back. The live page answering the container says nothing about a match page doing so.
 */
export async function probeCricinfoMatchPages({ fetchImpl = fetch, log = () => {}, timeoutMs = 15_000 } = {}) {
  const results = [];
  const get = async (url) => {
    const resp = await fetchImpl(url, { headers: HEADERS, signal: AbortSignal.timeout(timeoutMs) });
    return { status: resp.status, body: (await readBounded(resp)) ?? "" };
  };
  try {
    const live = await get(CRICINFO_LIVE_URL);
    const block = live.body.match(/<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
    const matches = block ? matchesIn(JSON.parse(block[1])) : [];
    const pick = matches.find((m) => m.state === "LIVE" && m.ballByBallSource) ?? matches.find((m) => m.state === "LIVE") ?? matches[0];
    if (!pick) return [{ name: "cricinfo-match", status: live.status, error: "no match on the live page" }];
    for (const tab of CRICINFO_TABS) {
      const url = cricinfoMatchUrl(pick, tab);
      if (!url) {
        results.push({ name: `cricinfo-${tab}`, status: null, error: "no usable slug" });
        continue;
      }
      const started = Date.now();
      const page = await get(url);
      const content = matchPageData(page.body)?.content;
      const result = {
        name: `cricinfo-${tab}`, matchId: String(pick.objectId), status: page.status, bytes: page.body.length, ms: Date.now() - started,
        comments: content?.comments?.length ?? null,
        overs: content?.innings?.[0]?.inningOvers?.length ?? null,
        batsmenPerformance: content?.batsmenPerformance?.length ?? null,
      };
      results.push(result);
      log(`[PROBE] ${JSON.stringify(result)}`);
    }
  } catch (e) {
    results.push({ name: "cricinfo-match", status: null, error: e?.message });
    log(`[PROBE] cricinfo-match threw: ${e?.message}`);
  }
  return results;
}

/** Matches stored per poll. Cricinfo forecasts a handful at once; a page claiming more is not trusted. */
export const CRICINFO_MAX_ROWS_PER_POLL = 12;
const MAX_TEXT = 120;

const text = (v) => (typeof v === "string" ? v.slice(0, MAX_TEXT) : null);
const num = (v) => (typeof v === "number" && Number.isFinite(v) ? v : null);

/**
 * A row reduced to what is stored, or null when it cannot be: the page is third-party content, and
 * its match id becomes part of a Storage file id. Only an all-digit id is accepted, every string is
 * bounded, and the prediction is kept as its four known numbers — nothing else from the page is
 * written through.
 */
export function storableRow(row) {
  const id = String(row?.cricinfoId ?? "");
  if (!/^\d{1,12}$/.test(id)) return null;
  const winProbability = num(row.predictions?.winProbability);
  if (winProbability === null || winProbability < 0 || winProbability > 100) return null;
  return {
    at: num(row.at),
    cricinfoId: id,
    slug: text(row.slug),
    format: text(row.format),
    state: text(row.state),
    startTime: text(row.startTime),
    teams: (Array.isArray(row.teams) ? row.teams : []).slice(0, 2).map((t) => ({
      id: num(t?.id), name: text(t?.name), score: text(t?.score), scoreInfo: text(t?.scoreInfo),
    })),
    liveInning: num(row.liveInning),
    liveOvers: num(row.liveOvers),
    statusText: text(row.statusText),
    winnerTeamId: num(row.winnerTeamId),
    predictions: {
      inningNumber: num(row.predictions.inningNumber),
      oversUnique: num(row.predictions.oversUnique),
      score: num(row.predictions.score),
      winProbability,
    },
  };
}

/** Stored samples plus a new row: one per innings and ball, oldest first, bounded. */
export function mergeCricinfo(stored, row) {
  const key = (r) => `${r?.predictions?.inningNumber}|${r?.predictions?.oversUnique}`;
  const byBall = new Map();
  for (const r of [...(Array.isArray(stored) ? stored : []), row]) if (r?.predictions) byBall.set(key(r), r);
  return [...byBall.values()].sort((a, b) => (a.at ?? 0) - (b.at ?? 0)).slice(-CRICINFO_MAX_SAMPLES);
}
