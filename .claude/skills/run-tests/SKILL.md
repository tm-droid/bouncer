---
name: run-tests
description: Run unit tests, detekt, or instrumented tests for the Bouncer Android project
---

Run the appropriate Gradle command based on scope. Always run from project root.

## Commands

**All unit tests:**
```bash
./gradlew test
```

**Single class (pattern):**
```bash
./gradlew test --tests "*ClassName*"
```

**Single class (fully qualified):**
```bash
./gradlew test --tests "com.tanush.bouncer.<pkg>.<ClassName>"
```

**Single method:**
```bash
./gradlew test --tests "com.tanush.bouncer.<pkg>.<ClassName>.<methodName>"
```

**Verbose output:**
```bash
./gradlew test --info 2>&1 | tee test.log
```

**Static analysis:**
```bash
./gradlew detekt
```

**Instrumented tests** (requires emulator or device):
```bash
./gradlew connectedAndroidTest
```

**Compose compiler metrics:**
```bash
./gradlew assembleDebug -PenableComposeCompilerReports
# Results: build/compose_metrics/report-*.txt
```

## Test Locations

| Category | Package | Key Tests |
|----------|---------|-----------|
| ML/Analysis | `com.tanush.bouncer.analysis` | MatchupAnalysisTest, SlidingWindowTest, ProbabilityTrackerTest |
| Network | `com.tanush.bouncer.network` | CricBuzzScraperTest |
| Data | `com.tanush.bouncer.data` | AppResultTest, AppExceptionTest, BouncerDatabaseMigrationVersionTest |
| Utils | `com.tanush.bouncer.utils` | MatchUtilsTest, SearchRepositoryTest, TimelineEventExtractorTest, CommentaryParserTest |
| Services | `com.tanush.bouncer.services` | CommentaryScorerTest, RetryPolicyTest, AICacheServiceTest, CommentaryFusionServiceTest |
| ViewModels | `com.tanush.bouncer.viewmodels` | LiveActivityViewModelTest |

## After Running

Report: pass count, fail count, any stack traces for failures, detekt violations if run.
