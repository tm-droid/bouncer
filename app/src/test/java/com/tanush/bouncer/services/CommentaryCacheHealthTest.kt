package com.tanush.bouncer.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Regression check for the `ai-commentary-cache` contents.
 *
 * The known-bad fixture is the real export taken on 2026-07-30, before any of the fixes landed.
 * Every check must fire against it — a check that passes on this data is not testing anything, and
 * that is the failure mode this file exists to prevent. The healthy sample is the same commentary
 * as it should look once the pipeline is correct.
 *
 * To scan a fresh export instead, pass its path:
 * ```
 * ./gradlew testDebugUnitTest --tests "*CommentaryCacheHealthTest" \
 *   -Dcommentary.cache.csv=/path/to/export.csv
 * ```
 */
class CommentaryCacheHealthTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    // --- known-bad export ----------------------------------------------------

    @Test
    fun `the pre-fix export fails every check`() {
        val rows = CommentaryCacheHealth.commentaryColumnOf(
            fixture("ai_commentary_cache_export_2026-07-30.csv")
        )
        assertTrue("expected the full export, parsed ${rows.size} rows", rows.size > 500)

        val report = CommentaryCacheHealth.analyse(rows)
        val fired = report.violations.map { it.rule }.toSet()

        // Named individually rather than asserting a count: a check silently dropping out of the
        // set is exactly the regression this test is here to catch.
        for (rule in listOf(
            "single-ball-number-share",
            "exact-duplicates",
            "near-duplicates",
            "meta-responses",
            "raw-scraper-lines",
        )) {
            assertTrue(
                "expected $rule to fire on the known-bad export; fired: $fired",
                rule in fired
            )
        }
    }

    @Test
    fun `the pre-fix export shows the fabricated ball number dominating`() {
        val rows = CommentaryCacheHealth.commentaryColumnOf(
            fixture("ai_commentary_cache_export_2026-07-30.csv")
        )
        val report = CommentaryCacheHealth.analyse(rows)

        // 18.5 is the ball number from the old system prompt's own example. Its share is the single
        // clearest signal that ball numbers are being invented rather than parsed.
        assertEquals("18.5", report.topBallNumber)
        assertTrue(
            "expected 18.5 to dominate, was ${report.topBallNumberShare}",
            report.topBallNumberShare > 0.5
        )
    }

    // --- healthy sample ------------------------------------------------------

    @Test
    fun `a healthy sample passes every check`() {
        val report = CommentaryCacheHealth.analyse(healthySample())
        assertTrue("expected no violations, got ${report.violations}", report.healthy)
    }

    @Test
    fun `a repeated ball number fails once the sample is large enough to judge`() {
        // Distinct text on every row, so only the ball number is wrong — this isolates the share
        // check from the duplicate checks.
        val poisoned = (1..60).map { i -> "18.5: delivery number $i, a distinct description." }
        val report = CommentaryCacheHealth.analyse(poisoned)
        assertTrue(
            "expected single-ball-number-share to fire, got ${report.violations}",
            report.violations.any { it.rule == "single-ball-number-share" }
        )
        assertTrue(
            "expected ball-number-spread to fire, got ${report.violations}",
            report.violations.any { it.rule == "ball-number-spread" }
        )
    }

    @Test
    fun `the distribution checks are skipped below the minimum sample size`() {
        // Guards the guard: a sample too small to judge must not be reported as healthy-by-default
        // in a way that hides the check being disabled outright.
        val tooSmall = (1..10).map { i -> "18.5: delivery number $i, a distinct description." }
        val report = CommentaryCacheHealth.analyse(tooSmall)
        assertTrue(
            "distribution checks must not fire below the minimum sample, got ${report.violations}",
            report.violations.none { it.rule == "single-ball-number-share" || it.rule == "ball-number-spread" }
        )
        assertEquals(
            "the underlying measurement is still reported",
            "18.5",
            report.topBallNumber
        )
    }

    @Test
    fun `a cached refusal is enough to fail a healthy sample`() {
        val poisoned = healthySample() +
            "Since there is no specific ball number provided, I'll write a general line."
        val report = CommentaryCacheHealth.analyse(poisoned)
        assertTrue(
            "expected meta-responses to fire, got ${report.violations}",
            report.violations.any { it.rule == "meta-responses" }
        )
    }

    @Test
    fun `a verbatim scraper line is enough to fail a healthy sample`() {
        val poisoned = healthySample() + "Mosaddek Hossain to Ben Curran, no run, flatter one outside off"
        val report = CommentaryCacheHealth.analyse(poisoned)
        assertTrue(
            "expected raw-scraper-lines to fire, got ${report.violations}",
            report.violations.any { it.rule == "raw-scraper-lines" }
        )
    }

    // --- ad-hoc scan of a fresh export ---------------------------------------

    @Test
    fun `an export passed via -Dcommentary_cache_csv is healthy`() {
        val path = System.getProperty("commentary.cache.csv")
        assumeTrue("no -Dcommentary.cache.csv supplied", path != null)

        val file = File(path!!)
        assertTrue("no such export: $path", file.isFile)

        val report = CommentaryCacheHealth.analyse(
            CommentaryCacheHealth.commentaryColumnOf(file.readText())
        )
        println(
            "rows=${report.rows} prefixed=${report.prefixedRows} " +
                "top=${report.topBallNumber}@${report.topBallNumberShare} " +
                "overs=${report.distinctOvers} exactDup=${report.exactDuplicateRate} " +
                "nearDup=${report.nearDuplicateRate} meta=${report.metaRows} raw=${report.rawScraperRows}"
        )
        assertTrue(
            "export is unhealthy:\n" + report.violations.joinToString("\n") { "  ${it.rule}: ${it.detail}" },
            report.healthy
        )
    }

    /**
     * Distinct deliveries across two overs and both formats, each rewritten rather than echoed.
     * Deliberately varied — near-duplicate detection is a real check and boilerplate would trip it.
     */
    private fun healthySample(): List<String> = listOf(
        "4.1: Douthwaite angles it across and Rhodes leaves it well alone outside off.",
        "4.2: Full and straight, jammed back down the ground for a single.",
        "4.3: Short of a length, Rhodes rocks back and cuts hard to deep point.",
        "4.4: Beaten! Seam movement away from the bat, and the keeper takes it cleanly.",
        "4.5: Driven on the up through extra cover, races away for four.",
        "4.6: A yorker to finish, dug out towards mid-on for no run.",
        "5.1: Hetmyer clears the front leg and heaves it over midwicket for six.",
        "5.2: Slower ball, well disguised, and the batter is through the shot early.",
        "5.3: Tucked off the pads down to fine leg, easy two.",
        "5.4: Wicket! Castled by a beauty that nips back through the gate.",
        "Ball 71: Jansen bangs it in short and Banton swivels it away for four.",
        "Ball 72: Full outside off, driven crisply but straight to the sweeper.",
        "Ball 73: Bouncer well outside off, called a wide by the square-leg umpire.",
        "Ball 74: Banton dances down the track and lofts it into the sightscreen.",
        "Ball 75: Fired in at the toes, squeezed out to backward point for one.",
        "Drinks are taken with the chase nicely poised.",
    )
}
