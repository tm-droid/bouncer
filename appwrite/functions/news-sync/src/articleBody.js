// Article body extraction, separated from the fetch so the shape handling is testable without a
// network call or a Storage client.
//
// The device used to scrape www.cricbuzz.com for this text on every article tap. That scrape broke
// silently when Cricbuzz renamed the body container from `gap-6` to `gap-4`: every selector missed,
// and the fallback returned a hardcoded "Unable to load article content" line that rendered as body
// text. Moving it here puts the body behind a permanent cache and a single parser that a fixture
// test can pin.

/** File id for one story's cached body. Stories are immutable, so this never expires. */
export const bodyFileId = (storyId) => `news-body-${storyId}`;

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
