package com.tanush.bouncer.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `parseBallRuns` must read the same clause in both languages.
 *
 * The copy that matters is `match-sync`'s: it writes the `runs` column every device reads, from the
 * RAW Cricbuzz text, before the AI rewrite deletes the number. The Kotlin copy in
 * `BallOutcome.kt` runs only where that column is absent — a row written before it existed,
 * or the on-device scrape fallback — so a one-sided edit is invisible until a row with no column
 * lands on a device and disagrees with the server about what the ball was worth.
 *
 * `appwrite/functions/match-sync/src/cricbuzz.js` is declared as a Gradle test input in
 * `app/build.gradle.kts`; without that, editing it leaves `testDebugUnitTest` UP-TO-DATE and this
 * check never runs.
 *
 * One asymmetry is deliberate and is NOT compared here: the Kotlin side additionally accepts a
 * literal "no run" anywhere in broadcast prose. That is a device-only fallback for AI-rewritten
 * text, which the server never sees — it classifies before the rewrite.
 */
class CommentaryRunsParityTest {

    private val js by lazy {
        // Two files, one source of truth. `BALL_OUTCOME` and `TRAILING_PUNCTUATION` moved to
        // llm.js when classifyEvent started reading the outcome clause before the prose: llm.js is
        // vendored byte-identically into `generate-commentary`, which has no cricbuzz.js, so the
        // import has to run cricbuzz.js -> llm.js and not the other way. Both are declared Gradle
        // test inputs, so an edit to either still invalidates this task.
        locate("appwrite/functions/match-sync/src/cricbuzz.js").readText(Charsets.UTF_8) +
            "\n" +
            locate("appwrite/functions/match-sync/src/llm.js").readText(Charsets.UTF_8)
    }
    private val kotlin by lazy {
        locate(
            "app/src/main/kotlin/com/tanush/bouncer/ui/screens/matchdetail/v2/BallOutcome.kt"
        ).readText(Charsets.UTF_8)
    }

    @Test
    fun `both sources are found and parsed, not silently skipped`() {
        // Guards the guard: a locator that quietly returned empty text would pass every comparison
        // below vacuously.
        assertTrue("the JS sources appear empty", js.length > 1000)
        assertTrue("BallOutcome.kt appears empty", kotlin.length > 1000)
        assertTrue(
            "cricbuzz.js no longer exports parseBallOutcome",
            js.contains("export function parseBallOutcome"),
        )
        assertTrue(
            "BallOutcome.kt no longer declares parseBallOutcome",
            kotlin.contains("internal fun parseBallOutcome"),
        )
    }

    @Test
    fun `the outcome-clause patterns are identical`() {
        for ((name, pattern) in EXPECTED) {
            assertEquals(
                "$name drifted between cricbuzz.js and BallOutcome.kt — reconcile both " +
                    "copies. A one-sided edit means the server and the device disagree about what " +
                    "a delivery was worth.",
                pattern,
                jsRegex(name),
            )
            assertEquals(
                "$name drifted between cricbuzz.js and BallOutcome.kt.",
                pattern,
                kotlinRegex(name),
            )
        }
    }

    @Test
    fun `match-sync writes the runs and extra columns beside the event column`() {
        // The Kotlin fallback exists for rows that predate the column. If the write is dropped it
        // becomes the ONLY source, silently, and every AI-rewritten line loses its pill again.
        val main = locate("appwrite/functions/match-sync/src/main.js").readText(Charsets.UTF_8)
        assertTrue(
            "main.js no longer computes the outcome from the raw text",
            main.contains("const { runs, kind: extra } = parseBallOutcome(clean);"),
        )
        assertTrue(
            "main.js no longer writes the runs column",
            main.contains("...(runs === null ? {} : { runs }),"),
        )
        assertTrue(
            "main.js no longer writes the extra column",
            main.contains("...(extra === null ? {} : { extra }),"),
        )
    }

    /** `const NAME = /pattern/flags;` → the pattern, unescaped-as-written. */
    private fun jsRegex(name: String): String {
        val m = Regex("""const $name = /(.*?)/[gimsuy]*;""").find(js)
            ?: error("neither cricbuzz.js nor llm.js has `const $name = /…/`")
        return m.groupValues[1]
    }

    /** `private val NAME = Regex("pattern"…)` → the pattern, with Kotlin's escaping undone. */
    private fun kotlinRegex(name: String): String {
        val m = Regex("""private val $name =\s*\n?\s*Regex\("((?:[^"\\]|\\.)*)"""").find(kotlin)
            ?: error("BallOutcome.kt has no `private val $name = Regex(\"…\")`")
        return m.groupValues[1].replace("\\\\", "\\").replace("\\\"", "\"")
    }

    private fun locate(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("could not find $relativePath from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val EXPECTED = listOf(
            "BALL_OUTCOME" to """^[^,]+,\s*([^,]+)""",
            "TRAILING_PUNCTUATION" to """[.!]+$""",
            "RUNS_OFF_BAT" to """^(\d{1,3}) runs?$""",
            "NO_RUN" to """^no run$""",
            "OUTCOME_FOUR" to """^four$""",
            "OUTCOME_SIX" to """^six$""",
            "OUTCOME_WICKET" to """^(?:out\b|that'?s out\b)""",
            "EXTRA_KIND" to
                """^(?:(\d{1,3})\s*)?(wd|wides?|nb|no.?balls?|lb|leg ?byes?|byes?|b)$""",
            "EXTRA_FOLLOW" to """^[^,]+,\s*[^,]+,\s*(?:(\d{1,3}) runs?|(four|six))\b""",
            "KIND_WIDE" to """^(?:wd|wides?)$""",
            "KIND_NOBALL" to """^(?:nb|no.?balls?)$""",
            "KIND_LEGBYE" to """^(?:lb|leg ?byes?)$""",
            "KIND_BYE" to """^(?:byes?|b)$""",
        )
    }
}
