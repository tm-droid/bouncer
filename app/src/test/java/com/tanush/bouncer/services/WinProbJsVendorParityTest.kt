package com.tanush.bouncer.services

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build when the two copies of `winprob.js` stop being byte-identical.
 *
 * `bouncer-match-sync` scores every live match on its one-minute run and publishes the number to
 * `live-winprob` and the detail row; `bouncer-win-probability` answers the same question on
 * demand. They run the same `evaluate`/`evaluateLong` over the same `team-stats` model out of two
 * separate files, because `tools/deploy-function.mjs` builds each deployment with
 * `tar -C <that function's dir> .` and nothing outside a function's own directory reaches the
 * container.
 *
 * The two were identical when this was written and nothing enforced it. A drift would be
 * particularly quiet: both copies would still return a plausible probability, and the disagreement
 * would show only as the same match reading differently depending on which function was asked —
 * exactly the shape of defect this repo has already hit with `llm.js`, where a header declaring
 * one file a "BYTE-IDENTICAL MIRROR" of the other sat above a 60-line difference. A comment
 * asserting an invariant is not an invariant; only a failing build is.
 *
 * Both paths are declared as inputs of the test task in `app/build.gradle.kts`. Without that, a
 * file outside the module never invalidates the task, the check stays UP-TO-DATE, and the drift
 * ships past a green build.
 */
class WinProbJsVendorParityTest {

    private val canonicalPath = "appwrite/functions/match-sync/src/winprob.js"
    private val vendoredPaths = listOf("appwrite/functions/win-probability/src/winprob.js")

    @Test
    fun `every copy is found and looks like the winprob module, not silently skipped`() {
        // Guards the guard: a rename that made the locator return empty text would let the byte
        // comparison below pass vacuously.
        for ((path, text) in listOf(canonicalPath to canonical) + vendoredPaths.map { it to read(it) }) {
            assertTrue("$path appears empty", text.length > 1000)
            assertTrue(
                "$path does not look like the winprob module",
                text.contains("export function evaluate") && text.contains("evaluateLong"),
            )
        }
    }

    @Test
    fun `every vendored copy is byte-identical to the canonical one`() {
        for (path in vendoredPaths) {
            assertEquals(
                "winprob.js drifted between $canonicalPath and $path. Both functions answer the " +
                    "same question about the same match from the same model, so a difference here " +
                    "is one match with two probabilities. Copy the canonical file over $path — do " +
                    "not delete this check.",
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
