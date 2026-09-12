package com.tanush.bouncer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the crest coming off the match payload rather than the bundled map.
 *
 * `matchInfo.team1/2` has always carried `imageId` next to `teamId` and `teamName`, and the parser
 * read the name and dropped the rest. Every crest therefore came from `assets/TeamLogos.json`, a
 * snapshot that cannot hold a team added since it was generated and points at a stale image for any
 * team whose id changed — 89 teams and 87 stale ids as measured when this was written, all of them
 * rendering nothing while Cricbuzz rendered a crest for the same fixture.
 *
 * The map is empty in a unit test (no assets on the classpath), which is what makes this test sharp:
 * any URL here can only have come from the payload.
 */
class CricBuzzTeamImageTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private val matches by lazy {
        CricBuzzScraper().parseMatchListHtml(fixture("cricbuzz_match_list_team_images.html"))
    }

    @Test
    fun `the list parses`() {
        assertTrue("no matches parsed from the captured list page", matches.isNotEmpty())
    }

    @Test
    fun `both sides carry the crest their payload names`() {
        val match = checkNotNull(matches.firstOrNull { it.id == "154649" }) {
            "match 154649 missing from the captured list page"
        }
        val (first, second) = match.teams[0] to match.teams[1]

        assertEquals("Trinbago Knight Riders", first.name)
        assertEquals("271", first.teamId)
        assertEquals("https://static.cricbuzz.com/a/img/v1/720x540/i1/c825110/i.jpg", first.logoUrl)

        assertEquals("Guyana Amazon Warriors", second.name)
        assertEquals("159", second.teamId)
        assertEquals("https://static.cricbuzz.com/a/img/v1/720x540/i1/c825126/i.jpg", second.logoUrl)
    }

    @Test
    fun `every parsed side has a crest, which the bundled map could not have supplied`() {
        val sides = matches.flatMap { it.teams }
        assertTrue(sides.size >= 2)
        val withoutCrest = sides.filter { it.logoUrl.isNullOrBlank() }
        assertEquals("sides with no crest: ${withoutCrest.map { it.name }}", emptyList<Any>(), withoutCrest)
    }

    @Test
    fun `a payload with no image id falls back rather than building a broken url`() {
        // The map is empty here, so the fallback is null — never a URL with an empty id in it.
        val fallback = cricketTeam("Nowhere XI", "0", "1/0", "0.1", false, imageId = "")
        assertEquals(null, fallback.logoUrl)
    }
}
