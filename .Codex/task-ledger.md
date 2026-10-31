# Goal: shared Bouncer Android/iOS repo and real-data iOS matches/detail

Status: approved cloud activation, real-data verification, branch publication and fast-forward main merge in progress.
Current workspace: /Users/tanushmalhotra/Documents/Tanush Coding/Bouncer-unified
Branch: codex/shared-ios-foundation

## Units
- [x] U1: monorepo/config/CI | files: root layout, .github, shared, Android Gradle path inputs | acceptance: Android history preserved, sanitized iOS, no relocation-only Play publish
- [x] U2: shared detail service | files: appwrite | acceptance: cache/contract/deadline/quota tests, existing Node suites
- [x] U3: iOS real-data slice | files: ios | acceptance: fixtures+lifecycle tests, build/test/smoke after queue grant
- [x] U4: Android shared detail fallback | files: android app source/tests | acceptance: regression checks and Android build/lint/detekt after queue grant
- [x] U5: review/verification/handoff | files: docs and integration fixes | acceptance: fresh review, appropriate checks, documented cloud setup

- [ ] U6: Appwrite activation/canary | owner: cloud worker | acceptance: private cache, Apple platform, healthy deployment, cadence and Free quota validation, rollback preserved
- [ ] U7: real iOS then Android smoke | owner: separate UI agents | acceptance: actual lists/detail/cache/lifecycle/offline with app data retained; fix scoped regressions
- [ ] U8: branch CI then main promotion | owner: GitHub worker/root | acceptance: exact tested commit, ship=no before normal FF push, main CI healthy, private repo description current
- [ ] U9: final handoff | owner: root | acceptance: clean commits, deployment/CI evidence, no lingering Bouncer workloads

## Decisions
- Existing remote is tm-droid/bouncer; preserve it and its Android history.
- Native SwiftUI, Android visual identity adapted; local prefs; iOS18.2/current IDs; no widgets/push/accounts/full parity/store release.
- Shared on-demand detail now, consumed by both apps; no LLM on request path; cron sole canonical-row writer.
- Private response-cache bucket; no mutation of legacy public bucket permissions.
- Worktree Bouncer-unified on codex/shared-ios-foundation; original parent/Android remain recovery checkouts and own common Git metadata.
- User explicitly approved cloud activation, feature-branch push, CI with publish=false and fast-forward GitHub main after verification. No paid upgrade or store release.
- Testing turns ended per Tanush; run existing iOS and Android simulators sequentially and stop them after verification.

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
- U2 pre-review: focused service/vendor 15/15 PASS; canonical HTTP 17/17 PASS; generated parser and syntax checks PASS. Stable schema/fixture passed to both native workers. Full suites pending fresh verifier.
- Root documentation commit 8e7836b records cloud setup and no-production-change boundary.
- Backend full verification: `npx --yes --package=node@22 -- npm ci --ignore-scripts --no-audit --no-fund --prefix appwrite/functions/api` and same prefix match-sync PASS; `npx --yes --package=node@22 -- npm test --prefix appwrite/functions/api` PASS119/119; same command match-sync PASS473/473. Logs /private/tmp/bouncer-{api,sync}-{install,test}.log.
- Found/fixed during verification: generated API lock lacked nestedundici dependency (regenerated under Node22); router's exact route list needed new path; five backend fixture references still pointed to pre-monorepo app/src resources. Tests now run original fixtures under android/app, no suppressed checks.
- Imported current local Android AGENTS/CLAUDE and extracted architecture/claude docs (they were untracked/local newer guidance omitted by clean-history worktree). Original files untouched; updated shared-root/config references.
- U2 complete dcd32a94: backend reviewer clean for route/cache, API119/119 and match-sync473/473 under Node22. Native/production end-to-end remains pending (route not deployed).
- Fresh iOS review found null envelope fetchedAt decoding mismatch on valid partial200 response; U3 fixing with regression coverage before native verification.
- Native review: iOS corrected nullable/fractional timestamps, final-score projection, lifecycle/cancellation, per-section freshness, metadata shapes;16 tests compile pending execution. Android review found delivery-label loss, break-state TTL and stale-card-before-archive selection; U4 fixing with regressions.
- Shared fixture corrected to real parser ballMetric object {label,over,ball}; both clients notified. Earlier numeric fixture was unrealistic, actual server contract unchanged.
- Android full all-gates run: compile had no reported Kotlin error;detekt failed8new findings. Scoped fixes underway; verifier runningassemble/tests/lint separately. Log /private/tmp/bouncer-android-verify.log.
- iOS runtime installation authorized/inprogress: xcodebuild -downloadPlatform iOS -architectureVariant arm64, root session12393, log /private/tmp/bouncer-ios-runtime.log. No originalcheckouts/productionresources modified.
- U4 complete10ce3f1c: finalunit1391tests0fail/1CSV-dependent skip; detektPASS;standalonelintPASS;finalassembleDebugPASS. No suppressions/baseline changes. Independent rereviewclean.
- U3 completead6d8bd1: simulator19/19testsPASS afterbody-stream fixturecapture/timestamp expectation fixes; currentcodecompiles; independent rereviewclean. Imported99iOSfiles without provider credential-pattern/user-state hits.
- U5 localverificationreport docs/verification.md records exactcommands/results. LiveUI BLOCKED byAppwrite InvalidOrigin testing.bouncer; no platform registration/privatebucket/route deployment/push authorized or performed. Recommendednextstep: registeriOSplatform anddeploy reviewedAPI/cache setup, thenrepeat realdataUI smoke.
- Deploymenttool now defaults publicIDs fromshared/backend.json and permits missingAndroidlocal.properties; node--check/config/vendor guardsPASS. No live deploy call made.
- Permanentcheckout moved from/private/tmp/bouncer-shared-ios to/Users/tanushmalhotra/Documents/Tanush Coding/Bouncer-unified; originalBouncerparent/Androidworkingtrees preserved. Nativecommits10ce3f1c/ad6d8bd1; endpoint/providerconfiguration remainsserver-side.
- LiveUIobservedactual Appwrite InvalidOrigin (testing.bouncer), so no realmatchdata/detailnavigation claimed. All automatedchecksPASS; deployment/push/consolechanges notperformed. Remainingworkrequiresproductionactivationoutlinedindocs/shared-backend.md.
- DedicatediOStestappterminatedandsimulatorC609F07D-E70D-42C1-B8C6-BA4138106F23 shutdown(exit0). No ongoingBouncerbuild/test/UIload.
- Repeatedpost-moveGitdiffwhitespacecheckstalled andwasinterrupted(exit130); pre-movecodewhitespacecheckhadpassed. Finalchangedtextfileschecked directlyfortrailingwhitespace; checkpointusesquietGitcommitwithoutoptionalpreload/scans.
- Newcheckoutislinked: commonGitmetadata remainsatoriginalBouncer/android/.git; preserveoriginaluntilseparateclone/cutover. Quietfinalcheckpoint succeeded; code/status verificationreport saved.

- User approved full continuation plan. Baseline a7975c16 clean; live Bouncer project confirmed connected via Appwrite MCP. Original main93549c93 unchanged/unprotected; currentfunctions interval60 hourly, plannedcron1min/15min with budget canary.
