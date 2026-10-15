// How many match-commentary rows the end-of-cycle prune may delete, as pure functions.
//
// A bulk delete bills one write per row (measured 2026-10-14: 1,676 rows, 1,676 writes), and the
// Free plan has 250k writes a cycle. So the prune runs once, in the last hours of a billing cycle,
// and spends most of what is left. What is left cannot be read: no API key can read usage (probed
// 2026-10-15: `/project/usage` 404s, `/tablesdb/usage` resolves as a database named "usage", and an
// organization key cannot be given the `teams.read` org usage needs). It is estimated instead, from
// the match archive's count of commentary rows created this cycle (`manifest.json` `cycle`), which
// matched Appwrite's own total exactly when checked (4,775 of 4,775).

export const WRITE_LIMIT = 250_000;
// Commentary creates were 66% of all writes and the prune's deletes 28% (2026-10-14); the rest,
// detail and scorecard PATCHes, ~6%, is ~9% on top of the creates. 15% leaves headroom for the
// highlights and player-profile writes nobody measured. Check it against the Console each cycle.
export const OTHER_WRITES_FACTOR = 1.15;
// The share of the estimated remainder spent; the rest absorbs an estimate that ran low.
export const SPEND_SHARE = 0.9;
// Commentary keeps being written while the prune runs; a busy evening is ~2,000 rows.
export const WINDOW_RESERVE = 5_000;
// The prune runs in the cycle's last twelve hours and stops a quarter of an hour before its end.
export const WINDOW_MS = 12 * 3_600_000;
export const WINDOW_STOP_MS = 15 * 60_000;
// The archive publishes every three hours; a count older than this may be missing a busy stretch.
export const MANIFEST_MAX_AGE_MS = 4 * 3_600_000;

/** Whether `now` is inside the prune window of the cycle the manifest names. */
export function inWindow(cycle, now) {
  const end = Date.parse(cycle?.end ?? "");
  if (!Number.isFinite(end)) return false;
  return now >= end - WINDOW_MS && now < end - WINDOW_STOP_MS;
}

/** The whole cycle's prune budget, fixed when the window opens: 90% of the estimated remainder. */
export function cycleBudget(commentaryRows) {
  const used = Math.ceil(Math.max(0, commentaryRows) * OTHER_WRITES_FACTOR);
  return Math.max(0, Math.floor(SPEND_SHARE * (WRITE_LIMIT - used - WINDOW_RESERVE)));
}

/**
 * What this run does: `{action: "skip", reason}` or `{action: "prune", state, remaining, cutoff}`.
 *
 * `state` is the prune's own record (`{cycleStart, budget, pruned}`), kept so that each run in the
 * window spends from one budget rather than 90% of a remainder it keeps recomputing, which would
 * creep to the limit. A new cycle starts a new record.
 */
export function planRun({ manifest, state, now }) {
  const cycle = manifest?.cycle;
  if (!cycle?.start || !Number.isFinite(cycle.commentaryRows)) return { action: "skip", reason: "no cycle count in the archive" };
  if (!inWindow(cycle, now)) return { action: "skip", reason: "outside the end-of-cycle window" };
  const age = now - Date.parse(manifest.updated ?? "");
  if (!(age <= MANIFEST_MAX_AGE_MS)) return { action: "skip", reason: "the archive's count is stale" };
  if (!manifest.watermark) return { action: "skip", reason: "no archive watermark" };
  const current =
    state?.cycleStart === cycle.start
      ? state
      : { cycleStart: cycle.start, budget: cycleBudget(cycle.commentaryRows), pruned: 0 };
  const remaining = Math.max(0, current.budget - current.pruned);
  if (remaining === 0) return { action: "skip", reason: "this cycle's budget is spent", state: current };
  return { action: "prune", state: current, remaining, cutoff: manifest.watermark };
}

/**
 * Every final match id in the archive's month indexes, oldest month first, so the oldest rows go
 * first. `months` is `{month: rows}` as the archive publishes them (rows newest first).
 */
export function finalIdsOldestFirst(months) {
  const ids = [];
  const keys = Object.keys(months).sort((a, b) => (a === "unknown" ? -1 : b === "unknown" ? 1 : a.localeCompare(b)));
  for (const month of keys) {
    for (const row of [...(months[month] ?? [])].reverse()) {
      const id = String(row?.[0] ?? "");
      if (/^\d{1,12}$/.test(id)) ids.push(id);
    }
  }
  return [...new Set(ids)];
}
