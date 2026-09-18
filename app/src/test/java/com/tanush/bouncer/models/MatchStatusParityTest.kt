package com.tanush.bouncer.models

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device and `bouncer-match-sync` must agree on what "the match is over" means.
 *
 * Both sides answer it, for the same reason and from the same free English prose: the server so it
 * can write a match's final `match-live-detail` and `match-scorecard` rows before the match leaves
 * `live-matches.json` for good, the device so it stops applying that row once the match is done.
 * If the server's list is the narrower one a result slips through and the row freezes mid-innings
 * again — which is the defect both halves exist to fix.
 *
 * So this lifts `TERMINAL_STATUS_PATTERNS` out of `cricbuzz.js`, compiles them here, and asserts
 * they classify the same fixtures `MatchStatusClassifier` does. `cricbuzz.js` is a declared Gradle
 * test input (`app/build.gradle.kts`), so editing one side alone re-runs this and fails the build.
 * A mirrored rule that nothing compares is not a pinned rule.
 */
class MatchStatusParityTest {

    /** Walks up from the test's working directory; the module dir is not the repo root. */
    private fun locate(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("could not find $relativePath from ${System.getProperty("user.dir")}")
    }

    private val cricbuzzJs: File get() = locate("appwrite/functions/match-sync/src/cricbuzz.js")

    /**
     * `poll-live-matches` classifies too, and over all three states rather than just the terminal
     * one: a Cricbuzz list page carries the site-wide ticker as well as its own tab's fixtures, so
     * it has to decide which tab each match belongs on exactly as the device does. A narrower list
     * there puts a finished match on Live or drops a fixture from Upcoming outright.
     */
    private val matchStateJs: File get() = locate("appwrite/functions/poll-live-matches/src/matchstate.js")

    /** The escaped pattern strings of a `const NAME = [...]` array in a JS file. */
    private fun patternsIn(file: File, name: String): List<String> {
        val source = file.readText()
        val block = Regex("""const $name = \[(.*?)];""", RegexOption.DOT_MATCHES_ALL)
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("$name not found in ${file.path}")
        return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(block)
            .map { it.groupValues[1].replace("\\\\", "\\") }
            .toList()
    }

    @Test
    fun `all three pattern lists are identical in Kotlin and poll-live-matches`() {
        // Element for element, not by count and not by a fixture set: either of those lets one side
        // gain or lose a rule silently, which is the failure this whole file exists to prevent.
        assertEquals(
            "TERMINAL drifted between MatchStatusClassifier and matchstate.js",
            MatchStatusClassifier.TERMINAL.map { it.pattern },
            patternsIn(matchStateJs, "TERMINAL_PATTERNS"),
        )
        assertEquals(
            "IN_PROGRESS drifted between MatchStatusClassifier and matchstate.js",
            MatchStatusClassifier.IN_PROGRESS.map { it.pattern },
            patternsIn(matchStateJs, "IN_PROGRESS_PATTERNS"),
        )
        assertEquals(
            "PRE_MATCH drifted between MatchStatusClassifier and matchstate.js",
            MatchStatusClassifier.PRE_MATCH.map { it.pattern },
            patternsIn(matchStateJs, "PRE_MATCH_PATTERNS"),
        )
    }

    @Test
    fun `the two server copies of the terminal list agree with each other`() {
        // match-sync decides when to write a match's final rows; poll-live-matches decides whether
        // that match is still on the live list at all. A disagreement between them is a match that
        // one half has finished and the other has not.
        assertEquals(
            "TERMINAL drifted between cricbuzz.js and matchstate.js",
            patternsIn(cricbuzzJs, "TERMINAL_STATUS_PATTERNS"),
            patternsIn(matchStateJs, "TERMINAL_PATTERNS"),
        )
    }

    /** The escaped pattern strings between `TERMINAL_STATUS_PATTERNS = [` and its closing `]`. */
    private fun serverPatterns(): List<Regex> {
        val source = cricbuzzJs.readText()
        val block = Regex("""const TERMINAL_STATUS_PATTERNS = \[(.*?)];""", RegexOption.DOT_MATCHES_ALL)
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("TERMINAL_STATUS_PATTERNS not found in ${cricbuzzJs.path}")
        return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(block)
            .map { it.groupValues[1].replace("\\\\", "\\") }
            .map { it.toRegex(RegexOption.IGNORE_CASE) }
            .toList()
    }

    private fun serverSaysTerminal(status: String, patterns: List<Regex>) =
        patterns.any { it.containsMatchIn(status) }

    private val terminal = listOf(
        "Southern Brave won by 39 runs",
        "India won by 5 wkts",
        "Australia won the match by an innings and 12 runs",
        "India beat Australia by 5 wickets",
        "Match drawn",
        "Match tied",
        "Match abandoned due to rain",
        "No result",
        "Complete",
        // Precedence, not position: a result string routinely quotes in-progress prose. This one
        // classified Live for a whole release because "innings" matched before "drawn".
        "Match drawn, England 2nd innings 340/6",
    )

    private val notTerminal = listOf(
        // A bare "won" is the START of a match. This classified Completed for the same reason.
        "India won the toss and elected to bat",
        "India needed 12 more runs",
        "Birmingham Phoenix need 96 runs in 44 balls",
        "Day 3: Stumps",
        "Day 2: Tea - England lead by 143 runs",
        "Innings Break",
        "Rain delay",
        "Bad light stopped play",
        "Live",
        "Match starts at 19:30 local time",
        // Word boundaries: "tea" inside "team", "draw" inside "drawing".
        "Both teams are on the field",
        "Kohli is drawing the field in",
    )

    @Test
    fun `the two pattern lists are identical`() {
        // Pattern for pattern, not a count and not a fixture set: either of those lets one side
        // gain a rule the other never hears about, which is precisely how a status the server calls
        // live and the device calls finished would reach production unnoticed.
        assertTrue("cricbuzz.js appears empty", cricbuzzJs.readText().length > 1000)
        assertEquals(
            MatchStatusClassifier.TERMINAL.map { it.pattern },
            serverPatterns().map { it.pattern },
        )
    }

    @Test
    fun `both sides call the same statuses terminal`() {
        val patterns = serverPatterns()
        for (status in terminal) {
            assertEquals(
                "device disagrees on: $status",
                StatusSignal.TERMINAL,
                MatchStatusClassifier.classify(status),
            )
            assertTrue("server disagrees on: $status", serverSaysTerminal(status, patterns))
        }
    }

    @Test
    fun `both sides refuse the same statuses`() {
        val patterns = serverPatterns()
        for (status in notTerminal) {
            assertTrue(
                "device calls this terminal: $status",
                MatchStatusClassifier.classify(status) != StatusSignal.TERMINAL,
            )
            assertTrue("server calls this terminal: $status", !serverSaysTerminal(status, patterns))
        }
    }
}
