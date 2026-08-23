# ──────────────────────────────────────────────────────────────────────────────
# Bouncer — ProGuard / R8 Rules
# ──────────────────────────────────────────────────────────────────────────────

# ── Retrofit ──────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit does reflection on generic parameters. InnerClasses is required to use
# Signature and EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep annotation default values (e.g., retrofit2.http.Field.encoded).
-keepattributes AnnotationDefault

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ── Gson ──────────────────────────────────────────────────────────────────────
# Only the JSON *tree* API is still used (JsonObject/JsonParser, in the scrapers). That is not
# reflective over app classes, so the model keeps that used to live here are gone:
#   -keep class com.tanush.bouncer.models.** { *; }
#   -keep class com.tanush.bouncer.network.*Dto { *; }
# Serialization moved to kotlinx.serialization, whose serializers are generated at compile time
# and need no reflection. Signature is still needed for generic type information at runtime.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# ── Appwrite SDK ──────────────────────────────────────────────────────────────
# The SDK deserializes with reflective Gson — every Realtime frame goes through
# `Gson.fromJson` in `io.appwrite.services.Realtime$AppwriteWebSocketListener.onMessage` — and
# sdk-for-android 25.1.0 ships **no consumer ProGuard rules** (verified: the AAR contains no
# proguard.txt). R8 therefore sees no constructor call for the model classes, makes them abstract,
# and the first Realtime message kills the process:
#
#   Abstract classes can't be instantiated! ... Class name: io.appwrite.models.RealtimeResponse
#
# This is release-only and invisible to every gate the project has: it compiles, all 646 unit tests
# pass, lint and detekt are clean. It was found by installing the minified APK on a device and
# opening the app. No rule for `io.appwrite` has ever existed here, so this is not fallout from
# dropping the app's own Gson keeps — the app has no reflective Gson left at all.
#
# Enums are kept alongside the models because Gson matches enum constants by field name, which R8
# renames.
-keep class io.appwrite.models.** { *; }
-keep class io.appwrite.enums.** { *; }

# ── Jsoup ─────────────────────────────────────────────────────────────────────
-keep class org.jsoup.** { *; }
-keeppackagenames org.jsoup.nodes

# ── OkHttp ────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Coil ──────────────────────────────────────────────────────────────────────

# ── Kotlin Serialization ─────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Sentry ────────────────────────────────────────────────────────────────────
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── General ───────────────────────────────────────────────────────────────────
# `-keep class com.tanush.bouncer.** { *; }` used to sit here. It kept every class, field and
# method in the application package, so `isMinifyEnabled = true` and proguard-android-optimize.txt
# were both cosmetic: nothing of ours was shrunk, obfuscated or inlined, across 400 Kotlin files.
# It existed for reflective Gson, which is gone. Do not reintroduce it — reach for a targeted keep,
# or @Keep on the specific type.
#
# Reflection roots that DO still need keeping are declared where they belong: manifest-registered
# components and WorkManager workers are kept by AGP's own rules, and kotlinx.serialization ships
# its own consumer rules.
