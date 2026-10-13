---
paths:
  - "app/src/main/kotlin/**/liveactivity/**"
  - "app/src/main/kotlin/**/services/**"
  - "app/src/main/kotlin/**/managers/MatchSubscriptionManager.kt"
  - "app/src/main/res/layout/notification_*"
---

# Live Update Cards and Match Alerts

## Promotion contract

**A Live Update is a promoted notification, and `RemoteViews` disqualify it.** It is an ongoing notification the system pins to the top of the shade, the lock screen and a status-bar chip. Every clause is load-bearing:

- Style must be Standard / `BigTextStyle` / `CallStyle` / `ProgressStyle` / `MetricStyle`.
- The manifest declares `POST_PROMOTED_NOTIFICATIONS`.
- The notification is `ongoing` and calls `setRequestPromotedOngoing(true)`.
- **No** `setCustomContentView`, and **not** a group summary.
- **`setColorized(true)` is required, and is the only flag that moves the verdict.** Measured on a stock API 36 emulator across every combination of style, `shortCriticalText`, large icon and actions: `hasPromotableCharacteristics()` is true with colorized and false without; a `ProgressStyle` card with a chip is refused like a `BigTextStyle` one.

The stack this replaced broke it three ways and reported none: `DecoratedCustomViewStyle` plus two `RemoteViews`, a reflective `setRequestPromotedOngoing` with a silent catch, and a service constructed with a `NoopMatchUpdateDataSource` nothing started. `LiveUpdateNotifier` uses `NotificationCompat.BigTextStyle`; core-ktx 1.19 carries every promotion API, so there is **no reflection and no `@RequiresApi`** — below API 36 the same builder degrades to an ordinary ongoing notification.

API traps:

- `Notification.isRequestPromotedOngoing()` does **not** exist on stock API 36 though the setter does; read `EXTRA_REQUEST_PROMOTED_ONGOING` from `extras`.
- The settings intent is `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`, not the `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` the developer guide names — and it **resolves to nothing on a stock API 36 build** (`canPostPromotedNotifications()` returns false there), so `resolveActivity`-check before `startActivity` or it is an `ActivityNotFoundException`.
- Promotion being unavailable is a normal device state: the app still posts an ordinary ongoing notification and the settings row says so.

Pinned by `LiveUpdatePromotabilityTest` (**androidTest**, API 36+ device), which asserts `Notification.hasPromotableCharacteristics()` — the platform's own verdict. It cannot be a unit test: there is no Robolectric here and the rules live in the framework.

## The two cards

**The device picks.** `RemoteViews` disqualify promotion absolutely (measured: `setCustomContentView`, `setCustomBigContentView`, either alongside a style, and `DecoratedCustomViewStyle` all return false), so a designed layout and a promoted card cannot coexist. But promotion is unavailable to most installs (Android 16 only, `minSdk` 26, and an API 36 device can report `canPostPromotedNotifications() == false`). `LiveUpdateNotifier.build` branches on `canPromote()`: promotable devices get the `BigTextStyle` card and the chip; everyone else gets `LiveUpdateScoreCard`, the real layout (`res/layout/notification_live_score{,_collapsed}.xml`). Both render one `LiveUpdateScoreboard` (fields, not a paragraph), and `LiveUpdateScoreCardTest` (androidTest) asserts the non-promotable branch takes the layout. Do not collapse to one path without re-measuring; the docs were wrong about the colorized rule too.

**The promoted card is `BigTextStyle`, not `ProgressStyle`.** `ProgressStyle` allows exactly **one** line of text below the title, which is why the old card showed a batting pair and nothing else while a balls-bowled bar took a third of the height. `LiveUpdateProgress` and its test were deleted; do not reintroduce a bar without re-reading what it would displace. This constrains the promoted card only.

**`LiveUpdateContent` builds five surfaces, each with its own budget.**

- `subText`: the header (series · format).
- `title`: the batting side's **whole** contribution plus its over figure (`"AUS 266 & 234/1 (56.4)"` — the side aggregate, never `score`, which is one innings of a Test).
- `text`: the collapsed second line, 48-character budget.
- `bigText`: the expanded scoreboard, capped at six lines, built in descending value (opposition total, striker, non-striker, bowler, recent balls, status) and truncated from the bottom, because the template ellipsises overflow.
- `shortCriticalText`: the status-bar chip, dropped entirely past 7 characters.

**A notification is drawn on the platform's surface, so it uses the platform's greys.** `text_secondary` (`#C1B08D`) and `card_border` (`#33F3DB93`) in `values-night` are correct on the app's gold-on-black and render as tan text and an olive divider in the system shade. Notification layouts use `notif_text_primary` / `notif_text_secondary` / `notif_divider`, neutral in both files. The old flat mustard was `getTeamNotificationColor` (deleted) missing on **every** match: it matched full country names with `contains` against `payload.team1Name`, a Cricbuzz short code ("RSA", "KENT"). `TeamColorRegistry` replaces it — a plain object, safe from a Service, with a short-code alias table and a deterministic hash fallback — and the platform picks its own text colour against the fill. This is the one place the "team colour is never ink" rule is overridden by the platform; `LiveUpdatePromotabilityTest` pins it.

## Feed and service

**The feed is Realtime, and the foreground service exists only to receive it.** `LiveUpdateService` subscribes `AppwriteMatchDetailClient.observeMatch` per followed live match. `bouncer-match-sync` writes that row every minute **because the service pings presence for every followed match** (`presenceHeartbeat`, one batched execution per 90s); before 2026-09-30 it did not, and a followed match nobody had open updated every 15 minutes at best. A tracked match costs one billed read at first paint and none after.

- A promoted notification does **not** need a foreground service; the FGS only keeps the process alive for websocket frames. So `onTimeout` (the API 34+ six-hour cumulative `dataSync` cap, which a Test day exceeds) can `STOP_FOREGROUND_DETACH` and leave every card posted.
- The channel is `live_update_scores` at `IMPORTANCE_DEFAULT`. The retired `live_activity_updates` was `IMPORTANCE_LOW`; channel importance is immutable after creation and a LOW channel has no status-bar presence for the chip.
- `startForeground` binds the service to exactly one notification id, so one match's card is the anchor and `anchorTo` moves it when that match ends.
- The card is one half of following a match (the "Live score card" switch in `MatchFollowSheet`); the settings row exists only to surface the one state the user cannot fix in-app.

## Reading the payload

**`situation` is the authority where present.** `liveSituation` (`match-sync/src/cricbuzz.js`) publishes `matchFormat`, `battingTeamIsTeam1` (compared by **team id** server-side), `runsScored`, `wicketsLost`, `ballsBowled`, `target`, `runsNeeded`, `oversRemaining` and both run rates. `MatchLiveDetailPayload` never declared it, so `AppJson.ignoreUnknownKeys = true` dropped it on every read while the device inferred the same facts from strings. `LiveSituation` now models it; the string paths stay as the fallback for older rows. Every field is nullable and `liveSituation` can return null outright — absence must degrade, never read as zero.

**The payload's `team1*` slots belong to the first-innings side, not the batting side**, and its strings never say who is batting. `battingSlot` (`LiveUpdateScore.kt`) reads `situation.battingTeamIsTeam1` first and falls back to matching the live `score` line against each side's latest innings — exact, but string equality, so a formatting drift takes it out silently. Do not compare names: `team1Name` is a short code ("EZONE", "RSAA") matching no full team name; every join onto the app's teams goes by `teamId`.

**Match-cache keys are constants.** `LiveUpdateSources` used `"live"`; `MatchCacheService` is keyed `"Live"`. `cachedLiveMatch()` therefore returned null on every device, costing the card its series name, **team crest** (`logoTeamName` joins through the cached match by team id) and format at once. Use `MatchCacheService.LIVE` / `UPCOMING` / `RECENT`; never write the literal.

## Match alerts

**Server-sent FCM data messages to a per-match topic.** Until September 2026 nothing sent them: `BouncerFirebaseMessagingService` handled `wicket`/`boundary`/`milestone`/`match_start`/`match_end`, but the only server push was the `cricket-live-updates` live-count broadcast (a widget refresh).

- `match-sync` sends wicket/boundary from `classifyEvent` of the raw line, on a row's **first** write only (a 409 means another execution already alerted); milestones when batter runs cross 50/100/150/200 between runs (in memory; a cold container seeds rather than alerts); and the result on the once-per-match finalising run.
- The match-list poll sends `match_start` when an id first appears live (`startedIds` in `poll-state`, 6-day memory; the first run after deploy records without sending).
- Deliveries older than `ALERT_MAX_AGE_MS` (5 min) are not alerted, so a Cricbuzz backlog republish does not burst.
- Topic = `match_{id}` (`matchTopic`, in the vendored `fcm.js` and mirrored in Kotlin, both pinned by `FcmJsVendorParityTest`).
- The server sends every event regardless of listeners; the device decides in `shouldShowMatchAlert` (per-match prefs × the Settings switches, cosmetic until then × quiet hours).
- Both senders need the **secret** `FIREBASE_SERVICE_ACCOUNT_JSON` + `FIREBASE_PROJECT_ID`. Secret values read back empty from the API, so check the run log: each run logs `FCM not configured` when missing.
- **The bell opens `MatchFollowSheet`**, which sets two independent things in `MatchSubscriptionManager`: `subscriptions` (the Live Update card) and `alertPrefs` (which alerts; any on ⇒ subscribed to the topic). `ScorePollingWorker` no longer posts notifications.

**Match reminders are listed at Notifications › Reminders.** `ReminderBottomSheet` used to schedule an alarm and record nothing (`RemindersManager.saveReminder` had no caller) and `RemindersActivity` was never launched. The sheet now upserts into `RemindersManager`; `upcoming()` drops fired ones.
