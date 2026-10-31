package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.viewmodels.MatchDetailViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class SharedMatchDetailTest {
    private fun fixture(): SharedMatchDetail {
        val roots = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        val file = roots.map { File(it, "appwrite/functions/api/test/fixtures/match-detail.json") }
            .first { it.isFile }
        return AppJson.decodeFromString(file.readText())
    }

    @Test
    fun `canonical partial fixture maps available detail and attributed raw commentary`() {
        val data = fixture()
        assertEquals("12345", data.matchId)
        assertEquals("140/3 (18)", data.detailSnapshot()?.payload?.score)
        assertEquals(1793404800000L, data.detailSnapshot()?.updatedAtMs)
        assertNull(data.availableScorecard())
        assertEquals(listOf("18.1: Bowler to Batter, 1 run"), data.rawCommentary())
        assertFalse(data.detailSnapshot()!!.confirmedTerminal)
    }

    @Test
    fun `section availability and freshness outrank envelope and payload presence`() {
        val data = fixture()
        assertNull(data.copy(availability = data.availability +
            ("detail" to DetailAvailability(false, 123, false))).detailSnapshot())
        val stale = data.copy(availability = data.availability +
            ("detail" to DetailAvailability(true, 123, true))).detailSnapshot()!!
        assertEquals(123L, stale.updatedAtMs)
        assertFalse(stale.isFresh(124))
        assertTrue(data.copy(commentary = listOf(RawDetailCommentary("AI", "cricbuzz", true))).rawCommentary().isEmpty())
    }

    @Test
    fun `completed correction requires fresh terminal payload and preserves legacy result protection`() {
        val data = fixture().copy(state = "Complete")
        assertFalse(data.detailSnapshot()!!.confirmedTerminal)
        val corrected = data.copy(detail = data.detail!!.copy(status = "India won by 7 wickets", team1Score = "161/3"))
        assertTrue(corrected.detailSnapshot()!!.confirmedTerminal)
        val initial = MatchDetailViewModel.UiState(liveTeam1Score = "160/3", liveStatus = "India won")
        assertEquals("160/3", initial.merge(corrected.detail!!, true).liveTeam1Score)
        assertEquals("161/3", initial.merge(corrected.detail!!, true, true).liveTeam1Score)
        val stale = corrected.copy(availability = corrected.availability +
            ("detail" to DetailAvailability(true, 123, true)))
        assertFalse(stale.detailSnapshot()!!.confirmedTerminal)
    }

    @Test
    fun `fresh cache and concurrent callers share a fetch while stale sections force refresh`() = runTest {
        var calls = 0
        val now = 1793404800001L
        val repository = SharedDetailRepository(fetch = { calls++; delay(20); fixture() }, now = { now })
        val first = async { repository.get("12345") }
        val second = async { repository.get("12345") }
        assertEquals(first.await(), second.await())
        repository.get("12345")
        assertEquals(1, calls)
        val staleRepository = SharedDetailRepository(fetch = {
            calls++
            fixture().copy(availability = mapOf("detail" to DetailAvailability(true, now, true)))
        }, now = { now })
        staleRepository.get("12345")
        staleRepository.get("12345")
        assertEquals(3, calls)
    }

    @Test
    fun `HTTP membership errors and timeouts propagate so archive fallback can report failure`() = runTest {
        for (failure in listOf(IOException("503 membership unavailable"), IOException("404 unknown"),
            java.net.SocketTimeoutException("20 second deadline"))) {
            val repository = SharedDetailRepository(fetch = { throw failure })
            try {
                repository.get("12345")
                fail("Expected failure")
            } catch (error: IOException) {
                assertEquals(failure.javaClass, error.javaClass)
                assertEquals(failure.message, error.message)
            }
        }
    }

    @Test
    fun `raw commentary keeps canonical metric object including Hundred labels without duplicate prefixes`() {
        val data = fixture()
        assertEquals("18.1", data.commentary!!.first().ballMetric!!.label)
        val lines = data.copy(commentary = listOf(
            RawDetailCommentary("Bowler to Batter, no run", "cricbuzz", false, RawBallMetric("Ball 25", null, 25)),
            RawDetailCommentary("18.1: Already labelled", "cricbuzz", false, RawBallMetric("18.1", 18, 1)),
            RawDetailCommentary("Session note", "cricbuzz", false, null),
        )).rawCommentary()
        assertEquals(listOf("Ball 25: Bowler to Batter, no run", "18.1: Already labelled", "Session note"), lines)
    }

    @Test
    fun `break states refresh at live TTL so resumed play is not held for ten minutes`() = runTest {
        for (state in listOf("Innings Break", "Stumps", "Rain", "Tea", "Toss", "Unknown")) {
            var time = 1793404800001L
            var calls = 0
            val repo = SharedDetailRepository(fetch = { calls++; fixture().copy(state = state) }, now = { time })
            repo.get("12345")
            time += 59_000
            repo.get("12345")
            assertEquals(1, calls)
            time += 2_000
            repo.get("12345")
            assertEquals(2, calls)
        }
    }

}
