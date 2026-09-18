# Play Store Listing — Bouncer

## Data Safety Form

All answers derived from codebase audit (June 2026).

### Sentry Instrumentation — What Each Feature Captures

| InstrumentationFeature | What is captured | What is NOT captured |
|------------------------|-----------------|----------------------|
| `OKHTTP` | HTTP request URL, method, status code, duration (performance spans + custom breadcrumb interceptor in `HttpClientProvider`) | Request/response bodies, Authorization header values, cookies |
| `DATABASE` | SQL query strings (table names, column names, WHERE clause structure), duration, success/failure | Row values returned by queries |
| `FILE_IO` | File read/write operation type, file path, byte count, duration | File content |

`setSendDefaultPii(true)` additionally captures: IP address, user-agent string.  
Screenshots on crash enabled (`attach-screenshot=true`).  
Session replay enabled with `mask-all-text=true` and `mask-all-images=true` (UI text and images redacted).

Breadcrumb PII: `HttpClientProvider` logs full request URL (e.g. `https://api.groq.com/openai/v1/chat/completions`). No player names or match data appear in URLs. FCM breadcrumbs contain `type` and `match_id` only — not personally identifying.

---

### Data Safety Answers (Google Play Console)

**Personal info** (name, email, phone, address)
- Collected: No — no user registration or account system

**Financial info**
- Collected: No

**Health and fitness**
- Collected: No

**Messages**
- Collected: No

**Photos and videos**
- Collected: No (highlight video URLs fetched and streamed, not stored locally)

**Audio files**
- Collected: No

**Files and docs**
- Collected: No

**Calendar**
- Collected: No

**Contacts**
- Collected: No

**App activity**
- Collected: Yes
- What specifically: App crashes, ANRs, screen views, button taps (via Sentry session replay and user interaction breadcrumbs)
- Shared with: Sentry (sentry.io) — crash reporting and performance monitoring
- Ephemeral: No — retained by Sentry per their data retention policy
- Encrypted in transit: Yes (HTTPS/TLS)
- Can user request deletion: No self-service (data is anonymous, no user ID)

**App info and performance**
- Collected: Yes
- What specifically: Crash logs, diagnostics, performance traces (HTTP request durations, DB query durations, file I/O durations), app version
- Shared with: Sentry (sentry.io)
- Encrypted in transit: Yes
- Can user request deletion: No

**Device or other identifiers**
- Collected: Yes
- What specifically: A random UUID generated on first launch (`appwrite_device_id` in SharedPreferences). Used solely to sync the user's favourite teams list to Appwrite. Not linked to a real-world identity.
- Shared with: Appwrite (appwrite.io) as the row identifier for favourite teams document
- Encrypted in transit: Yes (HTTPS)
- Can user request deletion: No self-service (anonymous UUID, no account)

**Location**
- Collected: No (no location permissions declared)

**Web browsing history**
- Collected: No

---

### Privacy Policy Disclosure Requirements

These items must appear in the Privacy Policy:

1. **Sentry crash reporting** — crash logs, ANRs, performance traces, session replay (masked), screenshots on crash, HTTP URLs, SQL query patterns, file I/O patterns
2. **External LLM calls** — match context (team names, player names, ball-by-ball scores) is sent to Groq (groq.com), Cerebras (cerebras.ai) and SambaNova (sambanova.ai) for AI ball commentary generation. Since 2026-08-04 these calls are made by the Appwrite backend, not by the device, and no user identity is attached.
3. **Highlightly API** — team names sent to Highlightly for fuzzy match highlight lookup
4. **Firebase Cloud Messaging** — device registers with FCM (Google) to receive push notifications for match events. FCM token not forwarded to any backend.
5. **Appwrite device sync** — anonymous device UUID synced to Appwrite cloud for favourite teams persistence

---

## Store Listing Content

### Short Description
**70 chars (limit: 80)**

```
It hits different. Live cricket scores, commentary and win probability
```

Alternates:
- `Live cricket scores, ball-by-ball commentary and win probability in one app` (75)
- `Cricket that hits different — live scores, commentary, win probability` (70)

"It hits different" is the tagline used on the closing beat of the ad and the
Play Store showcase video. Keep store copy and video on the same line.

**Promo video** (Play Console → Store listing → Promo video):
`https://youtu.be/8hGBRcYCWzo` — rendered from `my-video/` (Remotion, see its README);
thumbnail is the `Thumbnail` still there. Re-render and re-upload when a shown screen changes.

**Phone screenshots** (Play Console → Store listing → Phone screenshots; 2–8, PNG/JPEG, 9:16 or
16:9, each side 320–3,840 px, ≤ 8 MB): rendered from `my-video/` with `npm run screenshots`, which
writes seven 1080×1920 PNGs to `my-video/out/screenshots/` and fails on any file outside Play's
rules. Upload in file order — `01-home`, `02-overview`, `03-commentary`, `04-scorecard`,
`05-series`, `06-news`, `07-widgets`. Each is the ad's rebuilt screen (same IND v AUS fixture) in a
device frame under that beat's headline; the copy claims only what the app does. The 1080×2400
`Stills/*` are 9:20 and Play refuses them. Same rule as the video: re-render when a shown screen
changes.

---

### Full Description
**~2,000 chars (limit: 4,000) — paste into Play Console → Store listing → Full description**

```
It hits different.

Bouncer follows the cricket, not the clutter. Live scores, ball-by-ball commentary, full scorecards and the numbers that actually explain a match — in one fast, quiet app.

LIVE SCORES
Live, upcoming and recent matches on a single screen, refreshed as the game moves. Tests, ODIs, T20s, The Hundred and domestic competitions — and a Test side's score shows both innings, not just the last one.

BALL-BY-BALL COMMENTARY
Every delivery, in order, grouped by over, with wickets and boundaries called out as they happen.

WIN PROBABILITY
Live win probability that updates as the match turns, plus a swing chart showing where the game actually changed.

FULL SCORECARDS
Batting and bowling cards, extras, fall of wickets and partnerships, run rate, required rate and projected score.

HIGHLIGHTS AND NEWS
Highlight clips from the matches you're following, plus cricket news in the app.

PLAYERS AND SERIES
Player profiles with career numbers and recent form, side-by-side player comparison, series pages, fixtures and points tables.

FOLLOW WHAT YOU CARE ABOUT
Pick your favourite teams, set match reminders, and get notifications only for the matches you subscribe to.

ON YOUR HOME SCREEN AND IN YOUR CAR
Home-screen and lock-screen widgets, a countdown widget for the next match, an ongoing live-score notification you can pin, and Android Auto support for scores on the drive.

BUILT TO STAY OUT OF THE WAY
Light and dark themes that both look right, offline caching so the last score is there when signal isn't, and no account to create. Open the app and the cricket is there.

No sign-up. No clutter. Just the match.
```

**Claims discipline.** Every line above is backed by shipped code. Do not
reintroduce the copy this block replaced: "Powered by Llama 3.3 70B / Groq,
Cerebras, SambaNova" (device keys removed 2026-08-04 — generation is server-side,
and most stored commentary rows are raw Cricbuzz passthrough), "custom neural
network ... 35 contextual features ... Monte Carlo fallback" (overstates the
`bouncer-win-probability` function), and "GLASSMORPHISM DESIGN / Material You
dynamic theming" (removed by the August 2026 restraint pass).

---

### What's New
**~390 chars (limit: 500)**

```
First release.

• Live scores for Tests, ODIs, T20s, The Hundred and domestic cricket
• Ball-by-ball commentary, in match order
• Live win probability with a swing chart
• Full scorecards: extras, fall of wickets, partnerships
• Highlights, news, player profiles and comparison
• Home-screen, lock-screen and countdown widgets
• Android Auto, plus light and dark themes
```

---

### Permissions Rationale (for Play Console)

| Permission | Why needed |
|------------|------------|
| `INTERNET` | Live score polling, AI commentary, widget sync |
| `VIBRATE` | Notification haptic feedback (wickets, boundaries) |
| `POST_NOTIFICATIONS` | Match event push notifications (Android 13+) |
| `FOREGROUND_SERVICE` | `LiveUpdateService` — the ongoing live-score notification for followed matches |
| `FOREGROUND_SERVICE_DATA_SYNC` | Same service; it holds the Realtime connection that feeds that notification. See "Foreground service declaration" below |
| `RECEIVE_BOOT_COMPLETED` | Restart polling workers after device reboot |
| `SCHEDULE_EXACT_ALARM` | Match reminder alarms at exact scheduled time |
| `USE_EXACT_ALARM` | Required companion to SCHEDULE_EXACT_ALARM |

---

### Foreground service declaration

**Play Console → Monitor and improve → App content → Foreground service permissions.**
Separate from the store listing above. Required because the app targets Android 14+.

The app declares exactly one foreground service type. `LiveUpdateService`
(`services/LiveUpdateService.kt`, manifest `android:foregroundServiceType="dataSync"`) is the only
service that calls `startForeground`, and it passes `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. Nothing
else in the app uses a typed foreground service — background score polling, series sync and player
caching are all WorkManager, which needs no declaration.

**Use case:** Network processing → Other.
Custom text: `Real-time live score updates over a websocket for matches the user follows`.

Not backup/restore, not import/export, not local processing. The service neither uploads nor
writes files; it receives score rows over a connection the user opened by following a match.

**Description of functionality**

```
Bouncer shows live cricket scores. When a user taps the bell on a live match ("Follow live
updates"), the app starts a foreground service that keeps a websocket connection open to our
backend (Appwrite Realtime) and receives that match's score row every time it changes, about once
a minute while play is on. Each update is rendered into an ongoing "Live Update" notification that
stays in the notification shade and on the lock screen: current score, batters, bowler, recent
balls and the match situation. The service runs only while the user is following at least one live
match. It stops itself when the last followed match ends, when the user taps Stop or swipes the
notification away, and when the user unfollows the match. Nothing is uploaded; the connection only
receives score data for matches the user asked to watch.
```

**User impact if the task is deferred**

```
The Live Update notification does not appear, or appears with a stale score, so the user misses
wickets and boundaries in a match they explicitly asked to follow. Following a match is the only
control the feature has; there is no other way to get the live card.
```

**User impact if the task is interrupted**

```
The notification stays on screen but freezes at the last score received. The app falls back to a
periodic background poll, so the card catches up slowly rather than ball by ball, and the service
is re-armed the next time the app is opened. The app already handles this deliberately for the
Android 15 six-hour data-sync limit: onTimeout detaches the service from the foreground, leaves
the cards posted and switches to the poll, rather than dropping the user's followed matches.
```

**Video:** `distribution/declarations/fgs-datasync.mp4` — a real screen recording of the debug
build, 41 s, showing Home, the bell tap that starts the service, the resulting ongoing notification,
and Stop. Play takes a **link**, so upload it unlisted to the same YouTube channel as the promo
video and paste the URL here once submitted. See `distribution/declarations/README.md` for how to
re-record it.

**If a reviewer asks why not FCM or a user-initiated data transfer job:** a score changes every
ball for the length of a match, which is hundreds of pushes per match, and a user-initiated data
transfer job is for a bounded transfer with a known end, which a live match does not have. The
service is user-initiated (the bell), user-visible (the notification is its only output) and
self-terminating (it stops when the match does).
