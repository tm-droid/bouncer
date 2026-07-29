import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry)
    alias(libs.plugins.google.services)
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$projectDir/config/detekt.yml")
    baseline = file("$projectDir/config/detekt-baseline.xml")
}

// detekt 1.23.6 cannot accept the JDK 25 default jvm-target; pin to the project's JVM level.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "17" }
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { jvmTarget = "17" }

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

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
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "RAPIDAPI_KEY", "\"${localProperties.getProperty("RAPIDAPI_KEY") ?: System.getenv("RAPIDAPI_KEY") ?: ""}\"")
        buildConfigField("String", "GROQ_API_KEY",      "\"${localProperties.getProperty("GROQ_API_KEY")      ?: System.getenv("GROQ_API_KEY")      ?: ""}\"")
        buildConfigField("String", "CEREBRAS_API_KEY",  "\"${localProperties.getProperty("CEREBRAS_API_KEY")  ?: System.getenv("CEREBRAS_API_KEY")  ?: ""}\"")
        buildConfigField("String", "SAMBANOVA_API_KEY", "\"${localProperties.getProperty("SAMBANOVA_API_KEY") ?: System.getenv("SAMBANOVA_API_KEY") ?: ""}\"")
        buildConfigField("String", "APPWRITE_ENDPOINT", "\"${localProperties.getProperty("APPWRITE_ENDPOINT") ?: System.getenv("APPWRITE_ENDPOINT") ?: ""}\"")
        buildConfigField("String", "APPWRITE_PROJECT_ID", "\"${localProperties.getProperty("APPWRITE_PROJECT_ID") ?: System.getenv("APPWRITE_PROJECT_ID") ?: ""}\"")
buildConfigField("String", "APPWRITE_AI_CACHE_DB_ID", "\"${localProperties.getProperty("APPWRITE_AI_CACHE_DB_ID") ?: System.getenv("APPWRITE_AI_CACHE_DB_ID") ?: ""}\"")
        buildConfigField("String", "APPWRITE_AI_CACHE_COL_ID", "\"${localProperties.getProperty("APPWRITE_AI_CACHE_COL_ID") ?: System.getenv("APPWRITE_AI_CACHE_COL_ID") ?: ""}\"")
        buildConfigField("String", "APPWRITE_FAVOURITES_COL_ID", "\"${localProperties.getProperty("APPWRITE_FAVOURITES_COL_ID") ?: System.getenv("APPWRITE_FAVOURITES_COL_ID") ?: ""}\"")
        buildConfigField("String", "HIGHLIGHTLY_API_KEY", "\"${localProperties.getProperty("HIGHLIGHTLY_API_KEY") ?: System.getenv("HIGHLIGHTLY_API_KEY") ?: ""}\"")
        buildConfigField("String", "APPWRITE_HIGHLIGHTS_DB_ID", "\"${localProperties.getProperty("APPWRITE_HIGHLIGHTS_DB_ID") ?: System.getenv("APPWRITE_HIGHLIGHTS_DB_ID") ?: ""}\"")
        buildConfigField("String", "APPWRITE_HIGHLIGHTS_COL_ID", "\"${localProperties.getProperty("APPWRITE_HIGHLIGHTS_COL_ID") ?: System.getenv("APPWRITE_HIGHLIGHTS_COL_ID") ?: ""}\"")
        buildConfigField("String", "APPWRITE_PLAYER_FORMS_DB_ID", "\"${localProperties.getProperty("APPWRITE_PLAYER_FORMS_DB_ID") ?: System.getenv("APPWRITE_PLAYER_FORMS_DB_ID") ?: ""}\"")
        buildConfigField("String", "APPWRITE_PLAYER_PROFILES_COL_ID", "\"${localProperties.getProperty("APPWRITE_PLAYER_PROFILES_COL_ID") ?: System.getenv("APPWRITE_PLAYER_PROFILES_COL_ID") ?: ""}\"")
        buildConfigField("String", "BOUNCER_FUNCTION_SECRET", "\"${localProperties.getProperty("BOUNCER_FUNCTION_SECRET") ?: System.getenv("BOUNCER_FUNCTION_SECRET") ?: ""}\"")
        manifestPlaceholders["sentryDsn"] = localProperties.getProperty("SENTRY_DSN")
            ?: System.getenv("SENTRY_DSN")
            ?: ""
        // Default environment; overridden per build type below
        manifestPlaceholders["sentryEnvironment"] = "production"
        manifestPlaceholders["sentryDebug"] = "false"

        // Only include 64-bit ABIs — androidx.graphics.path native lib doesn't ship armeabi-v7a
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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
            manifestPlaceholders["sentryEnvironment"] = "debug"
            manifestPlaceholders["sentryDebug"] = "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Surface all warnings as errors in CI; locally they remain warnings.
        abortOnError = false
        checkReleaseBuilds = true
        // Write machine-readable report alongside the default HTML one.
        xmlReport = true
        htmlReport = true
        // Ignore auto-generated code (R.*, databinding, etc.)
        ignoreTestSources = true
        // Baseline captures pre-existing issues so new code is held to a higher bar.
        // baseline = file("lint-baseline.xml")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // MigrationTestHelper loads the exported Room schemas from the instrumentation APK's assets.
    // Without this the schemas sit in $projectDir/schemas, unreachable at runtime, and every test in
    // MigrationTest fails with "Cannot find the schema file in the assets folder" — which reads as a
    // broken migration but is only a missing asset path.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

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
    // Avoid duplicate logging artifacts
    modules {
        module("io.github.oshai:kotlin-logging-android-debug") {
            replacedBy("io.github.oshai:kotlin-logging-android", "Use the main artifact")
        }
    }

    // AndroidX Core — Stable aligned versions prevent runtime crashes
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
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
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Navigation
    implementation(libs.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.jsoup)
    implementation(libs.appwrite)

    // UI & Layout
    implementation(libs.palette)
    implementation(libs.window)
    implementation(libs.material3.windowsizeclass)
    implementation(libs.work.runtime.ktx)
    implementation(libs.play.app.update.ktx)

    // Glance Widgets
    implementation(libs.glance)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Core Engine
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlin.logging.android)

    // Room (offline cache)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Paging 3
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Firebase (requires google-services.json from Firebase Console to activate)
    implementation(platform(libs.firebase.bom.lib))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)

    // Car App (Android Auto)
    implementation(libs.car.app)

    // Security: explicit declarations at patched versions so Dependabot closes CVE alerts
    // (#25 commons-lang3, #29 jdom2, #31 jose4j, #34/#35/#36 bouncycastle)
    implementation(libs.commons.lang3)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.jose4j)
    implementation(libs.jdom2)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json impl so unit tests don't hit the mocked Android stub
    testImplementation(libs.json)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)
}

configurations.all {
    resolutionStrategy {
        // Netty — covers all Netty CVEs (#17,18,21–24,26–28,30,32,33,37–45)
        force("io.netty:netty-codec-http:4.2.15.Final")
        force("io.netty:netty-codec-http2:4.2.15.Final")
        force("io.netty:netty-codec:4.2.15.Final")
        force("io.netty:netty-common:4.2.15.Final")
        force("io.netty:netty-handler:4.2.15.Final")
        force("io.netty:netty-handler-proxy:4.2.15.Final")
        // Bouncy Castle (#34,35,36)
        force("org.bouncycastle:bcprov-jdk18on:1.84")
        force("org.bouncycastle:bcpkix-jdk18on:1.84")
        // Other transitive CVEs
        force("org.apache.commons:commons-lang3:3.20.0")
        force("org.bitbucket.b_c:jose4j:0.9.6")
        force("org.jdom:jdom2:2.0.6.1")
        force("com.google.guava:guava:33.6.0-android")
        force("org.apache.httpcomponents:httpclient:4.5.13")
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
    ignoredVariants.set(setOf("debug"))

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
        features.set(
            setOf(
                io.sentry.android.gradle.extensions.InstrumentationFeature.DATABASE,
                io.sentry.android.gradle.extensions.InstrumentationFeature.FILE_IO,
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
