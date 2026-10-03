package com.tanush.bouncer.services

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build when the three copies of `contextmath.js` stop being byte-identical.
 *
 * The win-probability model reads what is known about the two sides, the ground and the elevens.
 * `bouncer-train-win-probability` computes those terms for every historical match when it fits the
 * model; `bouncer-match-sync` and `bouncer-win-probability` compute them again for a live one. If a
 * shrinkage constant, a scale or the order of a vector differs between the copies, nothing breaks:
 * the model is handed a number that means something else and answers with a plausible
 * probability. The same reasoning as [WinProbJsVendorParityTest], one file further in.
 *
 * All three paths are declared as inputs of the test task in `app/build.gradle.kts`.
 */
class ContextMathVendorParityTest {

    private val canonicalPath = "appwrite/functions/match-sync/src/contextmath.js"
    private val vendoredPaths = listOf(
        "appwrite/functions/win-probability/src/contextmath.js",
        "appwrite/functions/train-win-probability/src/contextmath.js",
    )

    @Test
    fun `every copy is found and looks like the context maths, not silently skipped`() {
        for ((path, text) in listOf(canonicalPath to canonical) + vendoredPaths.map { it to read(it) }) {
            assertTrue("$path appears empty", text.length > 1000)
            assertTrue(
                "$path does not look like the context maths",
                text.contains("export function contextFeatures") && text.contains("export function sideContext"),
            )
        }
    }

    @Test
    fun `every vendored copy is byte-identical to the canonical one`() {
        for (path in vendoredPaths) {
            assertEquals(
                "contextmath.js drifted between $canonicalPath and $path. The trainer and the " +
                    "readers must compute the model's context terms identically, or a live match " +
                    "is scored on numbers the model was never fitted to. Copy the canonical file " +
                    "over $path — do not delete this check.",
                canonical,
                read(path),
            )
        }
    }

    private val canonical: String by lazy { read(canonicalPath) }

    private fun read(relativePath: String): String = locate(relativePath).readText(Charsets.UTF_8)

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
