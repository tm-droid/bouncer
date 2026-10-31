# Moved from CLAUDE.md

## Git Workflow
- After completing requested changes, commit and push directly to main (do not create a PR unless explicitly asked).
- Always run a clean build and tests before pushing; do not push a broken build. CI gates the Play upload on unit tests, lint, detekt and every function's `node --test` (`ci.yml`: they run before `bundleRelease` in the same Gradle invocation, and the upload steps come after it), so run `./gradlew lintDebug` as well as detekt before pushing — lint was red on main for weeks while it gated nothing.
- CI is one path-aware job (`ci.yml`), because this repo's Actions minutes are billed (~2,170 in the first half of October 2026, before this change).
  - A push that changes no app path (`app/`, `gradle/`, `distribution/`, the Gradle files) does not build a bundle or upload to Play.
  - A functions-only push runs the function suites and the unit tests (parity), and no lint, detekt or bundle.
  - A push touching only `paths-ignore` (`*.md`, `.claude/`, `.devcontainer/`, `my-video/`, `android-dev-mcp-server/`) starts no run.
  - To re-ship after a failed app run, run CI by `workflow_dispatch` with `publish` ticked.
  - A push to main whose exact commit already passed CI (typically a branch checked by dispatch, then fast-forwarded onto main) ships that run's kept `release-aab` artifact instead of rebuilding: no Gradle, no tests, just the Play upload. Fast-forward, don't merge-commit, or the SHA differs and it builds again.
  - A push to main never cancels an earlier run.
  - A bundling run lints once: `lintRelease` with `-x lintVitalRelease` (full lint on the release variant is a superset of lintVital). A run that does not bundle lints debug. Function suites run in parallel, with npm's cache kept between runs.
- The Gradle dependency graph is submitted by CI's release build (setup-gradle). The repository's "Automatic dependency submission" setting stays off: it was a second Gradle run on every push.
- Codespaces is the dev box. Leave **prebuilds off**: they bill Actions minutes.
- Every push to main uploads to Play with `distribution/whatsnew/whatsnew-en-US` as "What's new". A commit touching `app/src/main/` must update that file (user-facing wording, ≤500 chars — see `distribution/whatsnew/README.md`). `.claude/hooks/release-notes-check.sh` blocks the commit otherwise; prefix `SKIP_RELEASE_NOTES=1` only when nothing user-visible changed.
- **Retiring old builds.** A push with a `Force-Update: yes` commit trailer uploads at in-app update priority 5, and every Play install then shows Play's immediate update (`AppUpdateGate`). For installs Play cannot reach (sideloaded APKs, a stale Play cache), `node appwrite/tools/set-min-version.mjs <versionCode>` publishes a minimum that blocks older builds behind an "Update Bouncer" screen. Publish it only once that build has cleared closed-testing review. Builds below 1000 (local) are never gated.
- The Play bundle's Appwrite ids and Sentry DSN come from `local.properties.template`, not from GitHub secrets; `verifyReleaseConfig` fails a release artifact built without them. A green CI run does not mean the bundle can reach the backend — see "The Play build takes its ids from the template" in `.claude/rules/api-keys-and-setup.md`.
