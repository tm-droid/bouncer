// Article body extraction, separated from the fetch so the shape handling is testable without a
// network call or a Storage client.
//
// The device used to scrape www.cricbuzz.com for this text on every article tap. That scrape broke
// silently when Cricbuzz renamed the body container from `gap-6` to `gap-4`: every selector missed,
// and the fallback returned a hardcoded "Unable to load article content" line that rendered as body
// text. Moving it here puts the body behind a permanent cache and a single parser that a fixture
// test can pin.

/** File id for one story's cached body. */
export const bodyFileId = (storyId) => `${BODY_ID_PREFIX}${storyId}`;

/**
 * The prefix every cached body carries, and the ONLY safe way to select one for deletion.
 *
 * `cricket-stats` is a shared bucket. Alongside the bodies it holds `news-cache`, `team-stats`,
 * `h2h-stats`, `active-series`, `player-index`, `live-matches`, `live-winprob`, `slow-sync-state`,
 * `active-viewers`, `poll-state`, `recent-matches`, `upcoming-matches`, `live-count` and
 * `rate-limit-offenders` — every one of them load-bearing for a different feature, and a wrong
 * delete here is a silent outage somewhere else.
 *
 * Measured against the live bucket on 2026-09-12: `startsWith($id, "news-body-")` returns exactly
 * the bodies, while `search=news-body` ALSO returns `news-cache`. Storage search is fuzzy. Select
 * with `startsWith` on `$id`, and re-check the prefix on every candidate before deleting it.
 */
export const BODY_ID_PREFIX = "news-body-";

/** How long a cached body is kept. Matches `match-commentary-prune`'s retention, for the same
 *  reason: the data is cheap to re-fetch (one RapidAPI call, which re-caches it) and interest in a
 *  news story decays. Retention here is a cost knob, never a correctness one. */
export const BODY_RETENTION_MS = 14 * 24 * 60 * 60 * 1000;

/** Most deletions one sweep will do. A backlog drains over several sweeps, as in the commentary
 *  prune — a sweep is a guest on a news request and must not become the slow part of it. */
export const BODY_PRUNE_MAX = 50;

/**
 * Which of `files` to delete, given the clock.
 *
 * Pure so the rule that protects every other file in the bucket is testable without a Storage
 * client. Three independent conditions, and a file must pass all of them:
 *
 *   1. its `$id` really starts with [BODY_ID_PREFIX] — belt and braces behind the query, because a
 *      query-semantics surprise must not be able to become a deletion;
 *   2. its `$createdAt` parses — an unreadable age is NOT an old age, and defaulting the other way
 *      would delete a file precisely when we understand it least;
 *   3. it is older than [BODY_RETENTION_MS].
 */
export function selectBodiesToPrune(files, now, {
  retentionMs = BODY_RETENTION_MS,
  max = BODY_PRUNE_MAX,
} = {}) {
  return (Array.isArray(files) ? files : [])
    .filter((f) => typeof f?.$id === "string" && f.$id.startsWith(BODY_ID_PREFIX))
    .filter((f) => {
      const createdAt = new Date(f?.$createdAt ?? NaN).getTime();
      return Number.isFinite(createdAt) && now - createdAt > retentionMs;
    })
    .slice(0, max);
}

/**
 * Ordered body paragraphs from a Cricbuzz `news/v1/detail/{id}` payload.
 *
 * Shape tolerance is deliberate. The response wraps each block as
 * `{ content: { contentType, contentValue } }`, and `contentValue` has been observed both as a
 * plain string and as `{ content: "..." }`. Non-text blocks (images, ads, embedded scorecards)
 * carry no prose and are skipped by `contentType`, not by guessing at their fields.
 *
 * Returns [] rather than throwing for an unrecognised payload — the caller distinguishes "no body"
 * from "upstream unavailable", and conflating them is what the 503 rule below exists to prevent.
 */
export function extractParagraphs(detail) {
  const blocks = Array.isArray(detail?.content) ? detail.content : [];
  return blocks
    .map((block) => textOf(block?.content))
    .filter((text) => text.length > 0);
}

function textOf(content) {
  if (!content || content.contentType !== "text") return "";
  const value = content.contentValue;
  const raw = typeof value === "string" ? value : value?.content;
  return typeof raw === "string" ? raw.replace(/\s+/g, " ").trim() : "";
}

/**
 * The top-level keys of an unrecognised payload, for the log line.
 *
 * The same reasoning as `fetchHtml`'s body snippet in match-sync: an extractor that returns nothing
 * and says nothing about what it was given turns the next upstream format change into a multi-day
 * question. Keys only — the body itself can be several KB and is not worth a log entry.
 */
export function shapeHint(detail) {
  if (!detail || typeof detail !== "object") return typeof detail;
  const keys = Object.keys(detail).slice(0, 12).join(",");
  const blocks = Array.isArray(detail.content)
    ? detail.content.map((b) => b?.content?.contentType ?? "?").slice(0, 12).join(",")
    : "content:not-an-array";
  return `keys=[${keys}] blocks=[${blocks}]`;
}
