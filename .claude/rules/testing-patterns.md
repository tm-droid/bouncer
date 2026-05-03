# Testing Patterns & Commands

## Unit Tests (JVM)

**Run all:**
```bash
./gradlew test
```

**Single test class:**
```bash
./gradlew test --tests "com.tanush.bouncer.analysis.ml.ScorePredictionModelTest"
```

**Single test method:**
```bash
./gradlew test --tests "com.tanush.bouncer.analysis.ml.ScorePredictionModelTest.testPredictionAccuracy"
```

**Pattern matching:**
```bash
./gradlew test --tests "*MatchUtilsTest"
```

**With verbose output:**
```bash
./gradlew test --info 2>&1 | tee test.log
```

## Test Organization

**ML/Analysis tests** (`app/src/test/java/com/tanush/bouncer/analysis/ml/`)
- `TrainingPipelineTest` — end-to-end model training
- `GradientDescentTest` — optimization algorithm
- `FeatureExtractorTest` — feature pipeline
- `ScorePredictionModelTest` — prediction accuracy
- `LossFunctionTest` — loss computation

**Network tests** (`app/src/test/java/com/tanush/bouncer/network/`)
- `CricBuzzScraperTest` — scraper validation + live data parsing

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
