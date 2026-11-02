package com.tanush.bouncer.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build when the copies of `cricbuzz-http.js` stop being byte-identical.
 *
 * `match-sync` and `player-search` both scrape www.cricbuzz.com, and both must do it with the
 * same browser headers, the same rotated User-Agent, the same 350 ms request spacing and the same
 * 90 s block-aware backoff. Those measures exist because of ANDROID-35 — four consecutive
 * per-minute 403s on 2026-08-04, four minutes after request volume rose — and the match-list poller
 * (then its own function, now inside match-sync) previously had none of them, sending one
 * hardcoded User-Agent with no spacing at all.
 *
 * The file is duplicated rather than shared because it has to be: `tools/deploy-function.mjs`
 * builds each deployment with `tar -C <that function's dir> .`, so nothing outside a function's own
 * directory reaches the container. A symlink or a parent-directory import would deploy broken.
 *
 * A vendored copy nothing checks is a copy that rots, and the failure mode of rot here is silent:
 * one function keeps its politeness measures, the other quietly loses them, and the only symptom is
 * a 403 burst weeks later. Hence a byte comparison rather than a review note.
 */
class CricbuzzHttpVendorParityTest {

    private val canonicalPath = "appwrite/functions/match-sync/src/cricbuzz-http.js"

    /** Every function that scrapes www.cricbuzz.com carries its own copy. Add new ones here. */
    private val vendoredPaths = listOf(
        "appwrite/functions/api/src/player-search/cricbuzz-http.js",
    )

    @Test
    fun `every copy is found and looks like the throttle module, not silently skipped`() {
        // Guards the guard: if a rename made the locator return empty text, the byte comparison
        // below would pass vacuously — the exact shape of failure this file exists to prevent.
        for ((path, text) in listOf(canonicalPath to canonical) + vendoredPaths.map { it to read(it) }) {
            assertTrue("$path appears empty", text.length > 1000)
            assertTrue(
                "$path does not look like the throttle module",
                text.contains("export async function fetchHtml") &&
                    text.contains("MIN_REQUEST_GAP_MS") &&
                    text.contains("BLOCK_BACKOFF_MS") &&
                    text.contains("Sec-Fetch-Dest"),
            )
        }
    }

    @Test
    fun `every vendored copy is byte-identical to the canonical one`() {
        for (path in vendoredPaths) {
            assertEquals(
                "cricbuzz-http.js drifted between $canonicalPath and $path. Every function here " +
                    "scrapes the same host and must share one politeness contract; a one-sided " +
                    "edit leaves the others sending unthrottled requests. Copy the canonical file " +
                    "over $path — do not delete this check.",
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
