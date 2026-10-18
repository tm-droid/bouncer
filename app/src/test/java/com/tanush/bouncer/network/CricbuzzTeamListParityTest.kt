package com.tanush.bouncer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins `tools/update_logos.py` against the Kotlin crest builder.
 *
 * The script writes `assets/TeamLogos.json` and the app reads it, so the two sides must agree on
 * the URL shape. They are in different languages and nothing links them at build time, which is
 * why the script is declared as a test input in app/build.gradle.kts — undeclared, editing it
 * leaves this task UP-TO-DATE and the check never runs.
 *
 * The guards are pinned too, because the failure this replaces was silence: the old script read
 * four committed dumps, fetched nothing, and reported "No changes to commit" for five months while
 * Cricbuzz added 89 teams and changed 87 image ids.
 */
class CricbuzzTeamListParityTest {

    private val script: String by lazy { repoFile("tools/update_logos.py").readText() }

    /** Walks up from the test's working directory, which is the module dir, not the repo root. */
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("Could not locate $relative from ${File("").absolutePath}")
    }

    private fun constant(name: String): String? =
        Regex("""^$name\s*=\s*"([^"]*)"""", RegexOption.MULTILINE).find(script)?.groupValues?.get(1)

    @Test
    fun `the python url template renders what cricbuzzTeamLogoUrl builds`() {
        val template = checkNotNull(constant("LOGO_URL")) { "LOGO_URL not found in the script" }
        val rendered = template.replace("{image_id}", "776162")
        assertEquals(cricbuzzTeamLogoUrl("776162"), rendered)
    }

    @Test
    fun `the script fetches the four team pages rather than reading dumps`() {
        val pages = Regex("""https://www\.cricbuzz\.com/cricket-team[a-z/]*""").findAll(script)
            .map { it.value }.toSet()
        assertEquals(
            setOf(
                "https://www.cricbuzz.com/cricket-team",
                "https://www.cricbuzz.com/cricket-team/domestic",
                "https://www.cricbuzz.com/cricket-team/league",
                "https://www.cricbuzz.com/cricket-team/women",
            ),
            pages,
        )
        // The retired RapidAPI dumps are named in the script's own history note, so check for the
        // filenames it would actually open rather than the glob that note spells out.
        listOf("international_teams.json", "league_teams.json", "domestic_teams.json", "women_teams.json")
            .forEach { assertTrue("the script must not read $it", !script.contains(it)) }
    }

    @Test
    fun `a truncated corpus is refused rather than written`() {
        val floor = checkNotNull(
            Regex("""MIN_EXPECTED_TEAMS\s*=\s*(\d+)""").find(script)?.groupValues?.get(1)?.toInt()
        ) { "MIN_EXPECTED_TEAMS not found in the script" }
        assertTrue("the floor must be a real guard, not zero", floor >= 500)
        assertTrue("a short corpus must exit non-zero", script.contains("sys.exit(1)"))
    }

    @Test
    fun `both branding assets are written from the one pass`() {
        assertTrue(script.contains("app/src/main/assets/TeamLogos.json"))
        assertTrue(script.contains("app/src/main/assets/TeamAcronyms.json"))
    }

    @Test
    fun `the script drops the placeholder the app treats as no crest`() {
        val ids = checkNotNull(Regex("""PLACEHOLDER_IMAGE_IDS\s*=\s*\{([^}]*)\}""").find(script)) {
            "PLACEHOLDER_IMAGE_IDS not found in the script"
        }.groupValues[1]
        assertTrue(ids.contains("\"$CRICBUZZ_PLACEHOLDER_IMAGE_ID\""))
    }

    @Test
    fun `the placeholder url is recognised and a real crest is not`() {
        assertTrue(isCricbuzzPlaceholderLogo(cricbuzzTeamLogoUrl(CRICBUZZ_PLACEHOLDER_IMAGE_ID)))
        assertTrue(!isCricbuzzPlaceholderLogo(cricbuzzTeamLogoUrl("776162")))
        assertTrue(!isCricbuzzPlaceholderLogo("https://r2.thesportsdb.com/images/media/team/badge/c174284/x.png"))
    }
}
