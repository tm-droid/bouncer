package com.tanush.bouncer.network

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Test score is written the same way on both sides of the wire.
 *
 * The match list builds it in `teamInningsScore` (TeamInningsScore.kt) from the flight payload; the match
 * detail header gets it from `match-sync`'s `teamInningsSides`. Both render the same match, one
 * above the other on the same screen, so a one-sided edit shows the same side's score in two
 * different notations — "266 & 83/2" on the card and "266/10 & 83/2" in the header — with nothing
 * to say which is right.
 *
 * `appwrite/functions/match-sync/src/cricbuzz.js` is declared as a Gradle test input in
 * `app/build.gradle.kts`; without that, editing it leaves `testDebugUnitTest` UP-TO-DATE and this
 * check never runs.
 */
class MultiInningsScoreParityTest {

    private val js by lazy {
        locate("appwrite/functions/match-sync/src/cricbuzz.js").readText(Charsets.UTF_8)
    }
    private val kotlin by lazy {
        locate("app/src/main/kotlin/com/tanush/bouncer/network/TeamInningsScore.kt")
            .readText(Charsets.UTF_8)
    }

    @Test
    fun `both sources are found, not silently skipped`() {
        assertTrue("cricbuzz.js appears empty", js.length > 1000)
        assertTrue("TeamInningsScore.kt appears empty", kotlin.length > 500)
        assertTrue("cricbuzz.js no longer exports teamInningsSides", js.contains("export function teamInningsSides"))
        assertTrue(
            "TeamInningsScore.kt no longer declares teamInningsScore",
            kotlin.contains("internal fun teamInningsScore"),
        )
    }

    @Test
    fun `the innings separator is the same on both sides`() {
        assertTrue("cricbuzz.js does not join innings with \" & \"", js.contains("""join(" & ")"""))
        assertTrue(
            "TeamInningsScore.kt does not join innings with \" & \"",
            kotlin.contains("""joinToString(" & ")"""),
        )
    }

    @Test
    fun `a completed innings drops its wickets at the same threshold`() {
        assertTrue("cricbuzz.js lost ALL_OUT_WICKETS = 10", js.contains("ALL_OUT_WICKETS = 10"))
        assertTrue("TeamInningsScore.kt lost ALL_OUT_WICKETS = 10", kotlin.contains("ALL_OUT_WICKETS = 10"))
    }

    @Test
    fun `the same two formats are treated as multi-day`() {
        assertTrue(
            "cricbuzz.js no longer treats TEST and FC as multi-day",
            js.contains("""MULTI_DAY_FORMATS = new Set(["TEST", "FC"])"""),
        )
        assertTrue(
            "TeamInningsScore.kt no longer treats TEST and FC as multi-day",
            kotlin.contains("""MULTI_DAY_FORMATS = setOf("TEST", "FC")"""),
        )
    }

    /** Walk up from the working directory until the repo-relative path resolves. */
    private fun locate(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("Could not locate $relative from ${File("").absolutePath}")
    }
}
