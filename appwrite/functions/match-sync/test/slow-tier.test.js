import test from "node:test";
import assert from "node:assert/strict";
import { selectSlowTier, nextSlowState, commentaryCapFor, isFinalised, finalKey } from "../src/main.js";

// READ_BUDGET_PER_RUN = 20, READ_HEADROOM_RESERVE = 5, READ_COST_PER_MATCH = 1,
// SLOW_MAX_MATCHES_PER_RUN = 3, SLOW_TIER_INTERVAL_MS = 15 min, SLOW_MAX_NEW_PER_MATCH = 2.
// Mirrored here rather than exported: a test that reads the constant it is checking cannot catch
// a wrong constant. The interval and the per-pass cap are budget decisions against the LLM chain's
// daily request budget, not free parameters — see the derivation above the constants in main.js.
// Re-derived 2026-09-16 when three Gemini rungs were believed to take that budget from ~1000/day to
// ~2500. That figure was never measured: two of the three rungs are 20 RPD (2026-09-24), so the real
// Gemini supply is ~540/day. The constants below still stand; the headroom they assume does not.
const NOW = 1_785_500_000_000;
const INTERVAL = 15 * 60 * 1000;
const MAX_SLOW_PER_RUN = 3;
const USABLE_FAST_SLOTS = 15; // 20 budget − 5 reserve

const matches = (...ids) => ids.map((id) => ({ id }));
const select = (over) =>
  selectSlowTier({ live: matches("1"), viewed: new Set(), state: {}, now: NOW, ...over });

test("a match with no state entry is due on its first sighting, not one interval later", () => {
  // The whole point of the tier is covering matches nobody watches. Seeding "now" on discovery
  // would mean the first three quarters of an hour of every such match is lost for good.
  assert.deepEqual(select({ live: matches("144783"), state: {} }), ["144783"]);
  assert.deepEqual(select({ live: matches("144783"), state: undefined }), ["144783"]);
  assert.deepEqual(select({ live: matches("144783"), state: { "999": NOW } }), ["144783"]);
});

test("the interval holds a match back until it elapses", () => {
  const live = matches("144783");
  assert.deepEqual(select({ live, state: { "144783": NOW - 5 * 60 * 1000 } }), []);
  // 5 and 10 minutes are the regressions that matter now: each halving of the interval doubles the
  // daily LLM spend, and the ladder is a finite daily budget (far below the ~2500 once assumed).
  assert.deepEqual(select({ live, state: { "144783": NOW - 10 * 60 * 1000 } }), []);
  assert.deepEqual(select({ live, state: { "144783": NOW - INTERVAL + 1 } }), []);
  assert.deepEqual(select({ live, state: { "144783": NOW - INTERVAL } }), ["144783"]);
  assert.deepEqual(select({ live, state: { "144783": NOW - 16 * 60 * 1000 } }), ["144783"]);
});

test("a match with a viewer belongs to the fast tier and is never taken by the slow one", () => {
  const live = matches("a", "b");
  assert.deepEqual(select({ live, viewed: new Set(["a"]) }), ["b"]);
  assert.deepEqual(select({ live, viewed: new Set(["a", "b"]) }), []);
});

test("the fast tier's read budget is reserved before the slow tier gets any", () => {
  const live = matches(...Array.from({ length: 10 }, (_, i) => `m${i}`));
  const viewers = (n) => new Set(Array.from({ length: n }, (_, i) => `v${i}`));

  // One read short of the reserve: still room for slow matches.
  assert.equal(select({ live, viewed: viewers(USABLE_FAST_SLOTS - 1) }).length, 1);
  // Exactly at the reserve: the tier shuts off rather than eating into it.
  assert.deepEqual(select({ live, viewed: viewers(USABLE_FAST_SLOTS) }), []);
  // A busy night: every slot claimed, nothing left over, and no negative-headroom weirdness.
  assert.deepEqual(select({ live, viewed: viewers(USABLE_FAST_SLOTS + 8) }), []);
});

test("a latched read-budget 402 stops the slow tier entirely", () => {
  const live = matches("a", "b");
  assert.deepEqual(select({ live, cooldownUntil: NOW + 60_000 }), []);
  assert.deepEqual(select({ live, cooldownUntil: NOW }), ["a", "b"]);
});

test("no more than SLOW_MAX_MATCHES_PER_RUN are taken in one run", () => {
  const live = matches("a", "b", "c", "d", "e", "f");
  const picked = select({ live });
  assert.equal(picked.length, MAX_SLOW_PER_RUN);
  assert.deepEqual(picked, ["a", "b", "c"], "due order, so the same few are not always starved");
});

test("the clock is stamped only for matches that ran, and pruned to the live set", () => {
  const state = { ended: NOW - 90 * 60 * 1000, still_live: NOW - INTERVAL, other: NOW };
  const next = nextSlowState({
    state,
    processed: new Set(["still_live"]),
    liveIds: new Set(["still_live", "other"]),
    now: NOW,
  });
  assert.equal(next.still_live, NOW, "a processed match restarts its interval clock");
  assert.equal(next.other, NOW, "an untouched live match keeps its previous stamp");
  assert.equal("ended" in next, false, "a finished match must not accumulate in the file forever");
});

test("a match that just ran is not due again on the next run", () => {
  const live = matches("144783");
  const after = nextSlowState({
    state: {},
    processed: new Set(["144783"]),
    liveIds: new Set(["144783"]),
    now: NOW,
  });
  assert.deepEqual(select({ live, state: after, now: NOW + 60_000 }), []);
  assert.deepEqual(select({ live, state: after, now: NOW + INTERVAL }), ["144783"]);
});

test("the finalised marker rides in the same file without disturbing the tier", () => {
  // It shares the flat { key: epochMs } map so selectSlowTier, which only reads bare ids, needs no
  // change. If the suffixed key ever became visible to it, a finalised match would look like one
  // that had just had a slow pass and would stop being selected.
  const state = nextSlowState({
    state: {},
    processed: new Set(),
    liveIds: new Set(["144783"]),
    now: NOW,
    finalised: new Set(["144783"]),
  });
  assert.equal(isFinalised(state, "144783"), true);
  assert.equal(isFinalised(state, "999"), false);
  assert.equal(state["144783"], undefined, "finalising is not a slow pass");
  assert.deepEqual(
    select({ live: matches("144783"), state, now: NOW }),
    ["144783"],
    "the suffixed key must be invisible to the tier"
  );
});

test("the marker lives exactly as long as the match stays in the live list", () => {
  // Long enough to stop the finalisation repeating every minute — and on every cold container,
  // which is why it is persisted at all — and gone once the match can never sync again.
  const state = { [finalKey("144783")]: NOW - 60_000, [finalKey("ended")]: NOW - 60_000 };
  const next = nextSlowState({
    state,
    processed: new Set(),
    liveIds: new Set(["144783"]),
    now: NOW,
  });
  assert.equal(isFinalised(next, "144783"), true, "still listed live, so still finalised");
  assert.equal(isFinalised(next, "ended"), false, "left the list, so the marker goes with it");
});

test("the cap encodes the tier: 10 for a viewer, 2 for the slow tier, 0 otherwise", () => {
  const viewed = new Set(["watched"]);
  const slow = new Set(["unwatched"]);
  assert.equal(commentaryCapFor("watched", viewed, slow), 10, "fast tier is unchanged");
  // Two rows a pass. Each row is one LLM call, so this number times the pass rate times the live
  // match count is the daily spend — at 11 concurrent unwatched matches and a 15-minute interval,
  // 2 is ~1400/day and 3 would be ~2100 — sized against a ~2500/day ladder that turned out to be
  // ~540 Gemini + groq's token cap. It is a budget, not a taste.
  assert.equal(commentaryCapFor("unwatched", viewed, slow), 2);
  assert.equal(commentaryCapFor("idle", viewed, slow), 0, "0 means win-probability only, no writes");
  // A viewer arriving on a slow-tier match must promote it, never demote the fast tier.
  assert.equal(commentaryCapFor("both", new Set(["both"]), new Set(["both"])), 10);
});
