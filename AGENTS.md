# Bouncer common guidance

- `android/`: native Kotlin/Compose client; read `android/CLAUDE.md` and relevant `android/.claude/rules/` for platform details.
- `ios/`: native SwiftUI app/project and tests; retained widget/Live Activity and cricket-api sources are dormant unless explicitly brought into scope.
- `appwrite/`: shared backend functions and scheduled data tools. `shared/backend.json` is the single public client configuration default; keep `node shared/check-backend-config.mjs` passing.
- Keep provider credentials and Appwrite server keys server-side. Local Android SDK/signing/Sentry upload configuration belongs in ignored `android/local.properties`; iOS signing stays local. Never import user state, build products or Git directories.
- Read affected code before editing; make the smallest correct change and fix root causes. Preserve native models, offline data and backend contracts across clients.
- Run Android Gradle commands from `android/` using JDK 21. Backend commands and scheduled tools use root `appwrite/` paths. CI/release numbering remains in root `.github/workflows/ci.yml`.
- Run scoped builds/tests/lint before claiming completion; report commands and results. Use the shared device-testing queue when required by the active task; do not start competing heavy jobs.
- Do not deploy, publish, merge or push without authorization in the active request. Keep original recovery checkouts untouched during migrations.
