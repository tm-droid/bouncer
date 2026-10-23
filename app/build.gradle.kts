import java.util.Properties
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry)
    alias(libs.plugins.google.services)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$projectDir/config/detekt.yml")
    baseline = file("$projectDir/config/detekt-baseline.xml")
}

// detekt 1.23.x embeds a Kotlin 1.9 frontend and crashes on JDK 25 (EnvironmentFacade), so it has
// to be invoked with the daemon itself on JDK 21:
//   ./gradlew detekt "-Dorg.gradle.java.home=$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home"
// The project's `jvmToolchain(21)` does NOT cover this: detekt 1.23 runs inside the Gradle daemon
// rather than forking, and its task exposes no `javaLauncher`, so the daemon JDK is the only lever.
// It retires with the move to detekt 2.x, not before.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "17" }
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { jvmTarget = "17" }

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// A `KEY=` line loads as "", not null, so `getProperty(key) ?: fallback` never falls back. The CI
// release job wrote every Appwrite id that way — from GitHub secrets that did not exist — and the
// Play build shipped with a blank endpoint: no Appwrite call of any kind, and no Sentry DSN to
// report it. Blank is absent.
fun buildConfigValue(key: String): String =
    localProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""

// The values a release artifact is useless without. Hung on the artifact tasks rather than on
// preReleaseBuild: `test` and `lint` compile the release variant in CI with deliberately blank
// config and must keep working. The mustRunAfter only makes the check fail before the compile
// instead of after it.
val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    val missing = listOf("APPWRITE_ENDPOINT", "APPWRITE_PROJECT_ID", "SENTRY_DSN")
        .filter { buildConfigValue(it).isEmpty() }
    doLast {
        check(missing.isEmpty()) {
            "Release build has no value for $missing. Set them in local.properties (the Appwrite " +
                "ids are pre-filled in local.properties.template). Built without them the app " +
                "cannot reach Appwrite and reports nothing to Sentry."
        }
    }
}
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }
    .configureEach { dependsOn(verifyReleaseConfig) }
tasks.matching { it.name == "preReleaseBuild" }.configureEach { mustRunAfter(verifyReleaseConfig) }

// On macOS, redirect build outputs to ~/Library/Caches to avoid iCloud-synced Documents.
// iCloud modifies file metadata on every sync, invalidating Gradle's incremental task
// outputs and forcing mergeResources / dex / etc. to re-run from scratch every build.
// On Linux (CI), leave the build directory at the default (./app/build) so artifact
// upload paths in the CI workflow remain correct.
if (System.getProperty("os.name").lowercase().contains("mac")) {
    layout.buildDirectory.set(
        file("${System.getProperty("user.home")}/Library/Caches/bouncer-android/app")
    )
}

android {
    namespace = "com.tanush.bouncer"
    compileSdk = 37

    signingConfigs {
        create("release") {
            storeFile = file("bouncer-release.keystore")
            val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
            storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: if (isReleaseBuild) error("KEYSTORE_PASSWORD must be set for release builds") else ""
            keyAlias = localProperties.getProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS") ?: ""
            keyPassword = localProperties.getProperty("KEY_PASSWORD")
                ?: System.getenv("KEY_PASSWORD")
                ?: if (isReleaseBuild) error("KEY_PASSWORD must be set for release builds") else ""
        }
    }

    defaultConfig {
        applicationId = "com.tanush.bouncer"
        minSdk = 26
        targetSdk = 36
        // CI supplies this; a local build keeps the literal. The Play publish job ran on every
        // push to main against a hand-edited literal, so every push after a bump re-uploaded a
        // code Play already held and the job failed with "Version code N has already been used"
        // — permanently, and quietly, because the job carried continue-on-error. The workflow
        // run number is monotonic for the life of the workflow, so a code derived from it can
        // never collide or go backwards. versionName stays hand-edited: it is the thing users
        // read, and it changes when the release is worth a name, not when CI runs.
        versionCode = System.getenv("BOUNCER_VERSION_CODE")?.toIntOrNull() ?: 8
        versionName = "1.0.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // No secret is compiled into this app. Provider keys (Groq/Cerebras/SambaNova), the
        // RapidAPI and Highlightly keys, and the old BOUNCER_FUNCTION_SECRET all used to live here
        // as BuildConfig string constants — which put them, in clear text, in every shipped APK:
        //   unzip -oq app.apk 'classes*.dex' && strings classes*.dex | grep gsk_
        // R8 does not obfuscate string literals, so minification never hid them. Commentary
        // generation now runs entirely in bouncer-generate-commentary, which holds the keys
        // server-side. The ids below are not secrets: they name public, read("any") resources.
        listOf(
            "APPWRITE_ENDPOINT",
            "APPWRITE_PROJECT_ID",
            "APPWRITE_AI_CACHE_DB_ID",
            "APPWRITE_AI_CACHE_COL_ID",
            "APPWRITE_FAVOURITES_COL_ID",
            "APPWRITE_HIGHLIGHTS_DB_ID",
            "APPWRITE_HIGHLIGHTS_COL_ID",
            "APPWRITE_PLAYER_FORMS_DB_ID",
            "APPWRITE_PLAYER_PROFILES_COL_ID",
        ).forEach { key -> buildConfigField("String", key, "\"${buildConfigValue(key)}\"") }
        manifestPlaceholders["sentryDsn"] = buildConfigValue("SENTRY_DSN")
        // Default environment; overridden per build type below
        manifestPlaceholders["sentryEnvironment"] = "production"
        manifestPlaceholders["sentryDebug"] = "false"

        // All four ABIs. This was 64-bit only on the belief that androidx.graphics.path ships no
        // armeabi-v7a; graphics-path 1.0.1, sentry-native-ndk and datastore-core all ship all
        // four, and a 32-bit phone answered INSTALL_FAILED_NO_MATCHING_ABIS.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["sentryEnvironment"] = "production"
            manifestPlaceholders["sentryDebug"] = "false"
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isCrunchPngs = false
            // AGP's own JaCoCo, not Kover: `createDebugUnitTestCoverageReport` writes the XML that
            // CI uploads to Codecov, with no plugin to keep in step with AGP 9's built-in Kotlin.
            enableUnitTestCoverage = true
            manifestPlaceholders["sentryEnvironment"] = "debug"
            manifestPlaceholders["sentryDebug"] = "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        // Pin the JDK the compiler RUNS on, not just the bytecode it emits. Without a toolchain the
        // build used whatever JDK happened to be ambient — 25 locally, 17 in CI — which is what the
        // `foojay-resolver-convention` plugin in settings.gradle.kts exists to prevent and was
        // applied without ever being used. 21 rather than 17 because 17 is not installed on this
        // machine and detekt 1.23.x cannot run above 21; one JDK now satisfies both.
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // Resolve against JDK 17's java.base, not the toolchain's. Without this a JDK 21-only
            // API compiles cleanly and fails at runtime on a device.
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // The comment here used to say "surface all warnings as errors in CI" next to
        // `abortOnError = false`, with the baseline commented out — so nothing lint found could
        // fail anything, and CI compounded it by running bundleRelease with -x lintVital*. Six
        // Fatal FullBackupContent errors and a NewApi call that would NoSuchMethodError on API
        // 26-28 were sitting in the report, green.
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        // Ignore auto-generated code (R.*, databinding, etc.)
        ignoreTestSources = true
        // Pre-existing warnings are recorded here so new code is held to the real bar. Errors were
        // fixed rather than baselined. Do not regenerate this wholesale to make a build pass.
        baseline = file("lint-baseline.xml")
        // checkDependencies stays off: lint already takes ~14 minutes on this module, and library
        // lint would not be actionable here anyway.
        //
        // The "a newer version exists" checks are disabled rather than baselined. With
        // warningsAsErrors on they turn every upstream release into a red build here and in CI,
        // for a condition that is not a defect in this code and cannot be fixed by editing it.
        // Dependency currency is tracked by Dependabot, which is the tool that can actually open
        // the change.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // CommentaryConstantParityTest reads appwrite/functions/match-sync/src/llm.js from the repo and
    // asserts its SYSTEM_INSTRUCTION / META / LEADING_BALL_LABEL still match the Kotlin copies.
    // Gradle cannot see that dependency on its own: the file is outside the module, so editing it
    // leaves testDebugUnitTest UP-TO-DATE and the parity check never runs. Declaring it as an input
    // is what makes a one-sided edit fail the build — without this the test passes even with llm.js
    // deleted, which is exactly the vacuous-green it exists to prevent.
    tasks.withType<Test>().configureEach {
        // Gradle's default test JVM heap is 512m and the suite had grown to the edge of it: runs
        // failed with `java.lang.OutOfMemoryError: Java heap space` attributed to whichever test
        // happened to be executing, which reads as a flaky test rather than as an exhausted fork.
        // Every test in one JVM plus mockk's generated classes is what fills it, so the fix is the
        // heap and not the tests.
        maxHeapSize = "2g"

        inputs.files(rootProject.file("appwrite/functions/match-sync/src/llm.js"))
            .withPropertyName("commentaryConstantParitySource")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // Same reasoning for CricbuzzHttpVendorParityTest, which byte-compares the vendored
        // copies of cricbuzz-http.js. Both paths must be declared: with only one, editing the
        // other leaves the task UP-TO-DATE and the drift ships.
        inputs.files(
            rootProject.file("appwrite/functions/match-sync/src/cricbuzz-http.js"),
            rootProject.file("appwrite/functions/api/src/player-search/cricbuzz-http.js"),
        )
            .withPropertyName("cricbuzzHttpVendorParitySources")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // CricBuzzScorecardRowFilterTest and CricBuzzFowPartnershipTest assert that match-sync's
        // scorecard parser reads player names, fall of wickets and partnerships the same way
        // CricBuzzParser.kt does. Same reasoning as above: undeclared, an edit to cricbuzz.js
        // leaves the task UP-TO-DATE and the parity checks never run.
        inputs.files(rootProject.file("appwrite/functions/match-sync/src/cricbuzz.js"))
            .withPropertyName("cricbuzzScorecardParitySource")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // SeriesClassificationParityTest compares the Kotlin category rules against series.js, and
        // MatchStatusParityTest the classifier lists against matchstate.js. Same reasoning again.
        inputs.files(
            rootProject.file("appwrite/functions/match-sync/src/series.js"),
            rootProject.file("appwrite/functions/match-sync/src/matchstate.js"),
            // FcmJsVendorParityTest: matchTopic's rule, which the device mirrors.
            rootProject.file("appwrite/functions/match-sync/src/fcm.js"),
        )
            .withPropertyName("winProbAndSeriesParitySources")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // ContextMathVendorParityTest byte-compares the two copies of contextmath.js — the trainer
        // computes the model's team, ground and player terms with it and match-sync recomputes
        // them for a live match. Both paths, for the same reason as above.
        inputs.files(
            rootProject.file("appwrite/functions/match-sync/src/contextmath.js"),
            rootProject.file("appwrite/functions/train-win-probability/src/contextmath.js"),
        )
            .withPropertyName("contextMathVendorParitySources")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // CricbuzzTeamListParityTest asserts the Kotlin team-list reading and tools/update_logos.py
        // pull the same teams and build the same crest URL from one captured page. Same reasoning
        // again: undeclared, editing the script leaves the task UP-TO-DATE and the parity never runs.
        inputs.files(rootProject.file("tools/update_logos.py"))
            .withPropertyName("teamLogoSyncParitySource")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // SearchNormalizationParityTest pins the app's normalizeForSearch to the fixture that
        // player-search's own test pins its JS copy to. Same reasoning: undeclared, an edit to the
        // fixture leaves the task UP-TO-DATE and only one side ever gets checked against it.
        inputs.files(
            rootProject.file("appwrite/functions/api/test/player-search/fixtures/search-normalization.json"),
        )
            .withPropertyName("searchNormalizationParityFixture")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // TeamVariantParityTest pins TeamLogoManager's variant rule to the fixture the weekly logo
        // tool reads. Same reasoning: undeclared, an edit to it leaves the task UP-TO-DATE.
        inputs.files(rootProject.file("tools/team_variants.json"))
            .withPropertyName("teamVariantParityFixture")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // SearchCorpusRankingTest ranks teams over the shipped curated codes ("aus" is Australia's,
        // Austria's is AUT). Same reasoning: undeclared, an edit to the asset leaves the task UP-TO-DATE.
        inputs.files(file("src/main/assets/TeamAcronyms.json"))
            .withPropertyName("teamAcronymsAsset")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // LogoInkTest pins the plate colours tools/team_sources.py measures crests against to the
        // app's surfaceVariant tokens. Same reasoning: undeclared, an edit leaves the task UP-TO-DATE.
        inputs.files(rootProject.file("tools/team_sources.py"))
            .withPropertyName("crestPlateParitySource")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // CommentaryRunsParityTest and MatchRowIdTest both assert on lines of match-sync's main.js.
        // It was the one file a parity test read that was not declared here, so on a warm build an
        // edit to it left the task UP-TO-DATE and neither check ran.
        inputs.files(rootProject.file("appwrite/functions/match-sync/src/main.js"))
            .withPropertyName("matchSyncMainParitySource")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    // MigrationTestHelper loads the exported Room schemas from the instrumentation APK's assets.
    // Without this the schemas sit in $projectDir/schemas, unreachable at runtime, and every test in
    // MigrationTest fails with "Cannot find the schema file in the assets folder" — which reads as a
    // broken migration but is only a missing asset path.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    // The app ships English strings only; without this every library's translations (AndroidX,
    // Material, Play services, Car App) are bundled for locales the UI never renders.
    androidResources {
        localeFilters += "en"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

composeCompiler {
    // Strong skipping is enabled by default since Kotlin 2.1+ — no explicit flag needed.

    // Write Compose compiler metrics to build/compose_metrics/ on every debug build.
    // Inspect them with the Compose Compiler Metrics Viewer to spot unstable/restartable
    // functions and reduce unnecessary recomposition.
    if (project.findProperty("enableComposeCompilerReports") == "true") {
        val dir = layout.buildDirectory.dir("compose_metrics")
        metricsDestination = dir
        reportsDestination = dir
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX Core — Stable aligned versions prevent runtime crashes
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    // Installs the bundled baseline profile on sideloaded and older installs; Play does it itself.
    implementation(libs.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.savedstate.ktx)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Navigation
    implementation(libs.navigation.compose)

    // Networking
    // The scrapers use Gson's JSON *tree* API (JsonObject/JsonParser) to walk Cricbuzz's flight
    // payloads. It is not reflective over app classes, so it needs no ProGuard keep rules. It is
    // not a removable duplicate of kotlinx.serialization: the Appwrite SDK depends on Gson itself.
    implementation(libs.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.jsoup)
    implementation(libs.appwrite)
    // Play in-app updates: a release uploaded at priority 5 replaces every Play install (AppUpdateGate).
    implementation(libs.play.app.update.ktx)

    // UI & Layout
    implementation(libs.palette)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    // Glance Widgets
    implementation(libs.glance)
    implementation(libs.glance.appwidget)

    // Core Engine
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Room (offline cache)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Firebase (requires google-services.json from Firebase Console to activate)
    //
    // firebase-analytics is deliberately absent. It pulls play-services-measurement, which pulls
    // play-services-ads-identifier — AdvertisingIdClient. Play reads the bundle's own SDK list
    // (BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb), so that library's mere
    // presence makes the advertising-ID declaration "yes" no matter what the manifest says;
    // removing the merged AD_ID permission alone left Play rejecting the edit. Nothing in the app
    // ever called FirebaseAnalytics, so the only loss is auto-collected screen views in the
    // Firebase console, against an advertising ID appearing in the store's Data safety card.
    // firebase-messaging does not depend on measurement, so FCM is unaffected.
    implementation(platform(libs.firebase.bom.lib))
    implementation(libs.firebase.messaging)

    // Car App (Android Auto)
    implementation(libs.car.app)

    // Security pins (#25 commons-lang3, #29 jdom2, #31 jose4j, #34/#35/#36 bouncycastle) live in
    // `configurations.all { resolutionStrategy.force }` below, which reaches every transitive use.
    // They used to be `implementation` dependencies too — nothing in the app imports them, and that
    // shipped ~1.24 MB of BouncyCastle (mostly post-quantum lookup tables) in every release.

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Pure-JVM DataStore, so BouncerPreferences can be exercised over a temp file
    // without an emulator.
    testImplementation(libs.datastore.preferences.core)
    // Real org.json impl so unit tests don't hit the mocked Android stub
    testImplementation(libs.json)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    // Compose layout contracts (a weight must be > 0, promotion rules, …) live in the
    // framework, not in our code, so they can only be asserted by composing on a device.
    // The BOM has to be repeated here: the `implementation` platform above does not reach the
    // androidTest configuration, so ui-test-junit4 resolves with no version without it.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)
}

configurations.all {
    resolutionStrategy {
        // The only pin left, because it is the only one of the eight that reached the app.
        // `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` lists exactly
        // two third-party libraries from that old list: guava (declared 31.1-android by a transitive,
        // raised to 33.7.1-android by this line) and jsoup. Nothing else here shipped.
        force("com.google.guava:guava:33.7.1-android")

        // Deleted, and the reason is worth keeping: Netty, Bouncy Castle, commons-lang3,
        // jose4j, jdom2 and httpclient were forced in d0d9fbf to clear a wall of Dependabot
        // alerts, without tracing where any of them came from. None of them is in the AAB.
        // `:app:dependencies` puts every one on `androidLintTool` or on the Unified Test
        // Platform's own configuration, under `com.android.tools.utp:android-device-provider-
        // ddmlib` -> io.grpc -> io.netty — AGP's instrumented-test runner, which is not built
        // into anything a user installs. The forces therefore silenced alerts about code that
        // does not ship, and did it by overriding the versions AGP's own tooling was tested
        // against: Netty 4.2 moved classes out of netty-codec into netty-codec-base, and grpc
        // 1.57 is compiled against 4.1. The alerts are dismissed as not-used instead, with
        // that command as the evidence.
    }
}

sentry {
    // Disable telemetry: the Sentry plugin's TelemetryService invokes sentry-cli at
    // Gradle configuration time (even during `./gradlew tasks`) to gather version info,
    // but resource extraction fails silently in the Gradle classloader, leaving a
    // non-existent sentry-cli-*.exe path which crashes every Gradle invocation.
    telemetry.set(false)

    // Skip ALL Sentry tasks (mapDebugSourceSetPaths, collectSources, bytecode transforms)
    // for debug builds.  Source context and proguard uploads are only useful in release;
    // running them in debug causes a 30+ minute hang when source files are on iCloud Drive.
    // nonMinifiedRelease/benchmarkRelease are created by the baseline-profile plugin to run the
    // generator and benchmarks; they must not upload mappings or source bundles.
    ignoredVariants.set(setOf("debug", "nonMinifiedRelease", "benchmarkRelease"))

    org.set("tanush-4w")
    projectName.set("android")
    authToken.set(
        localProperties.getProperty("SENTRY_USER_AUTH_TOKEN")
            ?: System.getenv("SENTRY_AUTH_TOKEN")
            ?: ""
    )
    includeSourceContext.set(true)

    // ── Performance & Tracing ────────────────────────────────────────────
    tracingInstrumentation {
        enabled.set(true)
        // COMPOSE instrumentation is excluded: it causes androidx.activity.compose.R to be
        // included in both Sentry ASM transform outputs, producing a duplicate-class DEX
        // error at merge time (AGP 9 + activity-compose 1.12.x).
        // FILE_IO is excluded too. It traced OkHttp/Coil disk-cache entries and Session Replay's
        // own segment files: over half of all spans, enough to push the activity transaction past
        // Sentry's size limit, and not one performance issue in 90 days (measured 2026-10-15).
        features.set(
            setOf(
                io.sentry.android.gradle.extensions.InstrumentationFeature.DATABASE,
                io.sentry.android.gradle.extensions.InstrumentationFeature.OKHTTP
            )
        )
    }

    // ── Auto-install Sentry SDK + integrations ──────────────────────────
    autoInstallation {
        enabled.set(true)
        // Keep runtime SDK current for Session Replay / Logs / Metrics feature parity.
        sentryVersion.set("8.43.2")
    }
}
