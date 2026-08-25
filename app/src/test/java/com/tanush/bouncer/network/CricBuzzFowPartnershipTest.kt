package com.tanush.bouncer.network

import java.io.File
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fall of wickets and partnerships, read from the grid markup Cricbuzz actually serves.
 *
 * `7165b3c` fixed the per-player menu leaking into player names, but only for the batting and
 * bowling grids. A `scorecard-fow-grid` row has the same shape — the name span sits beside a hidden
 * menu ("View match performance", then "View profile") in one cell — so every dismissed batter was
 * still named "KL RahulView match performanceView profile". That reached `match-scorecard` and then
 * the app, which showed "KL RahulView match"; it also missed the `innings.batsmen` lookup in
 * `mapFallOfWickets`, which is where the "0 (0)" beside each name came from.
 *
 * Partnerships were a separate defect with the same blast radius: they render as
 * `div.scorecard-pship-grid`, not as fow-grid rows, so the section walk found none of them and the
 * Partnerships card never rendered on any device.
 *
 * The fixture is real captured markup. The hand-written approximation in `CricBuzzScraperTest` —
 * `<div>45-1 (D Warner, 8.2), …</div>` — is exactly why this survived: it kept passing against a
 * text blob Cricbuzz had already stopped serving.
 */
class CricBuzzFowPartnershipTest {

    private val content by lazy {
        Jsoup.parse(fixture().readText(Charsets.UTF_8)).selectFirst("#scard-team-2-innings-1")
            ?: error("fixture lost its innings container")
    }

    @Test
    fun `dismissed batters are named from the name span, not the menu`() {
        val fows = parseFallOfWickets(content)
        assertEquals(
            listOf("KL Rahul", "Yashasvi Jaiswal", "Shubman Gill"),
            fows.map { it.batsman },
        )
        assertTrue("a menu label leaked into a FOW name", fows.none { it.batsman.contains("View ") })
    }

    @Test
    fun `score is the runs half alone and the wicket number comes from the markup`() {
        // "37-1" is one cell. Keeping it whole is what made mapFallOfWickets' toIntOrNull yield 0,
        // and it disagrees with the split form match-sync writes to match-scorecard.
        val first = parseFallOfWickets(content).first()
        assertEquals("37", first.score)
        assertEquals(1, first.number)
        assertEquals("10.4", first.overs)
        assertEquals(listOf(1, 2, 3), parseFallOfWickets(content).map { it.number })
    }

    @Test
    fun `the section walk stops at the next header`() {
        // The fixture carries the Partnerships header row after the wickets. Walking past it would
        // add a headerless row to the wicket list.
        assertEquals(3, parseFallOfWickets(content).size)
    }

    @Test
    fun `partnerships parse from the pship grid, with names and figures separated`() {
        val partnerships = parsePartnerships(content)
        assertEquals(2, partnerships.size)
        val first = partnerships.first()
        assertEquals("Yashasvi Jaiswal", first.player1)
        assertEquals("Rahul", first.player2)
        assertEquals("37", first.runs)
        assertEquals("64", first.balls)
        assertEquals("19", first.contribution1)
        assertEquals("18", first.contribution2)
        assertTrue(
            "a menu label leaked into a partnership name",
            partnerships.none { it.player1.contains("View ") || it.player2.contains("View ") },
        )
    }

    @Test
    fun `every partnership figure is readable by the composable that renders it`() {
        // PremiumScorecardTab reads all four with toIntOrNull, for which "19(31)" is a silent 0.
        for (p in parsePartnerships(content)) {
            for ((field, value) in listOf(
                "runs" to p.runs,
                "balls" to p.balls,
                "contribution1" to p.contribution1,
                "contribution2" to p.contribution2,
            )) {
                assertTrue("$field is not a plain integer: '$value'", value.toIntOrNull() != null)
            }
        }
    }

    @Test
    fun `extras and total come off their bold labels`() {
        assertEquals("21 (b 8, lb 3, w 7, nb 3, p 0)", scorecardLabel("Extras"))
        assertEquals("503-9 d (138 Overs, RR: 3.64)", scorecardLabel("Total"))
    }

    @Test
    fun `the fixture still contains the menus these tests exist to reject`() {
        // Guards the guard: trimmed down to markup without the menu, the assertions above would
        // pass while proving nothing.
        for (selector in listOf("div.grid.scorecard-fow-grid", "div.grid.scorecard-pship-grid")) {
            assertTrue(
                "$selector rows no longer carry the per-player menu",
                content.select(selector).any { row ->
                    row.select("a").any { it.text().trim() == "View match performance" }
                },
            )
        }
    }

    @Test
    fun `the Node parser reads both sections the same way`() {
        // match-sync writes the scorecard rows every device reads, so a fix that lives only on the
        // device leaves the junk names in Appwrite for every other consumer. The Gradle input for
        // cricbuzz.js is declared in app/build.gradle.kts, without which editing it leaves
        // testDebugUnitTest UP-TO-DATE and this check never runs.
        val js = locate("appwrite/functions/match-sync/src/cricbuzz.js").readText(Charsets.UTF_8)
        assertTrue("cricbuzz.js appears empty", js.length > 1000)
        assertTrue(
            "cricbuzz.js no longer names the dismissed batter with playerNameOf — it must stay in " +
                "step with CricBuzzParser.parseFallOfWickets. Do not delete this check.",
            js.contains("batsman: playerNameOf("),
        )
        assertTrue(
            "cricbuzz.js no longer reads partnerships from div.scorecard-pship-grid. They are not " +
                "fall-of-wickets rows; walking the fow grid for them returns nothing at all, " +
                "which is what shipped. Do not delete this check.",
            js.contains("div.scorecard-pship-grid"),
        )
        assertEquals(
            "partnership players are not both read with playerNameOf in cricbuzz.js",
            2,
            Regex("player[12]: playerNameOf\\(").findAll(js).count(),
        )
    }

    private fun scorecardLabel(label: String): String =
        content.select("div.font-bold").first { it.text().trim() == label }
            .nextElementSibling()?.text()?.trim().orEmpty()

    private fun fixture(): File = locate("app/src/test/resources/cricbuzz_scorecard_fow_grid.html")

    private fun locate(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("could not find $relativePath from ${System.getProperty("user.dir")}")
    }
}
