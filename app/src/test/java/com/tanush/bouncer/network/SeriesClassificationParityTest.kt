package com.tanush.bouncer.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build when the Kotlin series classifier and `match-sync/src/series.js` disagree.
 *
 * The same series is classified on both sides of the wire: `bouncer-match-sync` (its match-list poll) writes
 * `category` and `isWomens` into the `active-series` bucket file, and the device recomputes both
 * whenever it falls back to its own scrape. A drift therefore shows up as one competition changing
 * category depending on which path happened to run — and nothing in either file would report it.
 * Both files already carried a comment saying they must stay identical. A comment is not a check.
 *
 * The patterns are read out of the real JS rather than restated here, because a copy in this file
 * would be a third place to keep in sync and would pass while both real copies drifted together.
 *
 * `series.js` is declared as an input of the test task in `app/build.gradle.kts`; without that an
 * edit to it leaves the task UP-TO-DATE and this never runs.
 */
class SeriesClassificationParityTest {

    private val relativePath = "appwrite/functions/match-sync/src/series.js"

    /** Each `/pattern/flags.test(` in source order — the only regex shape either function uses. */
    private val literals: List<Pair<String, String>> by lazy {
        Regex("""/((?:[^/\\\n]|\\.)+)/([a-z]*)\.test\(""")
            .findAll(source)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
    }

    @Test
    fun `the js source is found and parsed, not silently skipped`() {
        // Guards the guard. An empty file or a changed regex shape would make every comparison
        // below vacuous, which is the failure mode this file exists to prevent.
        assertTrue("series.js appears empty", source.length > 200)
        assertTrue(
            "series.js does not look like the expected module",
            source.contains("export function isWomensSeries") && source.contains("export function seriesCategory"),
        )
        assertEquals(
            "expected exactly three `/…/.test(` literals in series.js — women, league, domestic. " +
                "If the shape changed, update this parser; do not delete the check.",
            3,
            literals.size,
        )
    }

    @Test
    fun `the womens pattern is identical on both sides`() {
        val (pattern, flags) = literals[0]
        assertEquals(
            "isWomensSeries drifted. Women's cricket is an axis of its own, and the two sides " +
                "labelling the same competition differently is the defect this pair exists to avoid.",
            WOMENS.pattern,
            pattern,
        )
        assertCaseInsensitive(flags, WOMENS)
    }

    @Test
    fun `the league pattern is identical on both sides`() {
        val (pattern, flags) = literals[1]
        assertEquals("the LEAGUE pattern drifted from series.js", LEAGUE.pattern, pattern)
        assertCaseInsensitive(flags, LEAGUE)
    }

    @Test
    fun `the domestic pattern is identical on both sides`() {
        val (pattern, flags) = literals[2]
        assertEquals("the DOMESTIC pattern drifted from series.js", DOMESTIC.pattern, pattern)
        assertCaseInsensitive(flags, DOMESTIC)
    }

    @Test
    fun `the category chain is tested in the same order on both sides`() {
        // First-match-wins on both sides, so the ORDER is part of the contract: league before
        // domestic. Swapping them would make every "Domestic T20" change category.
        val body = source.substringAfter("export function seriesCategory")
        assertTrue(
            "series.js must return LEAGUE before DOMESTIC",
            body.indexOf("\"LEAGUE\"") < body.indexOf("\"DOMESTIC\""),
        )
    }

    private fun assertCaseInsensitive(flags: String, kotlin: Regex) {
        // Case sensitivity is part of the contract on both sides: competition names arrive in
        // whatever case the site used.
        assertTrue("series.js pattern must carry the i flag", "i" in flags)
        assertTrue(
            "the Kotlin pattern must be IGNORE_CASE",
            RegexOption.IGNORE_CASE in kotlin.options,
        )
    }

    private val source: String by lazy { locate().readText(Charsets.UTF_8) }

    private fun locate(): File {
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
