package com.tanush.bouncer.services

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build when the two copies of `nextflight.js` stop being byte-identical.
 *
 * Cricbuzz serves App Router pages, so every server-side reader of one — the match list, the live
 * page, the scorecard — has to walk the same `self.__next_f.push(...)` stream. `match-sync` and
 * `poll-live-matches` both do, out of two files, because `tools/deploy-function.mjs` builds each
 * deployment with `tar -C <that function's dir> .` and nothing outside a function's own directory
 * reaches the container.
 *
 * A drift here is quiet in the worst way: the walker would simply return fewer payloads, and the
 * caller would see a short list rather than an error. `poll-live-matches` writes that list to
 * `live-matches`, which every server-side surface runs off.
 *
 * Both paths are declared as inputs of the test task in `app/build.gradle.kts`. Without that, a
 * file outside the module never invalidates the task, the check stays UP-TO-DATE, and the drift
 * ships past a green build.
 */
class NextFlightVendorParityTest {

    private val canonicalPath = "appwrite/functions/match-sync/src/nextflight.js"
    private val vendoredPaths = listOf("appwrite/functions/poll-live-matches/src/nextflight.js")

    @Test
    fun `every copy is found and looks like the flight module, not silently skipped`() {
        // Guards the guard: if a rename made the locator return empty text, the byte comparison
        // below would pass vacuously — the exact shape of failure this file exists to prevent.
        for ((path, text) in listOf(canonicalPath to canonical) + vendoredPaths.map { it to read(it) }) {
            assertTrue("$path appears empty", text.length > 500)
            assertTrue(
                "$path does not look like the flight-payload module",
                text.contains("self.__next_f.push(") && text.contains("forEachNextFlightPayload"),
            )
        }
    }

    @Test
    fun `every vendored copy is byte-identical to the canonical one`() {
        for (path in vendoredPaths) {
            assertEquals(
                "nextflight.js drifted between $canonicalPath and $path. Both functions read the " +
                    "same Cricbuzz pages and must walk the flight stream the same way; a narrower " +
                    "copy returns a short list rather than an error. Copy the canonical file over " +
                    "$path — do not delete this check.",
                canonical,
                read(path),
            )
        }
    }

    private val canonical: String by lazy { read(canonicalPath) }

    private fun read(relativePath: String): String = locate(relativePath).readText(Charsets.UTF_8)

    /**
     * Resolved by walking up from the test's working directory (Gradle runs unit tests with the
     * module directory as `user.dir`, so the repo root sits one level up). A missing file is a hard
     * failure, never a skipped test.
     */
    private fun locate(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error(
            "could not find $relativePath by walking up from ${System.getProperty("user.dir")}. " +
                "If the function moved, update the path — do not delete this check."
        )
    }
}
