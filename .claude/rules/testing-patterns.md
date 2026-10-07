# Testing Patterns & Commands

## Unit Tests (JVM)

**Run all:**
```bash
./gradlew test
```

`--tests` is **not** valid on the aggregate `test` task (it fans out to several test tasks and Gradle
rejects the filter). Every filtered run must name a concrete variant task — `testDebugUnitTest`.

**Single test class:**
```bash
./gradlew testDebugUnitTest --tests "com.tanush.bouncer.analysis.MatchProjectionsTest"
```

**Single test method:**
```bash
./gradlew testDebugUnitTest --tests "com.tanush.bouncer.viewmodels.LiveActivityColdStartTest"
```

**Pattern matching:**
```bash
./gradlew testDebugUnitTest --tests "*MatchUtilsTest"
```

**With verbose output:**
```bash
./gradlew test --info 2>&1 | tee test.log
```

## Test Organization

**Analysis tests** (`app/src/test/java/com/tanush/bouncer/analysis/`)
- `MatchProjectionsTest` — run rate, projected score, required rate. The ML models moved
  server-side; their tests are the functions' `node --test` suites.

**Network tests** (`app/src/test/java/com/tanush/bouncer/network/`)
- `CricBuzzScraperTest` — parser checks against hand-written markup; the captured-page tests are the `*RscTest` and `*FlightTest` classes

**Data layer** (`app/src/test/java/com/tanush/bouncer/data/`)
- `AppResultTest` — Result<T> monad behavior
- `AppExceptionTest` — exception serialization

**Utils** (`app/src/test/java/com/tanush/bouncer/utils/`)
- `MatchUtilsTest` — match utility functions
- `SearchRepositoryTest` — search logic
- `TimelineEventExtractorTest` — timeline parsing

## Instrumented Tests (Android)

```bash
./gradlew connectedAndroidTest
```

Requires emulator or connected device.

## Compose Compiler Metrics

Enable on debug build to identify recomposition issues:

```bash
./gradlew assembleDebug -PenableComposeCompilerReports
# Read: build/compose_metrics/report-*.txt
```

Look for unstable classes and excessive recomposition in function chains.

## Function tests (Node)

```bash
cd appwrite/functions/match-sync && node --test     # each function directory has its own suite
```

CI runs every function's suite (`Function Tests` job) and the Play upload waits on it.
