import { test } from "node:test";
import assert from "node:assert/strict";
import { syncSeries } from "../src/poll.js";

const noop = () => {};
const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;

// A storage double that counts downloads — the whole point of syncSeries' freshness check is that
// a one-minute run which has nothing to do downloads nothing.
function fakeStorage(file) {
  const calls = { downloads: 0, creates: 0 };
  return {
    calls,
    getFileDownload: async () => {
      calls.downloads++;
      if (!file) throw Object.assign(new Error("not found"), { code: 404 });
      return file;
    },
    deleteFile: async () => {},
    createFile: async () => { calls.creates++; },
  };
}

const series = [{ id: "1", name: "Some Series 2026" }];
const fetchActive = async () => series;
const fetchPage = async () => "";

test("a fresh series clock in poll-state costs zero downloads", async () => {
  const storage = fakeStorage({ data: series, fetchedAt: 0 });
  const now = 10 * DAY;
  const prevState = { seriesFetchedAt: now - HOUR };
  const nextState = { ...prevState };
  const outcome = await syncSeries({ storage, prevState, nextState, now, log: noop, error: noop, fetchActive, fetchPage });
  assert.equal(outcome, "fresh");
  assert.equal(storage.calls.downloads, 0);
});

test("a legacy state file adopts the corpus fetchedAt once instead of re-scraping", async () => {
  const now = 10 * DAY;
  const storage = fakeStorage({ data: series, fetchedAt: now - HOUR });
  const prevState = {};
  const nextState = {};
  const outcome = await syncSeries({ storage, prevState, nextState, now, log: noop, error: noop, fetchActive, fetchPage });
  assert.equal(outcome, "adopted");
  assert.equal(nextState.seriesFetchedAt, now - HOUR);
  assert.equal(storage.calls.creates, 0);

  // The next run, reading that state, does not download again.
  const again = fakeStorage({ data: series, fetchedAt: now - HOUR });
  await syncSeries({ storage: again, prevState: nextState, nextState: { ...nextState }, now: now + 60_000, log: noop, error: noop, fetchActive, fetchPage });
  assert.equal(again.calls.downloads, 0);
});

test("a due refresh writes the corpus and records the clock", async () => {
  const now = 10 * DAY;
  const storage = fakeStorage({ data: [], fetchedAt: now - 2 * DAY });
  const prevState = { seriesFetchedAt: now - 2 * DAY };
  const nextState = { ...prevState };
  const outcome = await syncSeries({ storage, prevState, nextState, now, log: noop, error: noop, fetchActive, fetchPage });
  assert.equal(outcome, "updated");
  assert.equal(storage.calls.creates, 1);
  assert.equal(nextState.seriesFetchedAt, now);
});

test("a failed refresh backs off instead of downloading the corpus every minute", async () => {
  const now = 10 * DAY;
  const storage = fakeStorage(null);
  const prevState = { seriesFetchedAt: now - 2 * DAY };
  const nextState = { ...prevState };
  const failing = async () => { throw new Error("blocked"); };
  const outcome = await syncSeries({ storage, prevState, nextState, now, log: noop, error: noop, fetchActive: failing, fetchPage });
  assert.equal(outcome, "error");
  assert.equal(nextState.seriesRetryAt, now + HOUR);

  const later = fakeStorage(null);
  const retried = await syncSeries({ storage: later, prevState: nextState, nextState: { ...nextState }, now: now + 60_000, log: noop, error: noop, fetchActive: failing, fetchPage });
  assert.equal(retried, "fresh");
  assert.equal(later.calls.downloads, 0);
});
