package com.tanush.bouncer.data.history

import com.tanush.bouncer.R
import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.ui.components.v2.ScoreSentenceSpec
import com.tanush.bouncer.ui.components.v2.scoreSentenceSpec
import com.tanush.bouncer.ui.components.v2.splitInningsScore
import com.tanush.bouncer.ui.components.v2.spokenWickets
import com.tanush.bouncer.viewmodels.venueOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** Australia v India, Melbourne, 26 Dec 2011 (`m/518950.json`): four all-out innings, 0 wickets on no card. */
class CricsheetTestScoreTest {

    private val match: HistoryMatch = AppJson.decodeFromString(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("history_518950_trimmed.json")) { "missing fixture" }
            .bufferedReader().use { it.readText() },
    )

    @Test
    fun `a Test side's score joins its innings and drops the wickets of an all-out one`() {
        assertEquals("333 & 240" to "76.3", sideScoreOf(match, 0))
        assertEquals("282 & 169" to "47.5", sideScoreOf(match, 1))
    }

    @Test
    fun `an all-out Test innings is spoken as all out, not for 0`() {
        val current = splitInningsScore("333 & 240").last()
        assertEquals(10, spokenWickets(current, isTest = true))
        assertEquals(
            ScoreSentenceSpec(R.string.a11y_score_all_out, listOf("Australia", "240", "76.3")),
            scoreSentenceSpec("Australia", current.runs, spokenWickets(current, isTest = true), "76.3"),
        )
        assertEquals(6, spokenWickets(splitInningsScore("333 & 240/6").last(), isTest = true))
    }

    @Test
    fun `the file carries the ground, with no city`() {
        assertEquals("Melbourne Cricket Ground", venueOf(match))
        assertEquals("Wankhede Stadium, Mumbai", venueOf(match.copy(ven = "Wankhede Stadium", city = "Mumbai")))
        assertEquals("Eden Gardens, Kolkata", venueOf(match.copy(ven = "Eden Gardens, Kolkata", city = "Kolkata")))
    }
}
