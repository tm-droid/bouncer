# Goal: shared Bouncer Android/iOS repo and real-data iOS matches/detail

Status: paused at Tanush’s request to clear storage. Healthy shared API deployed; native smoke, Free cron limitation and Actions budget prevent full completion/main promotion.
Current workspace: /Users/tanushmalhotra/Documents/Tanush Coding/Bouncer-unified
Branch: codex/shared-ios-foundation

## Next (after storage is cleared)
1. Continue only in `/Users/tanushmalhotra/Documents/Tanush Coding/Bouncer-unified`, branch `codex/shared-ios-foundation`. Original folders share Git metadata; do not move/delete/reset them. Source deployment commit `f93721a7f90a4449e668dade6f1f5ef46387c352`.
2. Finish real iOS then Android UI checks sequentially, preserving data. iOS simulator `C609F07D-E70D-42C1-B8C6-BA4138106F23`, bundle `Testing.Bouncer`, built app `/private/tmp/bouncer-shared-derived/Build/Products/Debug-iphonesimulator/Bouncer.app`. Android existing `bouncer_test`, package `com.tanush.bouncer`, APK `/Users/tanushmalhotra/Library/Caches/bouncer-android/app/outputs/apk/debug/app-debug.apk` already installed with `-r`. Check lists/scores/scorecards/commentary/meta/offline/lifecycle. No new runtime download needed. Stop afterward.
3. Finish bounded section-freshness/partial/outage/expiry verification for `/match-detail`; use fixtures for quota exhaustion. Do not deliberately exhaust production. Earlier full API123/123 and native/match-sync checks remain reusable until relevant code changes.
4. Resolve actual Free cron rejection (`403 billing_feature_unsupported`, functionsAllowCronfalse, intervalMinimum60) within approved boundaries before any cadence change. Both functions remain hourly; current API minute55 falls in no-job window, so quarter-hour maintenance is unresolved. No paid upgrade, scheduler bypass or external trigger added. Minute canary did not run.
5. Restore GitHub Actions availability without spending changes, then dispatch `.github/workflows/ci.yml` with publish=false on latest pushed feature commit and require Android/backend/iOS pass. Last run41206442951 budget-blocked before steps. Do not treat local tests as satisfying this CI gate.
6. Only after all gates pass: fetch/recheck exact remote main, rerun migration/release detector against that base and require ship=no BEFORE normal fast-forward push. If main advanced, merge it into feature, resolve/retest/reassess. No force-push/PR needed. Verify mainCI/tree/backendhealth; update repo description for Android+iOS. Visibility stays private.
7. Update this ledger and `docs/verification.md` with final results/CI URLs/commit. No store release.

## Current safe cloud state
- Project `6a02f7f3000e6e100b19`, sgp. Apple platform `bouncer-ios` / bundle `Testing.Bouncer` registered.
- Private `bouncer-db/match-detail-cache`: permissions[], rowSecurityfalse, required mediumtext payload, encryptfalse (Free disallows column encryption; public cricket cache only/no secrets). Legacy resource permissions/provider secrets unchanged.
- Active healthy API `6ae5f4f2ea8070f7723a`; original rollback `6ad18b9dbd1bc3d6b655` retained. Intermediate `6ae5f39a79f25837a39f` inactive after real-list-shape failure/rollback/fix. Sync deployment `match-sync-mvliqrgc` unchanged. Production build enforces locked `npm ci --omit=dev --no-audit --no-fund`.
- Archive27files/72,524B, SHA256 `6d55b3c4d2843b175c6f02c27f7fee1c017b89c4bb07c9d1d12803b07850f250`.
- Rollback settings/source in protected `/private/tmp/bouncer-activation-rollback`; nonsecret cloud evidence `/private/tmp/bouncer-activation-evidence.json`. Do not publish server keys/secret values. Write MCP facade absent; approved REST fallback used existing original ignored `android/local.properties` key transiently, no key copies/new grants.
- Final org usage:108898reads/500000,41490writes/250000,9862executions/750000; execution+build compute63.7785/100GBh, cycleendsNov13. Baseline108875reads/41396writes/9841executions; delayed aggregate changes include background jobs, not exact test attribution.
- Both functions scheduleempty/interval60. No lingering Bouncer simulator/build/test/cloud jobs from this continuation.

## Verify with / gotchas
- Latest exact backend suite: `/Users/tanushmalhotra/.npm/_npx/52027bd8fc0022aa/node_modules/node/bin/node --test` from `appwrite/functions/api`:123/123 PASS(11.14s). `npx` wrapper earlier DNS-failed; cached Node22.23.4 works.
- `node shared/check-backend-config.mjs`; `node appwrite/tools/sync-api-parsers.mjs --check`; `git -c core.fsmonitor=false diff --check`:PASS. Fresh reviews clean for private-table and real-list-envelope deltas.
- Live170761/upcoming161234/recent147942 cold+warm200/schema1/private persisted rows[]; recent stateComplete. MalformedID400/invalidJSON400/unknown404/GET405. Four cheap oldroute hashes/statuses identical before/after. No interactiveAI/canonical-table writes in request code. Forced expiry/outage/sectionfreshness and actual nativeUI remain incomplete.
- iOS launch300s timeout/remote automation timeout despite booted status; shutdown verified. Android install-rPASS/launchStatusok→permissioncontroller; diskfilled beforeUI. Third lowmemory attempt stopped on userwrapup. `adb devices` empty, iOS dedicateddeviceShutdown. No appdata erased.
- Only task-generated iOS intermediates/index/modulecaches were removed to free space; app Products/testevidence/sourcepackages retained. Logs `/private/tmp/bouncer-{ios,android}-live-initial.txt`, Android emulator logs in same prefix. Do not blindly rerun failed resource-heavy jobs before storage is cleared.
- Feature CI https://github.com/tm-droid/bouncer/actions/runs/41206442951:failure, no steps, Actions budget preventing use; iOS skipped. Remote main lastverified93549c93f5f1d9b1f519391a63e8ac64e07c2c2f, never promoted.

## Units
- [x] U1: monorepo/config/CI | files: root layout, .github, shared, Android Gradle path inputs | acceptance: Android history preserved, sanitized iOS, no relocation-only Play publish
- [x] U2: shared detail service | files: appwrite | acceptance: cache/contract/deadline/quota tests, existing Node suites
- [x] U3: iOS real-data slice | files: ios | acceptance: fixtures+lifecycle tests, build/test/smoke after queue grant
- [x] U4: Android shared detail fallback | files: android app source/tests | acceptance: regression checks and Android build/lint/detekt after queue grant
- [x] U5: review/verification/handoff | files: docs and integration fixes | acceptance: fresh review, appropriate checks, documented cloud setup

- [ ] U6: Appwrite activation/canary | owner: cloud worker | acceptance: private cache, Apple platform, healthy deployment, cadence and Free quota validation, rollback preserved
- [ ] U7: real iOS then Android smoke | owner: separate UI agents | acceptance: actual lists/detail/cache/lifecycle/offline with app data retained; fix scoped regressions
- [ ] U8: branch CI then main promotion | owner: GitHub worker/root | acceptance: exact tested commit, ship=no before normal FF push, main CI healthy, private repo description current
- [x] U9: pause handoff | owner: root | acceptance: committed/pushed checkpoint, deployment/CI evidence, no lingering Bouncer workloads; resume from Next below

## Decisions
- Existing remote is tm-droid/bouncer; preserve it and its Android history.
- Native SwiftUI, Android visual identity adapted; local prefs; iOS18.2/current IDs; no widgets/push/accounts/full parity/store release.
- Shared on-demand detail now, consumed by both apps; no LLM on request path; cron sole canonical-row writer.
- Private server-only response-cache table in existing bouncer-db, approved after Free plan rejected a second bucket; no mutation of legacy resource permissions.
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

- U6 continuation: Apple platform bouncer-ios registered for Testing.Bouncer (2026-10-31T13:37:47Z); private bucket create rejected403 additional_resource_not_allowed (Free one-bucket limit). No API deployment/env/cadence changed. Rollback source/settings preserved in protected /private/tmp/bouncer-activation-rollback. User approved private cache table alternative; adapting code and config before fresh review/deployment.
- U8 feature branch pushed at af9c24401d49c7254950e36a608b477fc33058fd; workflow_dispatch publish=false run https://github.com/tm-droid/bouncer/actions/runs/41206442951 failed before any step: Actions budget preventing further use. iOS/dependency jobs skipped. Main remains93549c93f5f1d9b1f519391a63e8ac64e07c2c2f; CI gate is not satisfied and promotion is held.
- U6 table adaptation ready: match-detail-cache in existing bouncer-db, permissions[]/rowSecurityfalse; payload mediumtext available (column encryption unavailable on Free, public cricket payload only). 96,048-byte server roundtrip PASS, anonymousrow404, private row[], cleanupquery accepted; proberow deleted. Fresh independent review clean. API121/121 PASS using cached Node22.23.4 binary (183951ms); sharedconfig/parserguards and diff--check PASS. npx wrapper attempt failed DNS; direct cachedbinary suite passed. No new API deployment/env/cadence yet.
- U7 initial iOS retry: dedicated simulator reported booted, but app launch and automation timed out (launch300s, remote automation session creation timeout). Normal shutdown exit0; device verified Shutdown. No realUI/origin result claimed. Evidence /private/tmp/bouncer-ios-live-initial.txt; Android existingAVD next, preservingdata.
- U6 first candidate6ae5f39a79f25837a39f built in5s (lockednpmci), activated then rolled back to6ad18b9dbd1bc3d6b655 after realproducer listshape regression: data.matches object envelope vs assumedarray. Four cheap legacyroute response hashes/statuses matched baseline. Bothcronupdates denied403billing_feature_unsupported; realtier0plan has functionsAllowCronfalse/intervalMinimum60. Verified scheduleempty/interval60 retained; no minute canary orpaidupgrade/bypass.
- U6 listadapter now matches actual poll.js producer {data:{matches:[...]},fetchedAt}; realshape tests cover3tabs/membership/freshness/malformed. Freshreview clean; Node22fullAPI123/123 PASS(11.14s), config/vendor/diffguardsPASS. Checkpoint before reactivation.
- U7 cleanup/retry: removed only task-generated /private/tmp/bouncer-shared-derived intermediate/index/module caches, preserving Products/app/testevidence/sourcepackages. Free1.2→4.1GiB. ExistingAndroidAVD booted, adbinstall-rPASS, launchStatusok reachedpermissioncontroller; hostdiskfilled beforeUIcapture. Emulatorstoppedcleanly/adbdevicesempty,free1.9GiB restored. Allappdataretained; lists/detail/offline/lifecycle stillunverified.

- Pause requested by Tanush to clear storage. Corrected deployment healthy; all app verification workloads stopped. Final handoff commit will be pushed to existing feature branch; main remains gated.
