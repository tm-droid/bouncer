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
2. **External LLM calls** — match context (team names, player names, ball-by-ball scores) sent to Groq (groq.com), Cerebras (cerebras.ai), SambaNova (sambanova.ai) for AI ball commentary generation. No user identity attached.
3. **Highlightly API** — team names sent to Highlightly for fuzzy match highlight lookup
4. **Firebase Cloud Messaging** — device registers with FCM (Google) to receive push notifications for match events. FCM token not forwarded to any backend.
5. **Appwrite device sync** — anonymous device UUID synced to Appwrite cloud for favourite teams persistence

---

## Store Listing Content

### Short Description
**63 chars (limit: 80)**

```
Live cricket scores, AI commentary & win probability
```

---

### Full Description
**Paste into Play Console → Store listing → Full description**

```
Bouncer brings cricket intelligence to your fingertips with real-time live scores, AI-powered ball commentary, and a machine learning win probability engine.

LIVE MATCH COVERAGE
Follow every ball with live scores, ball-by-ball commentary, and full scorecards. Track current batsmen, bowling figures, and partnership stats. Match flow shows DRS reviews, powerplays, and impact substitutions — sourced from CricBuzz and ESPNCricinfo.

AI BALL COMMENTARY
Every delivery gets AI-generated commentary that blends insights from two data sources. Powered by Llama 3.3 70B with automatic failover across Groq, Cerebras, and SambaNova. Commentary is shared across users for near-zero latency delivery.

WIN PROBABILITY ENGINE
A custom neural network trained on historical Cricsheet data calculates real-time win probability for the chasing team after every ball. The model evaluates 35 contextual features: wickets lost, run rates, recent momentum, team strength ratios, and pressure metrics. Falls back to a Monte Carlo simulation while the model warms up.

SMART NOTIFICATIONS
Get alerted the moment it matters: wicket falls (with dismissal type, bowler, runs), boundaries, centuries, half-centuries, and 5-wicket hauls. Configure notifications per match — mute noisy matches, set quiet hours globally.

HOME SCREEN WIDGETS
A live match score widget in three sizes (2×1, 3×2, 4×2) shows the current score, run rate, and team names. A countdown widget shows time to the next scheduled match. Lock screen widget support on Android 14+.

PLAYER INTELLIGENCE
Browse player profiles with career statistics, recent form, and batting and bowling averages. Compare any two players head-to-head. View complete squad rosters for every team.

SERIES & STANDINGS
Follow IPL, international, and bilateral series. Points tables update in real time. Browse upcoming fixtures and past results by series.

HIGHLIGHTS
Watch key match moments and video highlights sourced automatically from Highlightly. Browse by match or series.

ANDROID AUTO
Take the score with you on the road via Android Auto.

OFFLINE-CAPABLE
Aggressive local caching with Room SQLite keeps the app fast even on poor connections. WorkManager background sync keeps data fresh when the app is closed.

GLASSMORPHISM DESIGN
Modern glassmorphism visual language with Material You dynamic theming that adapts to your wallpaper.

IPL · T20I · ODI · Test · International · Bilateral Series
```

---

### What's New
**~380 chars (limit: 500)**

```
• Fixed AI commentary verification — unverified responses no longer cached
• Win probability: ML dispatcher and format propagation fixes
• Match flow: DRS reviews, powerplays, impact substitutions now shown
• Pre-match: pitch reports and team news from ESPNCricinfo
• Player recent form from Cricbuzz
• Toss result and umpire details displayed
```

---

### Permissions Rationale (for Play Console)

| Permission | Why needed |
|------------|------------|
| `INTERNET` | Live score polling, AI commentary, widget sync |
| `VIBRATE` | Notification haptic feedback (wickets, boundaries) |
| `POST_NOTIFICATIONS` | Match event push notifications (Android 13+) |
| `FOREGROUND_SERVICE` | Background score polling service |
| `FOREGROUND_SERVICE_DATA_SYNC` | WorkManager data sync classification |
| `RECEIVE_BOOT_COMPLETED` | Restart polling workers after device reboot |
| `SCHEDULE_EXACT_ALARM` | Match reminder alarms at exact scheduled time |
| `USE_EXACT_ALARM` | Required companion to SCHEDULE_EXACT_ALARM |
