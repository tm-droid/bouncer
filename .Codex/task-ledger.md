# Goal: shared Bouncer Android/iOS repo and real-data iOS matches/detail

## Units
- [x] U1: monorepo/config/CI | files: root layout, .github, shared, Android Gradle path inputs | acceptance: Android history preserved, sanitized iOS, no relocation-only Play publish
- [ ] U2: shared detail service | files: appwrite | acceptance: cache/contract/deadline/quota tests, existing Node suites
- [ ] U3: iOS real-data slice | files: ios | acceptance: fixtures+lifecycle tests, build/test/smoke after queue grant
- [ ] U4: Android shared detail fallback | files: android app source/tests | acceptance: regression checks and Android build/lint/detekt after queue grant
- [ ] U5: review/verification/handoff | files: docs and integration fixes | acceptance: fresh review, appropriate checks, documented cloud setup

## Decisions
- Existing remote is tm-droid/bouncer; preserve it and its Android history.
- Native SwiftUI, Android visual identity adapted; local prefs; iOS18.2/current IDs; no widgets/push/accounts/full parity/store release.
- Shared on-demand detail now, consumed by both apps; no LLM on request path; cron sole canonical-row writer.
- Private response-cache bucket; no mutation of legacy public bucket permissions.
- Worktree /private/tmp/bouncer-shared-ios, codex/shared-ios-foundation; original parent/Android remain untouched.
- No push/deploy: production side effects are outside original authorization. Prepare concrete instructions instead.
- Testing queue: iOS baseline passed before hold; no heavy jobs now. New builds/simulators wait for explicit queue slot.

## Log
- Recon: Android backend already shares AI/provider keys server-side; iOS uses obsolete endpoints and hardcoded credentials; baseline simulator build PASS.
- U0: isolated branch created at 93549c93; native worktree tool rejected nested repo, so Git worktree fallback used.
- Preflight review: section freshness, membership-outage semantics, independent cleanup, Android outer timeouts/terminal-data acceptance pinned in spec; no missing user decisions.
- U1 layout ready; shared client-config schema frozen; U2 contract fixture frozen with per-section availability/freshness; U2/U3 running independently.
- Ruling: private cache bucket instead of the legacy publicly writable bucket — avoids introducing new trusted cache poisoning risk without breaking legacy clients.
- Testing queue released hold: all heavy commands wrap the shared device-testing-queue.py run --owner bouncer with default state; propagated to every active worker. Overnight checkpoint written at coordinator-requested path.
- U1 checks: actionlint all workflows PASS; shared config guard PASS; parser vendor --check PASS; shell/deploy syntax and Gradle parity/config path existence PASS. Structural commit in serialized index turn, excluding concurrent ios/backend implementation.
- U2 focused service tests 11/11 PASS; route/vendor syntax PASS; full backend suites will use shared queue.
- U1 complete ccd5acbc: all 1280 original tracked paths preserved; actionlint/config/vendor guards and CI path fixtures PASS, no relocation-only auto-publish.
- Tanush ended testing turns (Minecraft complete); builds/suites may run directly. Earlier backend waiting session is no longer accessible and no suite logs exist, so full suites remain unverified and will be rerun via verifier.
- Root checks: node shared/check-backend-config.mjs PASS; git diff --check PASS.
