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

## Review and live verification boundary

Independent backend review found no remaining scoped correctness gaps. Independent Android and iOS re-reviews confirmed their findings were fixed. The actual iOS app launches on the dedicated simulator, but Appwrite rejects its unregistered iOS origin: `testing.bouncer`. Consequently, real match loading and match-detail navigation remain **blocked**, despite passing build and fixture/lifecycle tests. No mocked screenshots were substituted for real data.

Before live end-to-end validation: register the iOS platform for the current bundle identifier `Testing.Bouncer` (the rejected origin is reported as `testing.bouncer`); create the private `match-detail-cache` bucket with no public permissions; set `MATCH_DETAIL_CACHE_BUCKET_ID=match-detail-cache`; and deploy the reviewed `bouncer-api` code through the narrow deployment tool. Provider/server keys stay server-side. See [shared backend setup](shared-backend.md).

No GitHub push, merge, production platform/bucket mutation, function deployment or store release was performed. Actual organization usage/limits and deployed permissions were not inspected. Local and warm-container limits do not guarantee an organization-wide monthly budget or global singleflight.

The dedicated Bouncer app was terminated and simulator `C609F07D-E70D-42C1-B8C6-BA4138106F23` was shut down after the UI check; both cleanup commands completed. No Bouncer build/test/UI workload remains running.
