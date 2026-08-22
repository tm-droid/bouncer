// Decoding for Appwrite Storage downloads.
//
// node-appwrite 15 inspects the file's mimeType: an `application/json` file comes back already
// parsed, as a plain object, while anything else arrives as bytes. Every reader in this repo was
// written against the bytes case and called `Buffer.from(raw)` unconditionally, which throws
//
//   The first argument must be of type string or an instance of Buffer, ArrayBuffer, or Array
//   or an Array-like Object. Received an instance of Object
//
// Each of those call sites sits inside a catch that treats any failure as "file missing", so the
// throw was invisible and permanently disabled whatever the file gated:
//
//   - match-sync's viewer gate saw no viewers ever, so no match got a scorecard, AI commentary or
//     a match-live-detail row.
//   - win-probability never loaded the trained model and always answered from the heuristic.
//   - news-sync never got a cache hit and called RapidAPI on every invocation, defeating the 4-hour
//     TTL that keeps it inside quota.
//   - poll-live-matches re-fetched the series list every minute and read the previous live count as
//     0, so "the count changed" was true on every run.
//   - match-presence started from an empty map on every ping, so each viewer erased all the others.
//
// Duplicated into each function because Appwrite deploys every function as an isolated archive with
// no shared package. A test asserts the copies are byte-identical so they cannot drift.

/** True for the byte-ish shapes a download can take: Buffer, ArrayBuffer, or a typed-array view. */
function isBytes(raw) {
  return Buffer.isBuffer(raw) || raw instanceof ArrayBuffer || ArrayBuffer.isView(raw);
}

/**
 * Parsed contents of a downloaded JSON file, whichever form the SDK returned.
 * Throws for genuinely malformed bytes — callers decide what a parse failure means.
 */
export function parseJsonFile(raw) {
  if (raw == null) return null;
  if (typeof raw === "string") return JSON.parse(raw);
  if (isBytes(raw)) return JSON.parse(Buffer.from(raw).toString("utf8"));
  if (typeof raw === "object") return raw;
  throw new TypeError(`Unsupported download type: ${typeof raw}`);
}

/** Text contents of a downloaded file, for the files that are not JSON. */
export function fileText(raw) {
  if (raw == null) return "";
  if (typeof raw === "string") return raw;
  if (isBytes(raw)) return Buffer.from(raw).toString("utf8");
  return JSON.stringify(raw);
}
