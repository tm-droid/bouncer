import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")

    id("io.sentry.android.gradle") version libs.versions.sentry.gradle.get()
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// Redirect build outputs to ~/Library/Caches so they live outside the iCloud-synced
// Documents folder.  iCloud modifies file metadata on every sync, which invalidates
// Gradle's incremental task outputs and forces mergeResources / dex / etc. to re-run
// from scratch on every single build.
layout.buildDirectory.set(
    file("${System.getProperty("user.home")}/Library/Caches/bouncer-android/app")
)

android {
    namespace = "com.tanush.bouncer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tanush.bouncer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "OPENROUTER_API_KEY", "\"${localProperties.getProperty("OPENROUTER_API_KEY") ?: System.getenv("OPENROUTER_API_KEY") ?: ""}\"")
        buildConfigField("String", "RAPIDAPI_KEY", "\"${localProperties.getProperty("RAPIDAPI_KEY") ?: System.getenv("RAPIDAPI_KEY") ?: ""}\"")
        manifestPlaceholders["sentryDsn"] = localProperties.getProperty("SENTRY_DSN", "https://e9ade5d835c36a97939191077734e5a7@o4510958403518464.ingest.us.sentry.io/4510963429212160")
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

dependencies {
    // Avoid duplicate logging artifacts
    modules {
        module("io.github.oshai:kotlin-logging-android-debug") {
            replacedBy("io.github.oshai:kotlin-logging-android", "Use the main artifact")
        }
    }

    // AndroidX Core — Stable aligned versions prevent runtime crashes
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
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
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)

    // Navigation
    implementation(libs.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.jsoup)

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
    implementation(libs.firebase.messaging)

    // Car App (Android Auto)
    implementation(libs.car.app)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)
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
        sentryVersion.set("8.35.0")
    }
}
