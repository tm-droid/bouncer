/**
 * Match alerts: which deliveries and states become a push, and what the push says.
 *
 * Pure, so the rules are testable without a live page. `main.js` sends what these return to the
 * match's FCM topic (`fcm.js`); the device filters by what the user asked for, so every alert-worthy
 * event is sent whether or not anyone is subscribed — a send to an empty topic costs nothing.
 *
 * The `type` values are the ones `BouncerFirebaseMessagingService` has always handled. Until these
 * were written nothing on the server produced them, which is why an enabled notification never
 * arrived.
 */

/**
 * A delivery older than this is not alerted. Cricbuzz sometimes stalls and then republishes a
 * backlog (thirteen minutes on match 161777, 2026-09-12), and a burst of stale wickets on the lock
 * screen is worse than none. It also covers a cold container seeding its watermark behind the page.
 */
export const ALERT_MAX_AGE_MS = 5 * 60 * 1000;

/** Individual scores that are announced. */
export const MILESTONE_RUNS = [50, 100, 150, 200];

const EVENT_TYPE = { WICKET: "wicket", FOUR: "boundary", SIX: "boundary" };

/** The match title without Cricbuzz's ", 3rd ODI" tail — the notification header has little room. */
function sidesOf(title) {
  return String(title || "").split(",")[0].trim();
}

/**
 * The push for one newly stored delivery, or null when it is not alert-worthy or is too old.
 * [event] is `classifyEvent` of the raw line, so an overturned review is already not a WICKET.
 */
export function deliveryAlert({ matchId, title, event, ball, text, timestampMs, now, score }) {
  const type = EVENT_TYPE[event];
  if (!type) return null;
  const ts = Number(timestampMs);
  if (!Number.isFinite(ts) || now - ts > ALERT_MAX_AGE_MS) return null;
  const label = event === "WICKET" ? "Wicket" : event === "SIX" ? "Six" : "Four";
  return {
    type,
    match_id: String(matchId),
    event: event.toLowerCase(),
    title: score ? `${label} · ${score}` : label,
    body: ball ? `${ball} ${text}` : text,
    match_title: sidesOf(title),
    ball,
  };
}

/**
 * Batters who crossed a milestone since the previous snapshot of this match.
 *
 * [previous] is the name → runs map from the last run, or undefined when this container has not
 * seen the match yet: a first sighting only seeds, because a batter already on 73 did not just
 * pass fifty. Returns the alerts and the map to keep.
 */
export function milestoneAlerts({ matchId, title, batsmen, previous, score }) {
  const next = new Map(previous ?? []);
  const alerts = [];
  for (const b of batsmen ?? []) {
    const runs = Number.parseInt(b?.runs, 10);
    if (!b?.name || !Number.isFinite(runs)) continue;
    const before = previous?.get(b.name);
    next.set(b.name, runs);
    if (previous === undefined || before === undefined) continue;
    const crossed = MILESTONE_RUNS.filter((m) => before < m && runs >= m).pop();
    if (crossed === undefined) continue;
    alerts.push({
      type: "milestone",
      match_id: String(matchId),
      title: `${b.name} reaches ${crossed}`,
      body: [b.balls ? `${crossed} off ${b.balls} balls` : null, score].filter(Boolean).join(" · ") || sidesOf(title),
      match_title: sidesOf(title),
      player: b.name,
      milestone: crossed,
    });
  }
  return { alerts, next };
}

/** The result push, sent once when match-sync finalises the match. */
export function resultAlert({ matchId, title, status }) {
  if (!status) return null;
  return {
    type: "match_end",
    match_id: String(matchId),
    title: sidesOf(title) || "Match result",
    body: status,
    match_title: sidesOf(title),
  };
}
