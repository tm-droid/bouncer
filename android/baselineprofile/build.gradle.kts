import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generates app/src/release/generated/baselineProfiles/baseline-prof.txt and runs the startup
// benchmark. Needs a connected device or emulator (API 28+):
//   ./gradlew :app:generateReleaseBaselineProfile
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.tanush.bouncer.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Profile capture needs API 28+; the app itself still supports 26.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
