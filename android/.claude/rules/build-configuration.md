---
paths:
  - "**/*.gradle.kts"
  - "gradle.properties"
  - "gradle/**"
  - "baselineprofile/**"
---

# Build Configuration & Optimization

## iCloud Drive Workarounds

Build outputs redirected to `~/Library/Caches/bouncer-android/app` to prevent Gradle task cache invalidation. iCloud modifies file metadata on every sync, fooling Gradle into treating sources as modified.

**gradle.properties settings:**
- `org.gradle.vfs.watch=false`: Disable VFS watching (iCloud generates constant synthetic FSEvents)
- `org.gradle.configuration-cache=true`: Enabled — KSP 2.2.20+ and AGP 9.1.1+ both support it; Sentry excluded for debug via `ignoredVariants`. `problems=warn` means release builds degrade gracefully if a violation is hit.

**Kotlin daemon memory:** 2GB (handles large code generation without OOM)

**Worker pool:** Limited to avoid overwhelming I/O system on synced folders

## Gradle Properties Tuning

```
org.gradle.jvmargs=-Xmx6g -XX:+UseG1GC -XX:SoftRefLRUPolicyMSPerMB=0 -XX:MaxMetaspaceSize=1g
org.gradle.workers.max=6
org.gradle.vfs.watch=false
org.gradle.configuration-cache=true
org.gradle.configuration-cache.problems=warn
kotlin.daemon.jvmargs=-Xmx5g -XX:+UseG1GC -XX:+UseStringDeduplication
```

## API Target & Compile Levels

- Compile SDK: 37
- Target SDK: 36
- Min SDK: 26
- Java version: 17
- Kotlin JVM target: 17

## Build Variants

**Debug:**
- Faster: skips resource compression (`isCrunchPngs=false`)
- Sentry transforms disabled (see Sentry section)

**Release:**
- Minified: ProGuard enabled
- Resources shrunk
- Sentry uploads source context + ProGuard mappings

## NDK Configuration

All four ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`. It was 64-bit only until 2026-10-09 on the belief that the androidx.graphics.path native lib ships no `armeabi-v7a`; graphics-path 1.0.1, sentry-native-ndk and datastore-core all ship all four, and a 32-bit phone (`ro.product.cpu.abilist` = `armeabi-v7a,armeabi`) answered `INSTALL_FAILED_NO_MATCHING_ABIS`. Check a new native dependency's AAR for all four `jni/` directories before adding it.

**Baseline profile:** `:baselineprofile` (Macrobenchmark) generates
`app/src/release/generated/baselineProfiles/`, which ships in the release bundle and is installed by
`profileinstaller`. Regenerate with a device or emulator attached (API 28+):
`./gradlew :app:generateReleaseBaselineProfile`; `connectedBenchmarkReleaseAndroidTest` runs
`StartupBenchmark` (no-compilation vs profile). The committed profile covers **cold start and
onboarding only**: on the `bouncer_test` emulator every UiAutomator tap on onboarding's Skip is
dropped (measured in the generator's logcat — clicked, never gone, three retries), so the Home and
match-detail journeys never ran. A physical phone is the way to extend it. The generator must also
keep the machine awake (`caffeinate -i`): a laptop sleeping mid-run leaves the test stuck at `0/3`
indefinitely. `nonMinifiedRelease`/`benchmarkRelease` are in Sentry's `ignoredVariants`.
