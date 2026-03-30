# 🏏 Bouncer

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Bouncer Logo" width="96"/>
</p>

<p align="center">
  <strong>Premium Android cricket companion — live scores, AI win probability, deep analytics.</strong>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-API%2026%2B-brightgreen?logo=android&logoColor=white"/>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white"/>
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-2024.10-4285F4?logo=jetpackcompose&logoColor=white"/>
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-orange"/>
  <img alt="Target SDK" src="https://img.shields.io/badge/targetSdk-35-blue"/>
  <img alt="CI" src="https://github.com/tm-droid/bouncer/actions/workflows/ci.yml/badge.svg"/>
  <img alt="License" src="https://img.shields.io/badge/license-Private-red"/>
</p>

---

## ✨ Features

### 🔴 Live Scores & Matches
- Real-time live score updates with ball-by-ball commentary
- Unified match feed — international, domestic, T20 leagues, and women's cricket
- Match cards with animated live indicators, run rate, and CRR/RRR
- Favorites — pin teams and tournaments for instant access
- All Matches screen with smart filtering and search

### 🧠 AI & Intelligence
- **Win Probability Engine** — ball-level probability tracking factoring pitch condition, dew, powerplay phase, death overs momentum, and team form
- **Pitch Analysis** — surface profiling (batting-friendly / balanced / bowling-friendly) with outcome impact modelling
- **Team Analysis** — historical head-to-head records, batting and bowling strength indices
- **AI Insights / DRS** — AI-generated key moment commentary, review decisions, and tactical breakdowns
- **Predictive Forecast** — MCP-connected AI match outcome forecasting
- **Intelligence Feed** — curated cricket intelligence, trending stats, and news
- **Elite Tier AI Stats** — advanced probabilistic analytics overlay

### 📊 Analytics & Stats
- **Player Detail** — career stats, recent form graph, batting/bowling comparison
- **Stats Overview & Deep Dive** — full innings breakdowns, partnership maps, over-by-over run charts
- **Duckworth-Lewis card**, Economy Tracker, Boundary Map, Death Overs analysis
- **AR Ball Tracking** — experimental ball trajectory visualization
- **Batsman vs Bowler matchup cards**

### 🏟 Discovery
- **Squad Screen** — full team lineups with player cards and role indicators
- **Match Info** — venue, toss, umpires, weather context
- **Reminders** — schedule local notifications for upcoming fixtures
- **Achievements & Trophy Room** — gamified cricket engagement milestones
- **Executive Box** — premium curated experience view

### 🎨 UI & Polish
- Custom **glass-morphism dark theme** with animated gradient backgrounds and subtle grid texture
- **Edge-to-edge** layout with proper insets handling
- **Adaptive layout** support for tablets and foldables  
- Smooth **Crossfade / SharedElement** transitions throughout
- **Home screen widgets** — Glance-powered live score and match status widgets
- Full interactive **onboarding** flow on first launch
- Custom cricket-themed loading spinners and skeleton loaders

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                         │
│  Jetpack Compose · Material 3 · Navigation Compose  │
│  20+ Screens · Reusable Component Library           │
├─────────────────────────────────────────────────────┤
│               ViewModel Layer                       │
│  LiveActivityViewModel · MainViewModel              │
│  StateFlow · Kotlin Coroutines                      │
├─────────────────────────────────────────────────────┤
│              Repository Layer                       │
│  CricketRepository · TeamDirectory                  │
│  Single source of truth, in-memory caching          │
├────────────────┬────────────────┬───────────────────┤
│ CricBuzz       │  ESPNCricInfo  │  RapidAPI         │
│ Scraper        │  Scraper       │  Retrofit Client  │
│ (Jsoup)        │  (Jsoup)       │  + Vercel API     │
└────────────────┴────────────────┴───────────────────┘
```

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.1.0 |
| UI Framework | Jetpack Compose BOM 2024.10.01 · Material 3 |
| Architecture | MVVM + StateFlow + Coroutines |
| Navigation | Compose Navigation 2.8.3 |
| Networking | Retrofit 2.11 · OkHttp · Ktor 3.0 |
| Scraping | Jsoup 1.18.3 (CricBuzz + ESPNCricInfo) |
| Image Loading | Coil 2.7.0 |
| Serialisation | Kotlin Serialization 1.7.3 |
| Adaptive UI | WindowSizeClass · Glance Widgets |
| Crash Reporting | Sentry |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 35) |
| Build System | Gradle 8.7.3 · AGP 8.7.3 · JDK 17 |

---

## 📁 Project Structure

```
android/
├── app/src/main/kotlin/com/tanush/bouncer/
│   ├── BouncerActivity.kt       # Entry point — nav graph, edge-to-edge, onboarding
│   ├── analysis/                # Win probability, pitch analysis, team analysis
│   ├── data/                    # CricketRepository, TeamDirectory
│   ├── network/                 # Retrofit, Jsoup scrapers, RapidAPI, Vercel, Cricsheet
│   ├── models/                  # All data models
│   ├── services/                # Background services
│   ├── viewmodels/              # LiveActivityViewModel, MainViewModel
│   ├── logic/                   # Pure cricket domain logic
│   └── ui/
│       ├── screens/             # 20+ full-page Compose screens
│       ├── components/          # 50+ reusable Compose components
│       ├── theme/               # BouncerTheme, colours, gradients, animations
│       ├── widgets/             # Glance home screen widgets
│       └── adaptive/            # Foldable + large screen support
├── app/build.gradle.kts         # App-level build config
├── build.gradle.kts             # Root build config
├── settings.gradle.kts          # Module declarations
├── gradle.properties            # JVM args, caching, parallel build flags
└── *_teams.json                 # Team data — domestic, international, leagues, women's
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Ladybug (2024.2) or later |
| JDK | 17 |
| Android SDK | API 35 |
| Gradle | Auto-downloaded via wrapper |

### 1. Clone

```bash
git clone https://github.com/tm-droid/bouncer.git
cd bouncer
```

### 2. Configure API keys

Create `local.properties` in the project root (git-ignored):

```properties
sdk.dir=/path/to/your/Android/sdk
OPENROUTER_API_KEY=your_key_here
RAPIDAPI_KEY=your_key_here
```

| Key | Where to get it | Used for |
|---|---|---|
| `OPENROUTER_API_KEY` | [openrouter.ai/keys](https://openrouter.ai/keys) | AI insights, predictive forecasts |
| `RAPIDAPI_KEY` | [rapidapi.com](https://rapidapi.com) | Structured cricket match data |

### 3. Build

```bash
# Debug build
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Lint check
./gradlew lint

# Install on connected device
./gradlew installDebug
```

Or open the project in Android Studio and press **Run ▶**.

---

## 🔌 Data Sources

| Source | Method | Data |
|---|---|---|
| CricBuzz | Jsoup scraping | Live scores, commentary, match state |
| ESPNCricInfo | Jsoup scraping | Player stats, match details, squads |
| RapidAPI | REST (Retrofit) | Structured match and player data |
| Vercel endpoint | REST (Retrofit) | Custom hosted cricket data |
| Cricsheet | File-based fetch | Historical ball-by-ball data for analysis |

---

## 📦 Build Variants

| Variant | ProGuard | Notes |
|---|---|---|
| `debug` | ✗ | Full logging, fast incremental builds |
| `release` | ✓ | Minification + resource shrinking enabled |

---

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, code style, and PR guidelines.

---

## 🔒 Security

See [SECURITY.md](SECURITY.md) for how to report vulnerabilities.

---

## 📄 License

Private repository — © 2026 tm-droid. All rights reserved.
