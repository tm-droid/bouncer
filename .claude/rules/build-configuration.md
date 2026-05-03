# Build Configuration & Optimization

## iCloud Drive Workarounds

Build outputs redirected to `~/Library/Caches/bouncer-android/app` to prevent Gradle task cache invalidation. iCloud modifies file metadata on every sync, fooling Gradle into treating sources as modified.

**gradle.properties settings:**
- `org.gradle.vfs.watch=false`: Disable VFS watching (iCloud generates constant synthetic FSEvents)
- `org.gradle.configuration-cache=false`: Disabled due to KSP + Sentry plugin compatibility issues

**Kotlin daemon memory:** 2GB (handles large code generation without OOM)

**Worker pool:** Limited to avoid overwhelming I/O system on synced folders

## Gradle Properties Tuning

```
org.gradle.jvmargs=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Xmx2048m
org.gradle.workers.max=4
org.gradle.vfs.watch=false
org.gradle.configuration-cache=false
file.encoding=UTF-8
org.gradle.console=rich
```

## API Target & Compile Levels

- Compile SDK: 36
- Target SDK: 35
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

Only 64-bit ABIs: `arm64-v8a`, `x86_64` (androidx.graphics.path native lib doesn't ship armeabi-v7a)
