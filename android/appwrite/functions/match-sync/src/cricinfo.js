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
 * notes are delimited by square brackets.
 *
 * It is an allowlist, and that is the point. The versions before it removed what was known to be
 * dangerous — brackets, control characters, a list of label words — and each was shown a case its
 * list had missed. A sentence about a delivery needs letters, digits, spaces and a little
 * punctuation; everything else becomes a space. No colon survives, so nothing can be a label; no
 * bracket, so nothing can be a tag or close a note; no line break, so nothing can be a second
 * item. `PROSE_RE` is the one definition: the cleaner replaces what is outside it and the final
 * test is that the result is inside it, so the two cannot disagree.
 *
 * Returns "" for text whose entities would not settle. What a model makes of the words that
 * remain is the checker's to judge.
 */
export function promptSafe(value, max = MAX_BALL_TEXT) {
  // Bounded before the passes below, not after: each is linear, but in a third party's length.
  let out = String(value ?? "").slice(0, 8 * max);
  // Entities to a fixed point — "&amp;lt;" is a tag two rounds from now — so that the markup is
  // gone before the text is read. Closed, not open: text that will not settle is not sent.
  let stable = false;
  for (let round = 0; round < MAX_SAFE_ROUNDS && !stable; round++) {
    const before = out;
    out = out
      .replace(/&#x([0-9a-f]{1,6});|&#(\d{1,7});/gi, (_m, hex, dec) => {
        const code = hex ? parseInt(hex, 16) : Number(dec);
        return code > 0 && code <= 0x10ffff && !(code >= 0xd800 && code <= 0xdfff) ? String.fromCodePoint(code) : " ";
      })
      .replace(/&amp;/gi, "&").replace(/&nbsp;/gi, " ").replace(/&rsquo;|&lsquo;|&apos;/gi, "'").replace(/&quot;|&ldquo;|&rdquo;/gi, '"')
      .replace(/&lt;/gi, "<").replace(/&gt;/gi, ">");
    stable = out === before;
  }
  if (!stable) return "";
  out = out
    .replace(/<[^>]*>/g, " ")
    // Compatibility forms become the characters a model would read them as, BEFORE the allowlist.
    .normalize("NFKC")
    // By code point (the `u` flag), so half a surrogate pair is one disallowed character.
    .replace(NOT_PROSE_RE, " ")
    .replace(/\s+/g, " ")
    .trim();
  // Cut on a code point: half a pair is not valid UTF-8, a provider refuses the request, and the
  // request is a batch of twelve deliveries.
  out = Array.from(out).slice(0, max).join("").trim();
  return PROSE_RE.test(out) ? out : "";
}
const MAX_SAFE_ROUNDS = 6;
// Latin letters, plain and accented; the ten digits; the space; the punctuation of a plain
// sentence. Named ranges, not Unicode categories: `\p{L}` is every script there is, which lets in
// a letter that only looks like another, and `\p{M}` is marks that stack without limit. Cricinfo
// writes in English, and a name outside these ranges costs that delivery its second report.
const LATIN = "A-Za-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u024F";
const PROSE_CLASS = `${LATIN}0-9 .,'’"!?()/%&+;\\-–—`;
const PROSE_RE = new RegExp(`^[${PROSE_CLASS}]*$`, "u");
const NOT_PROSE_RE = new RegExp(`[^${PROSE_CLASS}]`, "gu");

const plainText = (items) =>
  promptSafe((Array.isArray(items) ? items : []).slice(0, 6).map((i) => (typeof i?.html === "string" ? i.html.slice(0, 4 * MAX_BALL_TEXT) : "")).join(" "));

// A header is two people's names and the word between them; a dismissal is a scorecard entry.
// Neither is prose, so each must BE its shape, exactly as it arrived: nothing is cleaned into one.
const HEADER_RE = new RegExp(`^[${LATIN}][${LATIN} .'’-]{0,48} to [${LATIN}][${LATIN} .'’-]{0,48}$`, "u");
const DISMISSAL_RE = new RegExp(`^[${LATIN}†(][${LATIN}0-9 †()&.,'’/-]{0,78}$`, "u");
const HOW_RE = /^[a-z][a-z ]{1,30}$/;
const TEAM_CODE_RE = /^[A-Za-z0-9-]{1,10}$/;
const shaped = (value, shape) => {
  if (typeof value !== "string" || value.length > 200) return null;
  const v = value.normalize("NFKC").trim();
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
  const title = shaped(comment.title, HEADER_RE);
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
    dismissal: comment.isWicket === true ? shaped(comment.dismissalText?.long, DISMISSAL_RE) : null,
    how: comment.isWicket === true ? shaped(comment.dismissalText?.short, HOW_RE) : null,
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

// ── What the page says about the match itself ────────────────────────────────

const PERSON_RE = new RegExp(`^[${LATIN}][${LATIN} .'’-]{1,48}$`, "u");
const REVIEWS_RE = /^[A-Za-z0-9-]{1,10} - \d of \d(?:, [A-Za-z0-9-]{1,10} - \d of \d)?$/;
const people = (list, max) =>
  (Array.isArray(list) ? list : []).slice(0, max).map((u) => shaped(u?.player?.longName, PERSON_RE)).filter(Boolean);

/**
 * The officials, the players on debut and the reviews each side has left, or null when the page
 * names none of them. Shown on the Info tab as Cricinfo states them; every string must be the
 * shape of what it claims to be, or it is left out.
 */
export function matchFacts({ match, content } = {}) {
  const facts = {
    umpires: people(match?.umpires, 2),
    tvUmpires: people(match?.tvUmpires, 2),
    referees: people(match?.matchReferees, 2),
    debutants: (Array.isArray(match?.debutPlayers) ? match.debutPlayers : []).slice(0, 12)
      .map((d) => ({ team: shaped(d?.team?.abbreviation, TEAM_CODE_RE), name: shaped(d?.player?.longName, PERSON_RE) }))
      .filter((d) => d.team && d.name),
    reviews: shaped(content?.supportInfo?.liveSummary?.reviewsShortText, REVIEWS_RE),
  };
  const empty = !facts.reviews && [facts.umpires, facts.tvUmpires, facts.referees, facts.debutants].every((l) => l.length === 0);
  return empty ? null : facts;
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
    const team = shaped(inn.team?.abbreviation, TEAM_CODE_RE);
    for (const o of (Array.isArray(inn.inningOvers) ? inn.inningOvers : []).slice(0, MAX_OVERS_PER_INNINGS)) {
      const point = overPoint(o, innings, team);
      if (point) overs.push(point);
    }
  }
  return { balls, overs, facts: matchFacts(data) };
}

const nameWords = (name) => String(name ?? "").toLowerCase().split(/[^a-z]+/).filter((w) => w.length > 2);
// Cricinfo's header is a short name and not always the surname ("Arshdeep" for Arshdeep Singh,
// "Hosein" for Akeal Hosein), so one shared word is the test.
//
// The two sites can also shorten one name to different halves: Cricbuzz wrote "Ahmadzai" and
// Cricinfo "Yamin" for Yamin Ahmadzai, so every ball he bowled read as a different bowler and lost
// its second report (ANDROID-5C). `names` is the full names this match's Cricbuzz pages carry —
// the crease, the bowler, the scorecard — and two short names that are both part of one of them
// are one player. With no such name the test is the shared word, as before.
const sameName = (a, b, names = []) => {
  const first = nameWords(a);
  const second = nameWords(b);
  if (first.some((w) => second.includes(w))) return true;
  return names.some((full) => {
    const words = nameWords(full);
    return first.some((w) => words.includes(w)) && second.some((w) => words.includes(w));
  });
};

/**
 * Whether Cricinfo's delivery and Cricbuzz's are the same event, or the reason they are not.
 *
 * Two sites keyed to one ball label can still be describing different things: a correction one
 * has published and the other has not, or a label that drifted after an umpire's recount. A
 * second report that disagrees on who faced, on the runs or on a wicket is not a second report
 * of this ball, and is left out.
 *
 * @param cb `{ clean, runs, wicket }` — the Cricbuzz line and what its outcome clause says
 * @param names full player names known for this match, for two sites' different short names
 */
export function disagreement(cb, ball, names = []) {
  const header = String(cb?.clean ?? "").match(/^(.+?) to ([^,]+),/);
  const title = String(ball?.title ?? "").match(/^(.+?) to (.+)$/);
  if (!header || !title) return "unreadable header";
  if (!sameName(header[1], title[1], names)) return "bowler";
  if (!sameName(header[2], title[2], names)) return "batter";
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

// ── Where a batter's runs went ───────────────────────────────────────────────
//
// The statistics page carries, for the batters AT THE CREASE and nobody else, their runs split
// over eight zones of the ground. It is a snapshot of an innings in progress, so each one says
// how many runs and balls it covers and the caller keeps the latest per batter.
//
// The zones are octants of the field as Cricinfo draws it, numbered clockwise from the top, with
// the bowler's end at the BOTTOM: measured 2026-10-07 against per-ball coordinates and the
// commentary's own words on match 1529230 — "drilled straight back down the ground" is zone 4,
// at the bottom; a left-hander's pull "over deep backward square leg" is zone 7, upper left. They
// are the ground's geometry, not mirrored for a left-hander.

export const WAGON_ZONES = 8;
const MAX_BATTER_RUNS = 500;

/** One batter's snapshot, or null unless its zones are whole numbers that add up to the runs. */
export function wagonOf(entry) {
  const innings = int(entry?.inningNumber);
  const runs = int(entry?.runs);
  const balls = int(entry?.balls);
  const name = shaped(entry?.player?.longName, PERSON_RE);
  const zones = Array.isArray(entry?.wagonData) ? entry.wagonData : [];
  if (!innings || innings < 1 || innings > 4 || !name || runs === null || runs < 0 || runs > MAX_BATTER_RUNS) return null;
  if (balls === null || balls < 0 || balls > 2000 || zones.length !== WAGON_ZONES) return null;
  if (!zones.every((z) => Number.isInteger(z) && z >= 0 && z <= MAX_BATTER_RUNS)) return null;
  // The page's own arithmetic is the check that the eight numbers mean what this says they do.
  if (zones.reduce((a, b) => a + b, 0) !== runs) return null;
  return { innings, name, runs, balls, zones: [...zones] };
}

/** The statistics page's wagon snapshots: the batters at the crease, at most a handful. */
export function parseCricinfoStats(html) {
  const data = matchPageData(html);
  if (!data) return null;
  const list = Array.isArray(data.content.batsmenPerformance) ? data.content.batsmenPerformance : [];
  return { wagon: list.slice(0, 6).map(wagonOf).filter(Boolean) };
}

/** One match page. `ok` false says why; nothing here throws. */
export async function fetchCricinfoMatch(match, { fetchImpl = fetch, timeoutMs = 8_000, tab = "ball-by-ball-commentary", parse = parseCricinfoMatchPage } = {}) {
  const url = cricinfoMatchUrl(match, tab);
  if (!url) return { ok: false, reason: "no usable slug", status: null, bytes: 0 };
  try {
    const resp = await fetchImpl(url, { headers: HEADERS, signal: AbortSignal.timeout(timeoutMs) });
    const body = await readBounded(resp);
    if (body === null) return { ok: false, reason: "page too large", status: resp.status, bytes: MAX_PAGE_BYTES };
    const base = { status: resp.status, bytes: body.length };
    if (resp.status !== 200) return { ok: false, reason: `http ${resp.status}`, ...base };
    const parsed = parse(body);
    if (!parsed) return { ok: false, reason: "no data block", ...base };
    return { ok: true, ...base, ...parsed };
  } catch (e) {
    return { ok: false, reason: e?.message ?? "fetch failed", status: null, bytes: 0 };
  }
}

/** The statistics page of a match, through the same bounded fetch. */
export const fetchCricinfoStats = (match, options = {}) =>
  fetchCricinfoMatch(match, { ...options, tab: "match-statistics", parse: parseCricinfoStats });

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
