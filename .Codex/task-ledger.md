# Goal: shared Bouncer Android/iOS repo and real-data iOS matches/detail

## Units
- [ ] U1: monorepo/config/CI | files: root layout, .github, shared, Android Gradle path inputs | acceptance: Android history preserved, sanitized iOS, no relocation-only Play publish
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
