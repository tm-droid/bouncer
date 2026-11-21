# Making `tm-droid/bouncer` public: readiness check

Scan date: 2026-11-21. Scanned a mirror clone (all refs, 1,112 commits, 51 MiB pack) with
`gitleaks git --redact` plus filename and pattern greps over the full history.

Why consider it: public repositories get standard GitHub-hosted runner minutes free. This repo
spends about 250 billed minutes a month (CI ~140, Dependabot ~100 before the monthly switch, the
logo job ~37). The scheduled data jobs already run free in the public `bouncer-archive` and
`bouncer-history` repositories.

## Findings

| # | What | Where | Verdict |
|---|---|---|---|
| 1 | RapidAPI key (`cricket-api-free-data.p.rapidapi.com`) | `app/src/main/kotlin/com/tanush/bouncer/managers/TeamDirectory.kt`, commit `c939b754`, 2026-02-28. Not in the current tree. | **Resolved.** The key still answered on 2026-11-21 and was then revoked: it now gets HTTP 403. It stays readable in history but no longer works. |
| 2 | Firebase Android API key (`AIza…`) | `app/google-services.json` (history), `android/app/google-services.json` (current tree) | Not a secret by design: it ships inside every APK. Before going public, restrict it in Google Cloud Console (Android app restriction: package name and SHA-1, and limit it to the Firebase APIs used). |
| 3 | `SENTRY_CLI_SHA256` flagged as a Sentry token | `.github/workflows/ci.yml` | False positive. It is a checksum of the Sentry CLI download. |

None found: private keys (`BEGIN … PRIVATE KEY`), keystores (`*.jks`, `*.keystore`, `*.p12`),
`local.properties`, `.env` files, service-account JSON, GitHub tokens, OpenAI-style keys, Appwrite
API keys (`standard_…`). The signing keystore, the Play service account, the Sentry auth token and
the Appwrite API key exist only as Actions secrets, and those stay private on a public repository.

## Other exposure to weigh (not secrets)

- Appwrite project ID and endpoint, the Sentry DSN, and the Play service-account email are in
  the tree. All are identifiers, not credentials, and they also ship in the app.
- The whole Android and iOS source, `android/.claude/`, `android/other-apps/`, and `docs/` become
  readable. Choose a licence, or state "all rights reserved" in the README.
- Pull requests from forks: `pull_request` runs from forks get no secrets and a read-only token,
  so the CI publish steps cannot be reached from a fork. Keep it that way, and never switch CI to
  `pull_request_target`.
- Public repositories get free Copilot code review and Dependabot runs too.

## Checklist

The repository went public on 2026-11-21, after items 1 and 3. Items 2, 4 and 5 are still open.

1. Revoke the RapidAPI key from finding 1 (or confirm it was already revoked).
2. Restrict the Firebase API key from finding 2.
3. Re-run `gitleaks git --redact` on a fresh mirror clone. Expect only findings 2 and 3.
4. Add a LICENSE (or an all-rights-reserved note).
5. Review `android/.claude/` and `android/other-apps/` for anything personal. `.claude/` holds only
   agent rules, skills, hooks and settings. `other-apps/` holds 23 screenshots (28 MB) of Cricbuzz,
   Cricinfo and Cricket Guru, which are better kept out of a public repository.
6. Settings → General → Danger Zone → Change visibility. This is reversible, but anything cloned or
   cached while the repo is public stays public.
