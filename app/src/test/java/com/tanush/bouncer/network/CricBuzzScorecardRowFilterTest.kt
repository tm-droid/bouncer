package com.tanush.bouncer.network

import java.io.File
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player's name comes from the row's name span, not from its first link.
 *
 * On Cricbuzz's current scorecard markup the only `<a>` elements inside a `scorecard-*-grid` row
 * are a hidden per-player menu: "View match performance", then "View profile" pointing at
 * `/profiles/{id}/{slug}`. `row.select("a").first()` therefore named EVERY batter and EVERY bowler
 * "View match performance" — the names that reached Appwrite, and then the Scorecard tab, where
 * two LazyColumn items with the same key is an `IllegalArgumentException`, not a rendering glitch:
 * the app closed on the tab (Sentry ANDROID-40).
 *
 * The fixture is real captured markup rather than a hand-written approximation, because a
 * hand-written one is what let this survive: the old tests used `<a href="#">S Tendulkar</a>`,
 * a shape Cricbuzz has not served for some time.
 */
class CricBuzzScorecardRowFilterTest {

    private val content by lazy {
        Jsoup.parse(fixture().readText(Charsets.UTF_8)).selectFirst("#innings_1")
            ?: error("fixture lost its #innings_1 container")
    }

    @Test
    fun `batsmen are named from the name span, not the menu link`() {
        val batsmen = parseScorecardBatsmen(content)
        assertEquals(listOf("Yashasvi Jaiswal", "KL Rahul"), batsmen.map { it.name })
        assertTrue(
            "a menu label leaked into a batter name",
            batsmen.none { it.name.contains("View ") },
        )
        assertTrue("profile link not captured", batsmen.first().profileUrl.contains("/profiles/"))
    }

    @Test
    fun `bowlers are named from the name span, not the menu link`() {
        val bowlers = parseScorecardBowlers(content)
        assertTrue("no bowlers parsed", bowlers.isNotEmpty())
        assertTrue(
            "a menu label leaked into a bowler name",
            bowlers.none { it.name.contains("View ") },
        )
        assertTrue("profile link not captured", bowlers.first().profileUrl.contains("/profiles/"))
    }

    @Test
    fun `the fixture still contains the menu links this test exists to reject`() {
        // Guards the guard: trimmed down to markup without the menu, the assertions above would
        // pass while proving nothing.
        assertTrue(
            "fixture no longer contains the per-player menu",
            content.select("a").any { it.text().trim() == "View match performance" },
        )
    }

    @Test
    fun `the Node parser reads the name the same way`() {
        // match-sync writes the scorecard rows every device reads, so a fix that lives only on the
        // device leaves "View match performance" in Appwrite for every other consumer. The Gradle
        // input for this file is declared in app/build.gradle.kts, without which editing
        // cricbuzz.js leaves testDebugUnitTest UP-TO-DATE and this check never runs.
        val js = locate("appwrite/functions/match-sync/src/cricbuzz.js").readText(Charsets.UTF_8)
        assertTrue("cricbuzz.js appears empty", js.length > 1000)
        assertTrue(
            "cricbuzz.js no longer reads the player name from the name span — it must stay in " +
                "step with CricBuzzParser.playerNameOf. Do not delete this check.",
            js.contains("function playerNameOf") && js.contains("hover:underline"),
        )
        assertEquals(
            "playerNameOf is not applied to both the batting and the bowling grid in cricbuzz.js",
            2,
            Regex("const name = playerNameOf\\(\\\$, \\\$row\\);").findAll(js).count(),
        )
    }

    private fun fixture(): File = locate("app/src/test/resources/cricbuzz_scorecard_grid.html")

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
