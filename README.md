# Bouncer

Native Android and iOS cricket clients sharing one Appwrite backend.

| Path | Responsibility |
| --- | --- |
| `android/` | Kotlin/Compose app, Gradle, Android assets, tools and platform guidance |
| `ios/` | SwiftUI app, Xcode project, assets and tests; dormant extensions retained |
| `appwrite/` | Backend functions, deployment configuration and scheduled data tools |
| `shared/` | Public client configuration and cross-platform resource contracts |
| `.github/` | Path-aware checks, Android releases and scheduled backend workflows |

Start with [the shared configuration contract](shared/README.md), [Android setup](android/README.md), and the [native iOS foundation specification](docs/superpowers/specs/2026-10-31-shared-ios-design.md). Open `ios/Bouncer.xcodeproj` for iOS work. Run Android commands from `android/` using JDK 21; local SDK/signing/Sentry settings belong in ignored `android/local.properties`.

Public Appwrite defaults live in `shared/backend.json`. Provider credentials and server API keys belong only in backend environments. The retained `ios/cricket-api/` source is historical and is not part of the app or deployment setup. Clients consume the shared backend; no Vercel or client AI credentials are required.

CI preserves Android release numbering in `.github/workflows/ci.yml`. Android changes run Android checks, backend changes also run the Android parity tests, and iOS/shared changes run iOS checks. The initial directory migration cannot automatically publish a Play release. Scheduled archive, history and model jobs continue from root `appwrite/` paths.
