// What news-sync serves, separated from how it fetches, so the degradation rules are testable
// without standing up a Storage client.
//
// Between 2026-07-30 and 2026-08-05 the Cricbuzz RapidAPI plan answered 429 to every request. The
// function read its cache, found it stale, called upstream, got the 429 and returned it — throwing
// away the cached payload it was already holding. The device turned that into a blank News tab.
// Stale cricket headlines are worth more than nothing as long as the app says how old they are.

export const CACHE_TTL_MS = 4 * 60 * 60 * 1000; // 4 hours — caps RapidAPI at 6 calls/day

/** Age of a cached payload in ms, or null when `fetchedAt` is missing, unparseable or in the future. */
export function cacheAgeMs(cached, now) {
  const writtenAt = new Date(cached?.fetchedAt ?? NaN).getTime();
  if (!Number.isFinite(writtenAt)) return null;
  const age = now - writtenAt;
  return age >= 0 ? age : null;
}

/** True when a cached payload is present and still inside the TTL. */
export function isFresh(cached, now) {
  if (!hasPayload(cached)) return false;
  const age = cacheAgeMs(cached, now);
  return age !== null && age < CACHE_TTL_MS;
}

/**
 * The payload to serve when the live fetch produced nothing, or null when there is no cache to
 * fall back to and the caller must surface the real error instead.
 *
 * `_bouncerStale` is deliberately a separate flag from `_bouncerCacheAgeMs`. A cache whose
 * `fetchedAt` we cannot read is still worth serving, but claiming an age for it would be a guess —
 * and `JSON.stringify` turns a NaN age into `null`, which the client cannot tell apart from fresh.
 * The flag is what the client keys its banner off; the age only refines the wording.
 */
export function staleFallback(cached, now) {
  if (!hasPayload(cached)) return null;
  const ageMs = cacheAgeMs(cached, now);
  return {
    ...cached.response,
    _bouncerStale: true,
    ...(ageMs === null ? {} : { _bouncerCacheAgeMs: ageMs }),
  };
}

function hasPayload(cached) {
  const response = cached?.response;
  return !!response && typeof response === "object" && !Array.isArray(response);
}
