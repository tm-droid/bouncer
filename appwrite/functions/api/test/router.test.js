import test from "node:test";
import assert from "node:assert/strict";
import handler, { ROUTES, routeOf, scheduledJobs, withTimeout } from "../src/main.js";

const at = (iso) => scheduledJobs(new Date(iso));

test("each quarter-hour slot runs at most one job", () => {
  // The three jobs together do not fit the 300 s timeout, so two in one tick is the failure.
  for (let day = 1; day <= 31; day++) {
    for (let hour = 0; hour < 24; hour++) {
      for (const minute of [0, 15, 30, 45]) {
        const jobs = scheduledJobs(new Date(Date.UTC(2026, 9, day, hour, minute, 20)));
        assert.ok(jobs.length <= 1, `${day} ${hour}:${minute} -> ${jobs}`);
      }
    }
  }
});

test("highlights runs on the first tick of every hour and on no other", () => {
  assert.deepEqual(at("2026-10-13T09:00:20Z"), ["highlights"]);
  for (const t of ["09:15:20", "09:30:20", "09:45:20"]) assert.ok(!at(`2026-10-13T${t}Z`).includes("highlights"), t);
});

test("the prune is offered the third tick of every hour; its own window decides", () => {
  const runs = [];
  for (let hour = 0; hour < 24; hour++) {
    for (const minute of [0, 15, 30, 45]) {
      if (scheduledJobs(new Date(Date.UTC(2026, 9, 13, hour, minute))).includes("prune")) runs.push(minute);
    }
  }
  assert.equal(runs.length, 24);
  assert.ok(runs.every((m) => m === 30));
});

test("the player index is rebuilt every third day of the month", () => {
  const days = [];
  for (let day = 1; day <= 31; day++) {
    if (scheduledJobs(new Date(Date.UTC(2026, 9, day, 4, 15))).includes("player-index")) days.push(day);
  }
  assert.deepEqual(days, [1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31]);
});

test("a path is matched without its trailing slash or query", () => {
  assert.equal(routeOf("/news"), "/news");
  assert.equal(routeOf("/news/"), "/news");
  assert.equal(routeOf("/player-search?x=1"), "/player-search");
  assert.equal(routeOf("/match-detail/?x=1"), "/match-detail");
  assert.equal(routeOf(undefined), "/");
  assert.deepEqual(Object.keys(ROUTES).sort(), ["/match-detail", "/news", "/player-search", "/presence"]);
});

test("an unknown route is a 404, never a default handler", async () => {
  // A request with no path must not fall through to presence or news: the deploy tool's health
  // nudge posts `{}` to `/`, and an old build's body means nothing to another route's handler.
  const res = { json: (body, status = 200) => ({ body, status }) };
  const out = await handler({ req: { path: "/", headers: {} }, res, log() {}, error() {} });
  assert.equal(out.status, 404);
  assert.equal(out.body.error, "unknown_route");
});

test("each public route keeps the timeout it had as its own function", () => {
  // The function's timeout is 300 s for the prune. These are callable by anyone.
  assert.deepEqual(
    Object.fromEntries(Object.entries(ROUTES).map(([path, r]) => [path, r.timeoutMs])),
    { "/match-detail": 12_000, "/news": 30_000, "/player-search": 90_000, "/presence": 15_000 },
  );
});

test("a route that outlives its budget is answered for, not waited on", async () => {
  const never = new Promise(() => {});
  assert.equal(await withTimeout(never, 20, () => "timed out"), "timed out");
  assert.equal(await withTimeout(Promise.resolve("done"), 1000, () => "timed out"), "done");
});
