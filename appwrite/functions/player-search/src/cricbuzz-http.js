// Polite HTTP against www.cricbuzz.com: browser-shaped headers, rotated User-Agent, per-container
// request spacing, and a block-aware backoff.
//
// ══════════════════════════════════════════════════════════════════════════════════════════════
// VENDORED. The canonical copy is `match-sync/src/cricbuzz-http.js`; `poll-live-matches` carries a
// byte-identical duplicate. They are NOT symlinked and cannot be: `tools/deploy-function.mjs`
// builds each deployment with `tar -C <that function's dir> .`, so nothing outside a function's own
// directory reaches the container. `CricbuzzHttpVendorParityTest` fails the Android build when the
// two files stop matching — edit this one, then copy it across.
// ══════════════════════════════════════════════════════════════════════════════════════════════
//
// Every rule here was bought with a production incident, so read before relaxing any of it.

export const CRICBUZZ_BASE = "https://www.cricbuzz.com";

// Rotated per request, mirroring ScraperUserAgents.kt on the device. One static string identifies
// every request from this datacenter as the same client, which is half of what a bot filter keys on.
const USER_AGENTS = [
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
  "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
];

let uaCursor = 0;
// Round-robin rather than random: Math.random() is unavailable in some of this repo's execution
// contexts and a deterministic rotation is easier to reason about when reading logs.
const nextUserAgent = () => USER_AGENTS[uaCursor++ % USER_AGENTS.length];

/**
 * The header set a browser actually sends.
 *
 * The previous request carried `User-Agent` alone. `CricInfoScraper.connect()` on the device has
 * always sent the full set and is not blocked, while this function started collecting 403s
 * (ANDROID-35: four consecutive per-minute failures on 2026-08-04, self-recovering).
 *
 * Not to be confused with highlights-sync's `cricbuzz_http_429`: that gate calls
 * `cricbuzz-cricket.p.rapidapi.com`, a RapidAPI-proxied API with its own plan quota, and shares
 * nothing with this direct scrape of www.cricbuzz.com but the word "cricbuzz".
 */
function browserHeaders() {
  return {
    "User-Agent": nextUserAgent(),
    Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Cache-Control": "max-age=0",
    Referer: `${CRICBUZZ_BASE}/`,
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "same-origin",
    "Sec-Fetch-User": "?1",
    "Upgrade-Insecure-Requests": "1",
  };
}

// ── Politeness: spacing + block-aware backoff ───────────────────────────────
//
// syncMatch fetches a live page and a scorecard per match inside Promise.all, and main.js runs up
// to MAX_MATCHES of those inside Promise.allSettled — up to 24 requests leaving at once, every 60
// seconds, from one datacenter IP. The 403 burst started four minutes after a viewer raised one
// match from one fetch per 45 minutes to two per minute, which is what a rate limiter looks like.
//
// The spacing is per container and shared by every caller in it, which is the point: a second
// feature scraping the same host does not get its own budget. `poll-live-matches` walking the
// series archive queues behind its own live-match fetches rather than doubling the burst.

/** Minimum gap between two Cricbuzz requests from this container. */
export const MIN_REQUEST_GAP_MS = 350;
/** How long to stand down after the host says 403/429. */
export const BLOCK_BACKOFF_MS = 90_000;

let nextSlotAt = 0;
let blockedUntil = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Serialises callers onto MIN_REQUEST_GAP_MS slots. Concurrent callers queue instead of bursting. */
async function takeSlot(now = Date.now()) {
  const slot = Math.max(now, nextSlotAt);
  nextSlotAt = slot + MIN_REQUEST_GAP_MS;
  const wait = slot - now;
  if (wait > 0) await sleep(wait);
}

/** Thrown when the host is in its backoff window, so callers can tell it apart from a real failure. */
export class CricbuzzBlockedError extends Error {
  constructor(retryAfterMs) {
    super(`Cricbuzz backoff active — ${Math.round(retryAfterMs / 1000)}s remaining`);
    this.name = "CricbuzzBlockedError";
    this.retryAfterMs = retryAfterMs;
    this.blocked = true;
  }
}

/** Test seam: the module-scope clocks are the point in production. */
export function resetThrottleState() {
  nextSlotAt = 0;
  blockedUntil = 0;
  uaCursor = 0;
}

export function isBlockStatus(status) {
  return status === 403 || status === 429;
}

export async function fetchHtml(url, timeoutMs = 12_000) {
  const now = Date.now();
  if (now < blockedUntil) throw new CricbuzzBlockedError(blockedUntil - now);

  await takeSlot();
  const resp = await fetch(url, { headers: browserHeaders(), signal: AbortSignal.timeout(timeoutMs) });
  if (resp.ok) return resp.text();

  // Read the body on failure — permanently, not just while investigating. The 403 that started
  // this was diagnosed blind because fetchHtml threw at `!resp.ok` before anyone could see what
  // Cricbuzz actually said. A snippet is cheap and turns the next recurrence into a five-minute
  // question instead of a multi-day one.
  let snippet = "";
  try {
    snippet = (await resp.text()).replace(/\s+/g, " ").trim().slice(0, 300);
  } catch {
    snippet = "<body unreadable>";
  }

  if (isBlockStatus(resp.status)) {
    blockedUntil = Date.now() + BLOCK_BACKOFF_MS;
  }
  const err = new Error(`HTTP ${resp.status} from ${url} — body: ${snippet}`);
  err.status = resp.status;
  err.bodySnippet = snippet;
  err.blocked = isBlockStatus(resp.status);
  throw err;
}
