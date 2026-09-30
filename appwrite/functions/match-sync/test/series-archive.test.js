import { test } from "node:test";
import assert from "node:assert/strict";
import { mergeArchiveSeries, parseSeriesAnchors } from "../src/poll.js";
import { CricbuzzBlockedError } from "../src/cricbuzz-http.js";

const noop = () => {};

// Archive markup: the name lives in `title` with a " Matches <dates>" tail, and the anchor wraps
// nested divs. Schedule markup puts the name straight in the anchor text. Both are live shapes.
const ARCHIVE_HTML = `
<a href="/cricket-series/6732/icc-cricket-world-cup-2023/matches" title="ICC Cricket World Cup 2023 Matches 5 Oct 2023 - 19 Nov 2023"><div class="flex"><div class="text-ellipsis">ICC Cricket World...</div></div></a>
<a href="/cricket-series/7404/afghanistan-tour-of-uae-2023-24/matches" title="Afghanistan tour of United Arab Emirates 2023-24 Matches 29 Dec 2023 - 2 Jan 2024"><div>x</div></a>
`;
const SCHEDULE_HTML =
  `<a href="/cricket-series/12123/caribbean-premier-league-2026" class="block">Caribbean Premier League 2026</a>`;

test("reads the full name from title and strips the trailing Matches date range", () => {
  const [wc] = parseSeriesAnchors(ARCHIVE_HTML);
  assert.equal(wc.name, "ICC Cricket World Cup 2023");
  assert.equal(wc.id, "6732");
});

test("prefers title over the ellipsised inline text", () => {
  // Reading the inline text would have stored "ICC Cricket World..." — a name no query matches.
  assert.equal(parseSeriesAnchors(ARCHIVE_HTML)[0].name.includes("..."), false);
});

test("also handles the schedule-page shape where the name is the anchor text", () => {
  const [cpl] = parseSeriesAnchors(SCHEDULE_HTML);
  assert.equal(cpl.name, "Caribbean Premier League 2026");
});

test("decodes entities so an apostrophe in a name is searchable", () => {
  const html = `<a href="/cricket-series/1/x" title="ICC Men&#x27;s T20 World Cup 2024"><div>y</div></a>`;
  assert.equal(parseSeriesAnchors(html)[0].name, "ICC Men's T20 World Cup 2024");
});

test("walks a bounded number of pages per run, not the whole archive", async () => {
  let calls = 0;
  const fetchPage = async () => { calls++; return ARCHIVE_HTML; };
  await mergeArchiveSeries([], null, noop, noop, fetchPage);
  // ARCHIVE_PAGES_PER_RUN. An unbounded walk is the load profile that produced ANDROID-35.
  assert.equal(calls, 2);
});

test("resumes from the stored cursor instead of restarting at the newest year", async () => {
  const seen = [];
  const fetchPage = async (url) => { seen.push(url); return ARCHIVE_HTML; };
  const out = await mergeArchiveSeries([], { archiveCursor: 2020, data: [] }, noop, noop, fetchPage);
  assert.ok(seen[0].endsWith("/2020"), `expected to resume at 2020, got ${seen[0]}`);
  assert.ok(seen[1].endsWith("/2019"));
  assert.equal(out.cursor, 2018);
});

test("a block aborts the walk instead of collecting more 403s", async () => {
  let calls = 0;
  const fetchPage = async () => {
    calls++;
    throw new CricbuzzBlockedError(90_000);
  };
  await mergeArchiveSeries([], null, noop, noop, fetchPage);
  assert.equal(calls, 1, "should stop on the first block, not keep walking");
});

test("a block keeps the previous corpus rather than overwriting it with a truncated one", async () => {
  const previous = { data: [{ id: "999", name: "Previously Indexed Series" }], archiveCursor: 2022 };
  const fetchPage = async () => { throw new CricbuzzBlockedError(90_000); };
  const out = await mergeArchiveSeries([], previous, noop, noop, fetchPage);
  assert.deepEqual(out.series.map((s) => s.id), ["999"]);
});

test("a block does not advance the cursor past the page it failed on", async () => {
  const fetchPage = async () => { throw new CricbuzzBlockedError(90_000); };
  const out = await mergeArchiveSeries([], { archiveCursor: 2021, data: [] }, noop, noop, fetchPage);
  assert.equal(out.cursor, 2021, "next run must retry 2021, not skip it");
});

test("one non-block page failure is skipped, and the walk continues", async () => {
  let calls = 0;
  const fetchPage = async () => {
    calls++;
    if (calls === 1) throw new Error("HTTP 500");
    return ARCHIVE_HTML;
  };
  const out = await mergeArchiveSeries([], null, noop, noop, fetchPage);
  assert.equal(calls, 2);
  assert.ok(out.series.length > 0, "the second page's results should still land");
});

test("the active list wins over an archived copy of the same series", async () => {
  const active = [{ id: "6732", name: "ICC Cricket World Cup 2023", startDate: "1696464000000", endDate: "" }];
  const fetchPage = async () => ARCHIVE_HTML;
  const out = await mergeArchiveSeries(active, null, noop, noop, fetchPage);
  const wc = out.series.find((s) => s.id === "6732");
  assert.equal(wc.startDate, "1696464000000", "an in-progress series has fresher dates than the archive");
});

test("previously indexed series survive a run that finds nothing new", async () => {
  const previous = { data: [{ id: "111", name: "Old Series" }], archiveCursor: 2019 };
  const fetchPage = async () => "<html>no series here</html>";
  const out = await mergeArchiveSeries([], previous, noop, noop, fetchPage);
  assert.deepEqual(out.series.map((s) => s.id), ["111"], "a re-crawl must never shrink the corpus");
});
