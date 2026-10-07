import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "crypto";

import { ALERT_MAX_AGE_MS, deliveryAlert, milestoneAlerts, resultAlert } from "../src/alerts.js";
import { classifyEvent } from "../src/llm.js";
import { ALERT_TTL_SECONDS, matchTopic, resetFcmTokenCache, sendTopicMessage, ttlSecondsFor } from "../src/fcm.js";

const NOW = 1_800_000_000_000;
const base = { matchId: 161777, title: "India vs Australia, 3rd ODI", ball: "32.1", now: NOW, score: "145/4 (32.1)" };

test("a wicket becomes a wicket push carrying the match id", () => {
  const a = deliveryAlert({ ...base, event: "WICKET", text: "Cummins to Kohli, out Caught by Carey!!", timestampMs: NOW - 30_000 });
  assert.equal(a.type, "wicket");
  assert.equal(a.match_id, "161777");
  assert.equal(a.title, "Wicket · 145/4 (32.1)");
  assert.equal(a.body, "32.1 Cummins to Kohli, out Caught by Carey!!");
  assert.equal(a.match_title, "India vs Australia");
});

test("fours and sixes are boundary pushes; everything else is silent", () => {
  assert.equal(deliveryAlert({ ...base, event: "FOUR", text: "x", timestampMs: NOW }).type, "boundary");
  assert.equal(deliveryAlert({ ...base, event: "SIX", text: "x", timestampMs: NOW }).title, "Six · 145/4 (32.1)");
  assert.equal(deliveryAlert({ ...base, event: "NONE", text: "x", timestampMs: NOW }), null);
});

test("an overturned review is not a wicket push", () => {
  const text = "Manav Suthar to Pasindu Sooriyabandara, no run, has been given OUT LBW, but he reviews and survives";
  assert.equal(deliveryAlert({ ...base, event: classifyEvent(text), text, timestampMs: NOW }), null);
});

test("a republished backlog is not alerted", () => {
  assert.equal(deliveryAlert({ ...base, event: "WICKET", text: "x", timestampMs: NOW - ALERT_MAX_AGE_MS - 1 }), null);
  assert.equal(deliveryAlert({ ...base, event: "WICKET", text: "x", timestampMs: undefined }), null);
});

test("milestones: first sighting seeds, a crossing alerts once", () => {
  const seed = milestoneAlerts({ ...base, batsmen: [{ name: "Kohli", runs: "73", balls: "60" }], previous: undefined });
  assert.equal(seed.alerts.length, 0, "already past fifty when first seen — not news");
  const step = milestoneAlerts({ ...base, batsmen: [{ name: "Kohli", runs: "101", balls: "88" }, { name: "Rahul", runs: "4" }], previous: seed.next });
  assert.equal(step.alerts.length, 1);
  assert.equal(step.alerts[0].type, "milestone");
  assert.equal(step.alerts[0].title, "Kohli reaches 100");
  assert.equal(step.alerts[0].body, "100 off 88 balls · 145/4 (32.1)");
  const again = milestoneAlerts({ ...base, batsmen: [{ name: "Kohli", runs: "104" }], previous: step.next });
  assert.equal(again.alerts.length, 0);
});

test("a new batter is seeded, not alerted", () => {
  const r = milestoneAlerts({ ...base, batsmen: [{ name: "New", runs: "50" }], previous: new Map([["Old", 12]]) });
  assert.equal(r.alerts.length, 0);
  assert.equal(r.next.get("New"), 50);
});

test("result push carries the status line", () => {
  const r = resultAlert({ ...base, status: "India won by 4 wkts" });
  assert.deepEqual([r.type, r.title, r.body], ["match_end", "India vs Australia", "India won by 4 wkts"]);
  assert.equal(resultAlert({ ...base, status: null }), null);
});

test("matchTopic keeps only FCM-legal characters", () => {
  assert.equal(matchTopic(161777), "match_161777");
  assert.equal(matchTopic("a/b c"), "match_abc");
});

test("sendTopicMessage: unconfigured sends nothing; configured posts a stringified HIGH data message", async () => {
  resetFcmTokenCache();
  const calls = [];
  const fetchFn = async (url, init) => {
    calls.push({ url, init });
    if (url.includes("oauth2")) return { ok: true, status: 200, json: async () => ({ access_token: "tok" }) };
    return { ok: true, status: 200 };
  };
  assert.equal(await sendTopicMessage("t", { a: 1 }, { env: {}, fetchFn }), false);
  assert.equal(calls.length, 0);

  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const env = {
    FIREBASE_PROJECT_ID: "p",
    FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify({ client_email: "x@y", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) }),
  };
  assert.equal(await sendTopicMessage("match_1", { n: 5, gone: null }, { env, fetchFn, now: NOW }), true);
  assert.equal(await sendTopicMessage("match_1", { n: 6 }, { env, fetchFn, now: NOW + 1000 }), true);
  const sends = calls.filter((c) => c.url.includes("fcm.googleapis.com"));
  assert.equal(calls.filter((c) => c.url.includes("oauth2")).length, 1, "token is cached across sends");
  assert.equal(sends[0].url, "https://fcm.googleapis.com/v1/projects/p/messages:send");
  assert.equal(sends[0].init.headers.Authorization, "Bearer tok");
  assert.deepEqual(JSON.parse(sends[0].init.body), { message: { topic: "match_1", data: { n: "5" }, android: { priority: "HIGH", ttl: "300s" } } });
});

test("a push expires in transit: five minutes, longer for a start and a result", () => {
  // The default is the same bound the server applies before sending at all.
  assert.equal(ALERT_TTL_SECONDS * 1000, ALERT_MAX_AGE_MS);
  for (const type of ["wicket", "boundary", "milestone", "MATCH_STARTED", undefined]) {
    assert.equal(ttlSecondsFor(type), 300, String(type));
  }
  assert.equal(ttlSecondsFor("match_start"), 1800);
  assert.equal(ttlSecondsFor("match_end"), 21600);
});

test("sendTopicMessage never throws", async () => {
  resetFcmTokenCache();
  const errors = [];
  const ok = await sendTopicMessage("t", {}, {
    env: { FIREBASE_PROJECT_ID: "p", FIREBASE_SERVICE_ACCOUNT_JSON: "not json" },
    fetchFn: async () => { throw new Error("boom"); },
    onError: (m) => errors.push(m),
  });
  assert.equal(ok, false);
  assert.equal(errors.length, 1);
});

const sendEnv = () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  return {
    FIREBASE_PROJECT_ID: "p",
    FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify({ client_email: "x@y", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) }),
  };
};
const tokenOk = { ok: true, status: 200, json: async () => ({ access_token: "tok" }) };

test("sendTopicMessage: a 5xx is retried once, and the second answer stands", async () => {
  resetFcmTokenCache();
  let sends = 0;
  const fetchFn = async (url) => {
    if (url.includes("oauth2")) return tokenOk;
    sends += 1;
    return sends === 1 ? { ok: false, status: 503, text: async () => "{}" } : { ok: true, status: 200 };
  };
  assert.equal(await sendTopicMessage("match_1", { a: 1 }, { env: sendEnv(), fetchFn, now: NOW }), true);
  assert.equal(sends, 2);
});

test("sendTopicMessage: a final failure names FCM's own error code, not just the status", async () => {
  // ANDROID-4X carried only `sent: 1, queued: 2` — which alert, which topic and why were all lost.
  resetFcmTokenCache();
  const details = [];
  const fetchFn = async (url) =>
    url.includes("oauth2")
      ? tokenOk
      : { ok: false, status: 400, text: async () => JSON.stringify({ error: { status: "INVALID_ARGUMENT" } }) };
  const ok = await sendTopicMessage("match_1", { a: 1 }, {
    env: sendEnv(), fetchFn, now: NOW, onError: (_m, d) => details.push(d),
  });
  assert.equal(ok, false);
  assert.deepEqual(details, [{ topic: "match_1", status: 400, fcmStatus: "INVALID_ARGUMENT" }], "a 400 is not retried");
});

test("sendTopicMessage: concurrent cold sends share one token exchange", async () => {
  resetFcmTokenCache();
  let exchanges = 0;
  const fetchFn = async (url) => {
    if (url.includes("oauth2")) { exchanges += 1; return tokenOk; }
    return { ok: true, status: 200 };
  };
  const env = sendEnv();
  const results = await Promise.all([1, 2, 3].map((n) => sendTopicMessage(`match_${n}`, { n }, { env, fetchFn, now: NOW })));
  assert.deepEqual(results, [true, true, true]);
  assert.equal(exchanges, 1);
});
