package com.tanush.bouncer.services

import com.tanush.bouncer.data.AppJson
import kotlinx.serialization.Serializable

/**
 * When an install must update before it can be used. Two signals, either one sufficient:
 *
 * - **Play's in-app update priority.** CI uploads a release at [FORCED_PRIORITY] when its push
 *   carries a `Force-Update: yes` trailer (`ci.yml`). Play reports the highest priority of every
 *   version between the installed one and the latest, so a tester who skipped the forced release
 *   still gets it.
 * - **A minimum versionCode** in the `app-config` file of the `cricket-stats` bucket, set with
 *   `appwrite/tools/set-min-version.mjs`. It reaches installs Play has no update for: a sideloaded
 *   CI APK, or a Play Store whose update cache has not refreshed yet.
 *
 * Both exist so a server change that old builds cannot survive (a deleted function, a renamed
 * table) can retire those builds instead of leaving them silently broken.
 */
object AppUpdatePolicy {
    /** The in-app update priority Play's range tops out at; anything lower is optional. */
    const val FORCED_PRIORITY = 5

    /**
     * CI builds are `1000 + run number` (`ci.yml`); a local build is 8. A local build is never
     * gated, or a live minimum would lock the developer out of their own debug build.
     */
    const val CI_VERSION_CODE_FLOOR = 1000

    fun playForces(installed: Int, updateAvailable: Boolean, priority: Int): Boolean =
        installed >= CI_VERSION_CODE_FLOOR && updateAvailable && priority >= FORCED_PRIORITY

    fun belowMinimum(installed: Int, minimum: Int?): Boolean =
        installed >= CI_VERSION_CODE_FLOOR && minimum != null && installed < minimum

    /** The published minimum, or null when the file is unreadable or sets none. */
    fun parseMinimum(bytes: ByteArray): Int? =
        runCatching { AppJson.decodeFromString<AppConfig>(bytes.decodeToString()).minVersionCode }
            .getOrNull()
            ?.takeIf { it > 0 }

    @Serializable
    internal data class AppConfig(val minVersionCode: Int = 0)
}
