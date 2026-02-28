# 🏏 Bouncer

A premium Android cricket companion app built with Jetpack Compose. Bouncer delivers real-time live scores, deep match analytics, AI-powered win probability tracking, and a beautifully crafted dark-glass UI — all in one place.

---

## Features

### Live Scores & Matches
- Real-time live score updates with ball-by-ball commentary
- All matches view — international, domestic, and leagues
- Match cards with live indicators, run rates, and over-by-over progress
- Favorites — pin teams or matches to track them instantly

### Deep Match Analytics
- **Win Probability Engine** — ball-level probability tracking using pitch, dew, powerplay, and momentum factors
- **Pitch Analysis** — surface type profiling and its effect on match outcome
- **Team Analysis** — historical head-to-head, batting/bowling strength indicators
- **Player Detail Screen** — career stats, recent form, and comparative metrics
- **Stats Overview & Detail** — full batting and bowling breakdowns per innings

### AI & Intelligence
- **AI Insights / DRS Screen** — AI-generated match commentary, key moments, and DRS analysis
- **Predictive Forecast** — MCP-powered AI match forecasts
- **Intelligence Feed** — curated cricket intelligence and trending stats
- **Elite Tier AI Stats** — advanced analytics for premium experience

### Discovery & Extras
- **Squad Screen** — full team squads with player cards
- **Reminders** — set match reminders for upcoming fixtures
- **Achievements & Trophy Room** — gamified cricket engagement
- **Onboarding** — smooth first-launch experience
- **Home Widgets** — Glance-powered Android home screen widgets

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Architecture | MVVM + StateFlow |
| Navigation | Compose Navigation |
| Networking | Retrofit 2 + OkHttp + Ktor |
| Image Loading | Coil |
| Scraping | Jsoup (CricBuzz, ESPNCricInfo) |
| Serialisation | Kotlin Serialization |
| Coroutines | Kotlinx Coroutines |
| Crash Reporting | Sentry |
| Widgets | Jetpack Glance |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 35) |

---

## Project Structure

```
android/
├── app/
│   └── src/main/kotlin/com/tanush/bouncer/
│       ├── MainActivity.kt          # Entry point, nav graph, edge-to-edge
│       ├── analysis/                # Win probability, pitch & team analysis
│       ├── data/                    # CricketRepository, TeamDirectory
│       ├── network/                 # Retrofit, scrapers (CricBuzz, CricInfo, RapidAPI, Vercel)
│       ├── models/                  # Data models
│       ├── services/                # Background services
│       ├── viewmodels/              # LiveActivityViewModel + others
│       ├── logic/                   # Pure cricket logic
│       └── ui/
│           ├── screens/             # All app screens (20+)
│           ├── components/          # Reusable Compose components
│           ├── theme/               # BouncerTheme, colours, typography
│           ├── widgets/             # Glance home screen widgets
│           └── adaptive/            # Foldable / large screen support
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── domestic_teams.json / international_teams.json / league_teams.json / women_teams.json
```

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2) or later
- JDK 17
- Android SDK 35

### Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/tm-droid/bouncer.git
   cd bouncer
   ```

2. **Add API keys**

   Create a `local.properties` file in the project root (already git-ignored):
   ```properties
   OPENROUTER_API_KEY=your_openrouter_key_here
   RAPIDAPI_KEY=your_rapidapi_key_here
   ```

   | Key | Source | Used For |
   |---|---|---|
   | `OPENROUTER_API_KEY` | [openrouter.ai](https://openrouter.ai) | AI insights & predictive forecasts |
   | `RAPIDAPI_KEY` | [rapidapi.com](https://rapidapi.com) | Cricket data API |

3. **Build & run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or open in Android Studio and press **Run**.

---

## Data Sources

Bouncer aggregates cricket data from multiple sources:

- **CricBuzz** — live scores and commentary (scraped via Jsoup)
- **ESPNCricInfo** — player stats and match details (scraped via Jsoup)
- **RapidAPI** — structured match data
- **Vercel API** — custom hosted cricket data endpoint
- **Cricsheet** — historical ball-by-ball data for analysis

---

## Build Variants

| Variant | Notes |
|---|---|
| `debug` | Full logging, no PNG crunching, faster builds |
| `release` | ProGuard minification + resource shrinking enabled |

---

## License

Private repository — all rights reserved.
