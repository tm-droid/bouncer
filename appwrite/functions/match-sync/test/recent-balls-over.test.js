import test from "node:test";
import assert from "node:assert/strict";
import { parseLiveDetail } from "../src/cricbuzz.js";

/**
 * The over summary describes a COMPLETED over, and the payload has to say which one.
 *
 * Cricbuzz hangs `overSummary` off an `overSeparator` on the delivery that ENDS the over: ball 48.6
 * carries `overNumber: 49`. Published on its own, the device had no way to tell that from a summary
 * of the over its commentary feed was showing, so a strip headed "This Over" rendered the previous
 * over's six balls above the current over's first three.
 *
 * The second case is the innings boundary. `overNumber` restarts at 1 every innings, so the largest
 * number in the live page's ~20-item window can belong to the innings that has already finished.
 * Ordering is by `timestamp`, which is monotonic across the whole match.
 */

function flightHtml(payload) {
  const inner = JSON.stringify(payload);
  return `<html><body><script>self.__next_f.push([1,${JSON.stringify(inner)}])</script></body></html>`;
}

test("recentBalls carries the over number of the summary it came from", () => {
  const html = flightHtml({
    commentaryList: [
      {
        commText: "Daniel Douthwaite to Rhodes, 1 run",
        ballMetric: 48.6,
        timestamp: 1785344368208,
        overSeparator: {
          timestamp: 1785344368208,
          overNumber: 49,
          overSummary: "6 4 1 0 3 1 ",
          overRuns: 15,
        },
      },
    ],
  });

  const detail = parseLiveDetail(html);
  assert.deepEqual(detail.recentBalls, ["6", "4", "1", "0", "3", "1"]);
  assert.equal(detail.recentBallsOver, 49);
});

test("the latest summary is the latest by clock, not the largest over number", () => {
  const html = flightHtml({
    commentaryList: [
      // The innings that just ended: a high over number, an OLD timestamp.
      {
        commText: "Starc to Root, out Bowled!",
        ballMetric: 89.6,
        timestamp: 1785340000000,
        overSeparator: {
          timestamp: 1785340000000,
          overNumber: 90,
          overSummary: "0 0 1 0 0 W",
        },
      },
      // The innings now being bowled: over 2, and the newest thing on the page.
      {
        commText: "Cummins to Smith, no run",
        ballMetric: 1.6,
        timestamp: 1785344400000,
        overSeparator: {
          timestamp: 1785344400000,
          overNumber: 2,
          overSummary: "4 0 0 Wd1 1 0 2",
        },
      },
    ],
  });

  const detail = parseLiveDetail(html);
  assert.equal(detail.recentBallsOver, 2);
  assert.deepEqual(detail.recentBalls, ["4", "0", "0", "Wd1", "1", "0", "2"]);
});

test("no over summary anywhere is an absent over, not over zero", () => {
  const detail = parseLiveDetail(flightHtml({ commentaryList: [] }));
  assert.deepEqual(detail.recentBalls, []);
  assert.equal(detail.recentBallsOver, null);
});

// ── The miniscore's live bar ────────────────────────────────────────────────────────────────────
//
// `recentOvsStats` is the string behind Cricbuzz's own "this over" bar and it moves with every ball.
// Values below are the ones measured on match 143240 at 97.3 overs.

function miniscoreHtml(ms, commentaryList = []) {
  return flightHtml({
    miniscore: {
      batsmanStriker: { batName: "Adam Hose", batRuns: 105 },
      batsmanNonStriker: { batName: "Henry Cullen", batRuns: 12 },
      inningsId: 2,
      matchScoreDetails: { matchFormat: "TEST", inningsScoreList: [] },
      ...ms,
    },
    commentaryList,
  });
}

const over97Complete = {
  commText: "Potts to Hose, no run",
  ballMetric: 96.6,
  timestamp: 1785340000000,
  overSeparator: { timestamp: 1785340000000, overNumber: 97, overSummary: "0 1 0 0 0 0 " },
};

test("mid-over, the strip is the over being bowled, not the last one completed", () => {
  const detail = parseLiveDetail(miniscoreHtml({ overs: 97.3, recentOvsStats: "1 0 W" }, [over97Complete]));
  assert.deepEqual(detail.recentBalls, ["1", "0", "W"]);
  assert.equal(detail.recentBallsOver, 98);
});

test("at an over break the bar holds the over that just ended, numbered as that over", () => {
  const detail = parseLiveDetail(miniscoreHtml({ overs: 23, recentOvsStats: "1 1 0 0 1 0" }));
  assert.deepEqual(detail.recentBalls, ["1", "1", "0", "0", "1", "0"]);
  assert.equal(detail.recentBallsOver, 23);
});

test("an empty bar falls back to the newest over summary", () => {
  const detail = parseLiveDetail(miniscoreHtml({ overs: 97, recentOvsStats: "" }, [over97Complete]));
  assert.deepEqual(detail.recentBalls, ["0", "1", "0", "0", "0", "0"]);
  assert.equal(detail.recentBallsOver, 97);
});

test("The Hundred counts balls in `overs`, so it gets no over number", () => {
  const detail = parseLiveDetail(
    miniscoreHtml({
      overs: 72,
      recentOvsStats: "1 0 4",
      matchScoreDetails: { matchFormat: "HUN", inningsScoreList: [] },
    })
  );
  assert.deepEqual(detail.recentBalls, ["1", "0", "4"]);
  assert.equal(detail.recentBallsOver, null);
});
