package com.tanush.bouncer.services

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build when the two copies of `llm.js` stop being byte-identical.
 *
 * `match-sync` and `generate-commentary` are the two halves of one commentary pipeline — the first
 * narrates a ball for everyone, the second narrates it for the device that asked — so they must
 * share one provider chain, one SYSTEM_INSTRUCTION, one sanitizer and one event classifier.
 * Drift between them reads as two house styles inside a single match.
 *
 * This check exists because the drift had already happened and was invisible: the
 * `generate-commentary` copy carried a header declaring itself a "BYTE-IDENTICAL MIRROR" of the
 * `match-sync` one while the two differed by 60 lines. `match-sync` had `classifyEvent` and
 * `WICKET_VETO_RE` — the veto that stops a dropped catch or a dismissal off a no-ball being
 * recorded as a wicket — and `generate-commentary` had neither. A comment asserting an invariant
 * is not an invariant; only a failing build is.
 *
 * Duplicated rather than shared for the same reason as `cricbuzz-http.js`:
 * `tools/deploy-function.mjs` builds each deployment with `tar -C <that function's dir> .`, so
 * nothing outside a function's own directory reaches the container.
 *
 * Both paths are declared as inputs of the test task in `app/build.gradle.kts`. Without that, a
 * file outside the module never invalidates the task, the check stays UP-TO-DATE, and the drift
 * ships past a green build.
 */
class LlmJsVendorParityTest {

    private val canonicalPath = "appwrite/functions/match-sync/src/llm.js"
    private val vendoredPaths = listOf("appwrite/functions/generate-commentary/src/llm.js")

    @Test
    fun `every copy is found and looks like the llm module, not silently skipped`() {
        // Guards the guard: if a rename made the locator return empty text, the byte comparison
        // below would pass vacuously — the exact shape of failure this file exists to prevent.
        for ((path, text) in listOf(canonicalPath to canonical) + vendoredPaths.map { it to read(it) }) {
            assertTrue("$path appears empty", text.length > 1000)
            assertTrue(
                "$path does not look like the llm module",
                text.contains("const PROVIDERS") &&
                    text.contains("SYSTEM_INSTRUCTION") &&
                    text.contains("export function classifyEvent"),
            )
        }
    }

    @Test
    fun `every vendored copy is byte-identical to the canonical one`() {
        for (path in vendoredPaths) {
            assertEquals(
                "llm.js drifted between $canonicalPath and $path. Both functions write commentary " +
                    "for the same match and must share one provider chain, one prompt, one " +
                    "sanitizer and one classifier. Copy the canonical file over $path — do not " +
                    "delete this check.",
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
