package com.tanush.bouncer.data.history

import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal
import org.junit.Assert.assertEquals
import org.junit.Test

class CricsheetResultTextTest {

    private fun match(outcome: HistoryOutcome, innings: List<HistoryInnings> = emptyList()) =
        HistoryMatch(v = 1, id = "1", t = listOf("India", "Australia"), o = outcome, inn = innings)

    private fun assertResult(expected: String, m: HistoryMatch) {
        val text = resultText(m)
        assertEquals(expected, text)
        assertEquals("'$text' must read as over", StatusSignal.TERMINAL, MatchStatusClassifier.classify(text))
    }

    @Test fun `won by runs`() =
        assertResult("Australia won by 45 runs", match(HistoryOutcome(w = 1, by = mapOf("runs" to 45))))

    @Test fun `won by wickets`() =
        assertResult("India won by 6 wickets", match(HistoryOutcome(w = 0, by = mapOf("wickets" to 6))))

    @Test fun `singular margins`() {
        assertResult("India won by 1 wicket", match(HistoryOutcome(w = 0, by = mapOf("wickets" to 1))))
        assertResult("India won by 1 run", match(HistoryOutcome(w = 0, by = mapOf("runs" to 1))))
    }

    @Test fun `won by an innings and runs`() =
        assertResult(
            "India won by an innings and 112 runs",
            match(HistoryOutcome(w = 0, by = mapOf("innings" to 1, "runs" to 112))),
        )

    @Test fun `a winner with no margin`() =
        assertResult("Australia won the match", match(HistoryOutcome(w = 1)))

    @Test fun `duckworth lewis marks the method`() =
        assertResult(
            "India won by 5 runs (DLS)",
            match(HistoryOutcome(w = 0, by = mapOf("runs" to 5), m = "D/L")),
        )

    @Test fun `another method is named as given`() =
        assertResult("India won by 5 runs (VJD)", match(HistoryOutcome(w = 0, by = mapOf("runs" to 5), m = "VJD")))

    @Test fun `tie`() = assertResult("Match tied", match(HistoryOutcome(r = "tie")))

    @Test fun `a tie settled by a super over names the winner`() =
        assertResult(
            "Match tied (Australia won the super over)",
            match(HistoryOutcome(r = "tie", el = 1), listOf(HistoryInnings(t = 0, so = 1))),
        )

    @Test fun `draw`() = assertResult("Match drawn", match(HistoryOutcome(r = "draw")))

    @Test fun `no result`() = assertResult("No result", match(HistoryOutcome(r = "no result")))

    @Test fun `an outcome the file does not explain`() = assertResult("Match complete", match(HistoryOutcome()))
}
