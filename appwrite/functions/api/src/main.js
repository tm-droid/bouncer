// bouncer-api: every on-demand route and every low-frequency scheduled job, in one function.
//
// Appwrite's Free plan allows two functions per project. `bouncer-match-sync` is one; this is the
// other. It holds what were five functions (news-sync, player-search, match-presence,
// highlights-sync, match-commentary-prune), each still a self-contained handler in its own
// directory with its own tests. This file only decides which one an execution is for.
//
// Two node-appwrite majors are installed on purpose: the three on-demand handlers were written
// against 15 (see storageJson.js for what its file download returns), the prune needs 26 for bulk
// deletes, and highlights was already on 26. They import `node-appwrite` and `node-appwrite-26`
// respectively; moving a handler across is a change to that handler, not to this file.

import news from "./news/main.js";
import playerSearch from "./player-search/main.js";
import presence from "./presence/main.js";
import highlights from "./highlights/main.js";
import prune from "./prune/main.js";

/**
 * On-demand routes, by the execution's path. The device sends one of these; nothing else is served.
 *
 * Each carries the timeout it had as its own function. The function's own timeout is 300 s, sized
 * for the prune, and these routes are `execute("any")`: without a cap of their own, a hung upstream
 * would let any caller hold a container ten times longer than before, against a 100 GB-hour plan.
 */
export const ROUTES = {
  "/news": { handler: news, timeoutMs: 30_000 },
  "/player-search": { handler: playerSearch, timeoutMs: 90_000 },
  "/presence": { handler: presence, timeoutMs: 15_000 },
};

/**
 * [work]'s result, or [onTimeout]'s once [ms] has passed.
 *
 * This bounds the EXECUTION, which is what is billed and what holds the container: returning ends
 * it. It does not cancel [work]. The handlers take no abort signal, so a request already in flight
 * finishes or dies by its own timeout; every upstream call a route makes must therefore keep one.
 */
export async function withTimeout(work, ms, onTimeout) {
  let timer;
  const expired = new Promise((resolve) => {
    timer = setTimeout(() => resolve(onTimeout()), ms);
  });
  try {
    return await Promise.race([work, expired]);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Which scheduled jobs a quarter-hourly tick runs, as a pure function of the clock (UTC).
 *
 * One schedule drives three cadences, so each job claims a quarter-hour slot and no two share
 * one: together they would not fit the 300 s timeout (the prune alone is budgeted most of it).
 *  - highlights: the first tick of every hour. The handler also gates itself on `minute < 15`,
 *    so a tick this function sends it outside that window would be a no-op anyway.
 *  - prune: 03:15 daily (was 03:00 on its own function).
 *  - player index rebuild: 04:15 on every third day of the month (was 03:00 on those days).
 */
export function scheduledJobs(now) {
  const minute = now.getUTCMinutes();
  const hour = now.getUTCHours();
  const firstQuarter = minute < 15;
  const secondQuarter = minute >= 15 && minute < 30;
  const jobs = [];
  if (firstQuarter) jobs.push("highlights");
  if (secondQuarter && hour === 3) jobs.push("prune");
  if (secondQuarter && hour === 4 && (now.getUTCDate() - 1) % 3 === 0) jobs.push("player-index");
  return jobs;
}

const JOBS = {
  highlights,
  prune,
  // The handler rebuilds its index when it sees `x-appwrite-trigger: schedule`, a header Appwrite
  // sets on this execution and refuses from callers.
  "player-index": playerSearch,
};

/** `/news/` and `/news?x=1` are `/news`; anything else is returned as it came. */
export function routeOf(path) {
  const clean = String(path ?? "").split("?")[0].replace(/\/+$/, "");
  return clean === "" ? "/" : clean;
}

export default async (context) => {
  const { req, res, log, error } = context;

  // The scheduler's header is the only thing that reaches the jobs, and the routes are public, so
  // it matters that a caller cannot send it. Measured 2026-10-13: the executions API answers 400 to
  // any caller-supplied `x-appwrite-*` header, and this function has no domain. Giving it a domain
  // is a decision to re-measure that, not a setting.
  if (req.headers?.["x-appwrite-trigger"] === "schedule") {
    const jobs = scheduledJobs(new Date());
    if (jobs.length === 0) return res.json({ scheduled: true, ran: [] });
    // At most one job per tick by construction; a loop rather than an index so that stays an
    // assertion of `scheduledJobs`, not an assumption here.
    const ran = [];
    let last = null;
    for (const name of jobs) {
      log(`scheduled job: ${name}`);
      try {
        last = await JOBS[name](context);
        ran.push(name);
      } catch (e) {
        error(`scheduled job ${name} threw: ${e?.stack ?? e?.message}`);
        return res.json({ scheduled: true, ran, failed: name, error: e?.message }, 500);
      }
    }
    return last ?? res.json({ scheduled: true, ran });
  }

  const path = routeOf(req.path);
  const route = ROUTES[path];
  if (!route) return res.json({ error: "unknown_route", path: req.path ?? null }, 404);
  return withTimeout(route.handler(context), route.timeoutMs, () => {
    error(`${path} exceeded its ${route.timeoutMs / 1000}s budget`);
    return res.json({ error: "route_timeout", path }, 504);
  });
};
