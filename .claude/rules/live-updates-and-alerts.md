---
paths:
  - "app/src/main/kotlin/**/liveactivity/**"
  - "app/src/main/kotlin/**/services/**"
  - "app/src/main/kotlin/**/managers/MatchSubscriptionManager.kt"
  - "app/src/main/res/layout/notification_*"
---

# Live Update Cards and Match Alerts

**A Live Update is a promoted notification, and `RemoteViews` disqualify it.** The home-screen live
score is an Android Live Update — an ongoing notification the system pins to the top of the shade,
to the lock screen, and to a status-bar chip. The contract is narrow and every clause is
load-bearing: the style must be Standard / `BigTextStyle` / `CallStyle` / `ProgressStyle` /
`MetricStyle`; the manifest must declare `POST_PROMOTED_NOTIFICATIONS`; the notification must be
`ongoing` and call `setRequestPromotedOngoing(true)`; and it must have **no** `setCustomContentView`
and **not** be a group summary. The stack this replaced broke all three ways at once and reported
none of them — it was built with `DecoratedCustomViewStyle` plus two `RemoteViews` (so promotion
could never be granted), its `setRequestPromotedOngoing` was a reflective call into
`NotificationCompat.Builder` with a silent catch, and its service was constructed with a
`NoopMatchUpdateDataSource` that nothing ever started. `LiveUpdateNotifier` uses
`NotificationCompat.BigTextStyle`; core-ktx 1.19 carries every promotion API, so there is **no
reflection and no `@RequiresApi`** — below API 36 the same builder degrades to an ordinary ongoing
notification. Two API traps: `Notification.isRequestPromotedOngoing()` does **not** exist on stock
API 36 even though the setter does (read `EXTRA_REQUEST_PROMOTED_ONGOING` from `extras` instead),
and the settings intent is `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`, not the
`ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` the developer guide names — **and that action resolves
to nothing on a stock API 36 build** (the Android 16 emulator has neither the screen nor the
feature: `canPostPromotedNotifications()` returns false there), so it must be `resolveActivity`-
checked before `startActivity` or it is an `ActivityNotFoundException`. Promotion being unavailable
is a normal device state, not an error: the app still posts an ordinary ongoing notification and
the settings row says so. Pinned by
`LiveUpdatePromotabilityTest` (**androidTest**, needs an API 36+ device), which asserts
`Notification.hasPromotableCharacteristics()` — the platform's own verdict, and the only check that
would have caught any of the above. It cannot be a unit test: there is no Robolectric here and the
promotion rules live in the framework, not in `NotificationCompat`.

**The Live Update feed is Realtime, and the foreground service exists only to receive it.**
`LiveUpdateService` subscribes `AppwriteMatchDetailClient.observeMatch` per followed live match —
the row `bouncer-match-sync` writes every minute **because the service pings presence for every
followed match** (`presenceHeartbeat`, one batched execution per 90s). Before 2026-09-30 it did not,
so a followed match nobody had open was on the slow tier and its card updated every 15 minutes at
best. So a tracked match costs one billed read
at first paint and none after. A promoted notification does **not** need a foreground service; the
FGS only keeps the process alive for the websocket frames, which is why `onTimeout` (the API 34+
six-hour cumulative `dataSync` cap, which a Test day exceeds) can `STOP_FOREGROUND_DETACH` and
leave every card posted. The card is one half of following a match (the "Live score card" switch in
`MatchFollowSheet`); the settings row exists only to surface the one state the user cannot fix from
inside the app. The channel is `live_update_scores` at `IMPORTANCE_DEFAULT`: the retired
`live_activity_updates` was `IMPORTANCE_LOW`, channel importance is immutable after creation, and a
LOW channel has no status-bar presence for the chip to attach to. `startForeground` binds the
service to exactly one notification id, so one match's card is the anchor and `anchorTo` moves it
when that match ends.

**A cricket card is a scoreboard, not a position, so the style is `BigTextStyle`.** `ProgressStyle`
allows exactly **one** line of text below the title, which is why the card it replaced showed a
batting pair and nothing else — no opposition total, no bowler, no target — while a bar counting
balls bowled took a third of the height. None of the iOS Live Activities this is modelled on has a
bar. `LiveUpdateProgress` and its test were deleted with it; do not reintroduce a bar without
re-reading what it would displace. This constrains the **promoted** card only — the custom-layout
card below has real views.

**`setColorized(true)` is a promotion requirement, and it is the only flag that moves the verdict.**
Measured on a stock API 36 emulator across every combination of style, `shortCriticalText`, large
icon and actions: `hasPromotableCharacteristics()` is true with colorized and false without, and a
`ProgressStyle` card carrying a chip is refused exactly like a `BigTextStyle` one. The lever on a
promoted card is the colour, not whether there is one.

**There are two cards, and the device picks.** `RemoteViews` disqualify promotion absolutely —
measured the same way: `setCustomContentView`, `setCustomBigContentView`, either alongside a style,
and `DecoratedCustomViewStyle` all return false. So a designed layout and a promoted card cannot
coexist, and the promoted one is a single TextView of left-aligned text: no column for the figures,
no second crest, no large numeral. But promotion is **unavailable to most of the install base** —
it is Android 16, `minSdk` is 26, and even an API 36 device can report
`canPostPromotedNotifications() == false` (the stock emulator does). Serving everyone the template
therefore charged every pre-16 user the design and gave them nothing. `LiveUpdateNotifier.build`
now branches on `canPromote()`: promotable devices get the `BigTextStyle` card and the chip;
everyone else gets `LiveUpdateScoreCard`, the real layout
(`res/layout/notification_live_score{,_collapsed}.xml`). Both render one
`LiveUpdateScoreboard` — fields, not a paragraph — so a change to what the card says lands on both,
and `LiveUpdateScoreCardTest` (androidTest) asserts the non-promotable branch actually takes the
layout. Do not collapse this to one path without re-measuring; the docs were wrong about the
colorized rule too.

**A notification is drawn on the platform's surface, not on ours, so it uses the platform's greys.**
`text_secondary` is `#C1B08D` and `card_border` `#33F3DB93` in `values-night` — correct against the
app's gold-on-black surfaces, and inside the neutral system shade they render as tan body text and
an olive divider, the same cast the Fall of Wickets card had. The notification layouts use
`notif_text_primary` / `notif_text_secondary` / `notif_divider` instead, which are neutral in both
files. The flat mustard every card used to be was
`getTeamNotificationColor` (now deleted) missing on **every** match: it matched full country names
with `contains` against `payload.team1Name`, which is a Cricbuzz short code ("RSA", "KENT"), so the
gold was a fallback rather than a team colour. `TeamColorRegistry` replaces it — a plain object,
safe from a Service, with a short-code alias table and a deterministic hash fallback — and the
platform picks its own text colour against the fill. This is the one place the visual grammar's
"team colour is never ink" rule is overridden by the platform, and `LiveUpdatePromotabilityTest`
pins it.

**`LiveUpdateContent` builds five surfaces and each has a different budget.** `subText` is the
header (series · format), `title` the batting side's **whole** contribution plus its over figure
(`"AUS 266 & 234/1 (56.4)"` — the side aggregate, never `score`, which is one innings of a Test),
`text` the collapsed second line under a 48-character budget, `bigText` the expanded scoreboard
capped at six lines, and `shortCriticalText` the status-bar chip, dropped entirely past 7
characters. `bigText` is built in descending order of value — opposition total, striker,
non-striker, bowler, recent balls, status — and truncated from the bottom, because the template
ellipsises overflow rather than scrolling it.

**The live payload carries a `situation` object, and `ignoreUnknownKeys` hid it for its whole
life.** `liveSituation` (`match-sync/src/cricbuzz.js`) publishes the match state as numbers —
`matchFormat`, `battingTeamIsTeam1` (compared by **team id** server-side), `runsScored`,
`wicketsLost`, `ballsBowled`, `target`, `runsNeeded`, `oversRemaining`, and both run rates — and
`MatchLiveDetailPayload` never declared it, so `AppJson.ignoreUnknownKeys = true` dropped it on
every read. Meanwhile the device inferred the same facts from the cached list entry and from exact
string equality between `score` and a side aggregate. `LiveSituation` now models it and is the
authority where present; the string paths stay as the fallback for older rows. Every field is
nullable and `liveSituation` can return null outright — absence must degrade, never read as zero.

**The Live Update read the match cache under the wrong key for its whole life.**
`LiveUpdateSources` used `"live"`; `MatchCacheService` is keyed `"Live"`. `cachedLiveMatch()`
therefore returned null on every device for every match, and that single mismatch cost the card its
series name, its **team crest** (`logoTeamName` joins through the cached match by team id) and its
format at once — three unrelated-looking symptoms, one cause. The keys are now
`MatchCacheService.LIVE` / `UPCOMING` / `RECENT` and every caller reads them; never write the
literal.

**The payload names the first-innings side, not the batting side.** `MatchLiveDetailPayload`'s
`team1*` slots belong to whoever batted first, which is the wrong team for the whole of a second
innings, and the payload's *strings* never say who is batting. `battingSlot` (`LiveUpdateScore.kt`)
reads `situation.battingTeamIsTeam1` first and falls back to matching the live `score` line against
each side's latest innings — both are built from the same Cricbuzz totals, so that comparison is
exact, but it is string equality and a formatting drift takes it out silently. Do not compare the names: `team1Name` is a short code
("EZONE", "RSAA") that matches no full team name, which is why every join onto the app's own teams
goes by `teamId`.

**Match alerts are server-sent FCM data messages to a per-match topic, and until September 2026 nothing
sent them.** `BouncerFirebaseMessagingService` always handled `wicket`/`boundary`/`milestone`/
`match_start`/`match_end`, but the only server push was `poll-live-matches`' `cricket-live-updates`
live-count broadcast, which the device turns into a widget refresh and nothing else — so a user with
notifications enabled received none. Now: `match-sync` sends wicket/boundary (from `classifyEvent` of
the raw line, on a row's **first** write only — a 409 means another execution already alerted),
milestones (batter runs crossing 50/100/150/200 between runs, kept in memory; a cold container seeds
rather than alerts) and the result (on the once-per-match finalising run); `poll-live-matches` sends
`match_start` when an id first appears live (`startedIds` in `poll-state`, 6-day memory; the first run
after deploy records without sending). Deliveries older than `ALERT_MAX_AGE_MS` (5 min) are not
alerted, so a Cricbuzz backlog republish does not burst. Topic = `match_{id}` (`matchTopic`, in the
vendored `fcm.js` and mirrored by the Kotlin `matchTopic`, both pinned by `FcmJsVendorParityTest`).
The server sends every event whether or not anyone listens; the device decides in
`shouldShowMatchAlert` (per-match prefs × the Settings switches, which were cosmetic until then ×
quiet hours). Both functions need the **secret** `FIREBASE_SERVICE_ACCOUNT_JSON` + `FIREBASE_PROJECT_ID`
— secret values read back empty from the API, so check the run log: each run logs `FCM not
configured` when they are missing. **The bell opens `MatchFollowSheet`**, which sets two independent
things in `MatchSubscriptionManager`: `subscriptions` (the Live Update card) and `alertPrefs` (which
alerts; any on ⇒ subscribed to the topic). `ScorePollingWorker` no longer posts notifications.

**Match reminders are listed at Notifications › Reminders.** `ReminderBottomSheet` used to schedule an alarm and record nothing (`RemindersManager.saveReminder` had no caller), and `RemindersActivity` was never launched, so a reminder could be set but never seen or cancelled. The sheet now upserts into `RemindersManager`; `upcoming()` drops fired ones.
