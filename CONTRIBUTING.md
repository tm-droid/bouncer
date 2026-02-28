# Contributing to Bouncer

Thanks for your interest in contributing! This document covers everything you need to get up and running.

---

## 👀 Before You Start

- Check existing [issues](https://github.com/tm-droid/bouncer/issues) and [pull requests](https://github.com/tm-droid/bouncer/pulls) to avoid duplicate work
- For significant features, open an issue first to discuss before building
- Small fixes (typos, minor bugs) can go straight to a PR

---

## 🛠 Development Setup

### Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Ladybug (2024.2) or later |
| JDK | 17 |
| Android SDK | API 35 |

### Setup

```bash
git clone https://github.com/tm-droid/bouncer.git
cd bouncer
```

Create `local.properties` in the root:

```properties
sdk.dir=/path/to/Android/sdk
OPENROUTER_API_KEY=your_key
RAPIDAPI_KEY=your_key
```

Build:

```bash
./gradlew assembleDebug
```

---

## 🌳 Branch Strategy

| Branch | Purpose |
|---|---|
| `main` | Stable, production-ready code |
| `develop` | Integration branch for upcoming release |
| `feature/your-feature` | Feature branches off `develop` |
| `fix/your-fix` | Bug fix branches off `main` or `develop` |
| `v*.*.*` tags | Release tags — trigger the release workflow |

Branch naming: `feature/win-probability-chart`, `fix/crash-match-detail`, `chore/update-dependencies`

---

## 📝 Code Style

- **Kotlin** — follow `kotlin.code.style=official` (Kotlin coding conventions)
- **Compose** — one `@Composable` per file where practical, follow slot API patterns
- **No hardcoded strings** in UI — use constants or string resources
- **No API keys** in source code — always via `local.properties` / `BuildConfig`
- Run `./gradlew lint` before opening a PR; fix or justify any new warnings

---

## 🧪 Testing

```bash
# Run all unit tests
./gradlew test

# Run a specific test class
./gradlew test --tests com.tanush.bouncer.analysis.WinProbabilityCalculatorTest
```

Add unit tests for:
- New analysis / logic code
- Repository methods
- Any non-trivial utility functions

---

## 📦 Submitting a PR

1. Fork the repo and create your branch from `develop` (or `main` for hotfixes)
2. Make your changes, add tests where appropriate
3. Run `./gradlew lint test assembleDebug` and verify everything passes
4. Open a Pull Request against `main` (or `develop` for features)
5. Fill in the PR template completely
6. Request review from [@tm-droid](https://github.com/tm-droid)

---

## 🚫 What We Won’t Accept

- Hardcoded credentials or API keys
- Breaking changes without discussion
- PRs that don’t include the PR template checklist
- Code that introduces lint errors without justification
