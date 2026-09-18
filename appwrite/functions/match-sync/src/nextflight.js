/**
 * Next.js flight-payload parsing.
 *
 * Cricbuzz serves App Router pages: there is no `__NEXT_DATA__` block, only a stream of
 * `self.__next_f.push([1, "<payload>"])` calls whose second element is a JSON fragment with a
 * short hex-and-colon prefix. Every server-side reader of a Cricbuzz page goes through this —
 * the match list, the live page, the scorecard and the profile pages — so it lives in one file.
 *
 * Vendored byte-identically into every function directory that needs it, for the same reason
 * `cricbuzz-http.js` is: `tools/deploy-function.mjs` builds each deployment with
 * `tar -C <that function's dir> .`, so nothing outside a function's own directory reaches the
 * container. `NextFlightVendorParityTest` fails the build if the copies drift.
 *
 * Port of `forEachNextFlightPayload` in `CricBuzzScraper.kt`.
 */

function parseLenient(s) {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

function normalizeNextFlightPayload(raw) {
  let normalized = String(raw).trim();
  const colonIndex = normalized.indexOf(":");
  if (colonIndex >= 1 && colonIndex <= 9) {
    const prefix = normalized.slice(0, colonIndex);
    if ([...prefix].every((c) => /[0-9a-f]/i.test(c))) {
      normalized = normalized.slice(colonIndex + 1);
    }
  }
  if (normalized.startsWith("I")) normalized = normalized.slice(1);
  normalized = normalized.trim();
  return normalized.startsWith("{") || normalized.startsWith("[") ? normalized : null;
}

function unwrapNextFlightPayload(scriptContent) {
  const el = parseLenient(scriptContent);
  if (!Array.isArray(el)) return null;
  if (el.length <= 1 || typeof el[1] !== "string") return null;
  return normalizeNextFlightPayload(el[1]);
}

function findMatchingParenEnd(html, openParenIdx) {
  let parenCount = 0;
  let inString = false;
  let escape = false;
  for (let i = openParenIdx; i < html.length; i++) {
    const ch = html[i];
    if (escape) {
      escape = false;
      continue;
    }
    if (ch === "\\") {
      escape = true;
      continue;
    }
    if (ch === '"') {
      inString = !inString;
      continue;
    }
    if (inString) continue;
    if (ch === "(") parenCount++;
    else if (ch === ")") {
      parenCount--;
      if (parenCount === 0) return i;
    }
  }
  return -1;
}

function forEachNextFlightPayload(html, cb) {
  const startMarker = "self.__next_f.push(";
  let startIndex = 0;
  while (true) {
    const idx = html.indexOf(startMarker, startIndex);
    if (idx === -1) break;
    const openParenIdx = idx + startMarker.length - 1;
    const endIdx = findMatchingParenEnd(html, openParenIdx);
    if (endIdx === -1) {
      startIndex = idx + 1;
      continue;
    }
    const scriptContent = html.substring(openParenIdx + 1, endIdx);
    const unwrapped = unwrapNextFlightPayload(scriptContent);
    if (unwrapped) {
      const parsed = parseLenient(unwrapped);
      if (parsed) cb(parsed);
    }
    startIndex = endIdx + 1;
  }
}

export { forEachNextFlightPayload };
