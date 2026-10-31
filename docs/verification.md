# Shared Android/iOS verification — 2026-10-31

Workspace: `/Users/tanushmalhotra/Documents/Tanush Coding/Bouncer-unified`.

Branch: `codex/shared-ios-foundation`. Android source commit `10ce3f1c`; iOS source commit `ad6d8bd1`. Original parent and Android working trees remain unchanged. This is a linked Git worktree: its common Git metadata remains in `/Users/tanushmalhotra/Documents/Tanush Coding/Bouncer/android/.git`; keep that metadata until a separate clone/cutover is completed. The unified branch retains Android history and imports sanitized iOS sources.

## Passing checks

Backend (from repository root, Node22.23.4):

```sh
npx --yes --package=node@22 -- npm ci --ignore-scripts --no-audit --no-fund --prefix appwrite/functions/api
npx --yes --package=node@22 -- npm ci --ignore-scripts --no-audit --no-fund --prefix appwrite/functions/match-sync
npx --yes --package=node@22 -- npm test --prefix appwrite/functions/api
npx --yes --package=node@22 -- npm test --prefix appwrite/functions/match-sync
```

Both clean installs exit0; API119/119 pass and match-sync473/473 pass. API was rerun after synchronizing the legacy player-search HTTP vendor copy. Logs: `/private/tmp/bouncer-api-final-test.log`, `/private/tmp/bouncer-sync-test.log`.

Android (from `android/`; each command exits0):

```sh
./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.java.home=/Users/tanushmalhotra/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home testDebugUnitTest detekt
./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.java.home=/Users/tanushmalhotra/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home lintDebug
./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.java.home=/Users/tanushmalhotra/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home assembleDebug
```

1391tests:0failures,0errors,1skip. The skipped data-dependent `CommentaryCacheHealthTest` requires `-Dcommentary.cache.csv`, which was not supplied. Tests/detekt passed in5m9s; standalone lint15m13s; final APK assembly1m33s. No lint/detekt suppression or baseline weakening. Initial combined runs failed on new detekt issues, mocks and vendor drift; these were corrected before the final passes. Early lint infrastructure exceptions did not recur in the final standalone run.

Logs: `/private/tmp/bouncer-android-final-tests-detekt.log`, `/private/tmp/bouncer-android-final-lint.log`, `/private/tmp/bouncer-android-final-assemble.log`. APK: `/Users/tanushmalhotra/Library/Caches/bouncer-android/app/outputs/apk/debug/app-debug.apk`.

iOS (from repository root, exits0):

```sh
xcodebuild -project ios/Bouncer.xcodeproj -scheme Bouncer -destination 'platform=iOS Simulator,id=C609F07D-E70D-42C1-B8C6-BA4138106F23' -derivedDataPath /private/tmp/bouncer-shared-derived -clonedSourcePackagesDirPath /private/tmp/bouncer-shared-packages -parallel-testing-enabled NO CODE_SIGNING_ALLOWED=NO test
```

19/19Swift tests pass. The app and test target compile with pinned Appwrite AppleSDK21.2.0. Tests cover real wire shapes, nullable/fractional timestamps, per-section staleness, terminal corrections, metadata, request body/20-second route timeout, raw commentary labels/deduplication, cache/singleflight/cancellation, quota handling and visible lifecycle. The first execution exposed two test-fixture problems; the timestamp expectation and URLProtocol body-stream capture were corrected without changing production request behavior or removing assertions. Log: `/private/tmp/bouncer-ios-final-tests.log`; xcresult under `/private/tmp/bouncer-shared-derived/Logs/Test/`.

Installed iOS27.1 arm64 runtime was used successfully. Xcode's download command also attempted an unnecessary second27.0 runtime and exited70 for insufficient disk space; the27.1 installation itself succeeded and its availability and test execution were verified. No unrelated files were deleted; only obsolete task-owned baseline build caches were removed.

Other checks:

```sh
node shared/check-backend-config.mjs
node appwrite/tools/sync-api-parsers.mjs --check
node --check appwrite/tools/deploy-function.mjs
actionlint .github/workflows/ci.yml
git -c core.fsmonitor=false diff --check
```

The code checks passed. A repeated post-move Git whitespace scan stalled and was interrupted; the final changed text files passed a direct trailing-whitespace check, and the local checkpoint was saved with optional Git scans disabled. Six targeted iOS CI path cases pass; prior migration/Android/backend/shared filters also passed, including no relocation-only Play publish. Known provider credential scan:0hits in imported iOS text sources; machine/user-state import scan:0hits.

## Earlier baseline review and live verification boundary

Independent backend review found no remaining scoped correctness gaps. Independent Android and iOS re-reviews confirmed their findings were fixed. The actual iOS app launches on the dedicated simulator, but Appwrite rejects its unregistered iOS origin: `testing.bouncer`. Consequently, real match loading and match-detail navigation remain **blocked**, despite passing build and fixture/lifecycle tests. No mocked screenshots were substituted for real data.

Before live end-to-end validation: register the iOS platform for the current bundle identifier `Testing.Bouncer` (the rejected origin is reported as `testing.bouncer`); create the private `match-detail-cache` bucket with no public permissions; set `MATCH_DETAIL_CACHE_BUCKET_ID=match-detail-cache`; and deploy the reviewed `bouncer-api` code through the narrow deployment tool. Provider/server keys stay server-side. See [shared backend setup](shared-backend.md).

No GitHub push, merge, production platform/bucket mutation, function deployment or store release was performed. Actual organization usage/limits and deployed permissions were not inspected. Local and warm-container limits do not guarantee an organization-wide monthly budget or global singleflight.

The dedicated Bouncer app was terminated and simulator `C609F07D-E70D-42C1-B8C6-BA4138106F23` was shut down after the UI check; both cleanup commands completed. No Bouncer build/test/UI workload remains running.


## Activation result and pause handoff

Paused at Tanush’s request to clear storage. The shared API is healthy; native UI verification and GitHub main promotion remain incomplete. Continue in `Bouncer-unified` on `codex/shared-ios-foundation`; preserve original recovery folders/shared Git metadata.

- Active API: `6ae5f4f2ea8070f7723a`, built from source commit `f93721a7f90a4449e668dade6f1f5ef46387c352` with locked `npm ci --omit=dev --no-audit --no-fund`. Archive27files/72,524B, SHA256 `6d55b3c4d2843b175c6f02c27f7fee1c017b89c4bb07c9d1d12803b07850f250`.
- Rollback deployment `6ad18b9dbd1bc3d6b655` and protected source/settings in `/private/tmp/bouncer-activation-rollback` retained. The first candidate `6ae5f39a79f25837a39f` was rolled back after real-data inspection exposed the `data.matches` list-envelope mismatch. That adapter was corrected, covered with producer-shape tests, freshly reviewed and redeployed.
- Apple platform `bouncer-ios` registered for `Testing.Bouncer`. Private table `bouncer-db/match-detail-cache` has permissions[] and rowSecurityfalse. Appwrite Free rejected a second bucket, and Tanush approved the table alternative. Required mediumtext payload is unencrypted because Free rejects column encryption; it contains public cricket responses and no secrets. Legacy resource permissions/provider credentials were preserved.
- Live170761, upcoming161234 and recent147942 each passed cold/warm200 responses, schemaVersion1 and private persisted-row permissions checks; recent normalized toComplete. Malformed ID400, invalidJSON400, unknownID404 and GET405 passed. Four inexpensive existing-route status/body hashes matched predeployment baselines. A96,048-byte table probe roundtrip and anonymous404 denial passed; probe removed. Request code remains AI-free and reads canonical tables without writing them.
- Latest affected suite: `/Users/tanushmalhotra/.npm/_npx/52027bd8fc0022aa/node_modules/node/bin/node --test` from `appwrite/functions/api`: **123/123 PASS**,11.14s, Node22.23.4. The npx wrapper earlier failed DNS; the cached exact binary worked. Shared config guard, parser vendor guard, `git -c core.fsmonitor=false diff --check` and both fresh reviews passed. Earlier match-sync473, Android1391 and iOS19 results above remain applicable; native source was unchanged.
- Both requested cron changes returned403`billing_feature_unsupported`. Actual Free metadata reports `functionsAllowCron:false`, minimum interval60. Both functions remain scheduleempty/interval60; no minute canary or paid upgrade/bypass. API's existing minute55 trigger falls in its no-job window, leaving the quarter-hour maintenance cadence unresolved.
- Final org usage:108,898reads/500,000;41,490writes/250,000;9,862executions/750,000; execution/build compute63.7785/100GBh, cycle endingNov13. Initial baseline108,875reads/41,396writes/9,841executions. Delayed org-level deltas include background jobs and are not exact test attribution. Evidence: `/private/tmp/bouncer-activation-evidence.json`.
- iOS actual UI remains unverified: booted status, launch300s timeout, remote automation session timeout; normal shutdown verified. Android booted, `adb install -r` passed and launch reached permissioncontroller, but hostdiskfilled before UIcapture. Appdata preserved. Only this task's generated iOS intermediate/index/module caches were cleaned; app Products/testevidence/sourcepackages retained. All Bouncer simulator workloads stopped; `adb devices` empty. Evidence: `/private/tmp/bouncer-ios-live-initial.txt` and `/private/tmp/bouncer-android-live-initial.txt`.
- Feature branch was pushed ataf9c244 before these final fixes. [CI with publish=false](https://github.com/tm-droid/bouncer/actions/runs/41206442951) failed before steps: “The job was not started because an Actions budget is preventing further use.” iOS/dependency jobs skipped. Final fixes/handoff are checkpointed and pushed before stopping; main remains lastverified93549c93f5f1d9b1f519391a63e8ac64e07c2c2f and was never promoted.

Next: finish sequential native UI/offline/lifecycle checks after freeing storage, finish bounded section-freshness/partial/outage checks, resolve Free scheduling constraints and Actions availability without spending changes, then require passing CI and exact-base ship=no guard before normal fast-forward main promotion. No store release. Detailed resumable steps are in `.Codex/task-ledger.md`.
