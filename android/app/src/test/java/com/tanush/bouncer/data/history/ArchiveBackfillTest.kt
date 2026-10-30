package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.SearchRepository.TeamEntry
import com.tanush.bouncer.data.remote.cricsheetH2H
import com.tanush.bouncer.models.TimelineEventType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A match written by the Cricbuzz backfill (`archive-data.md`): scorecard, info, and where the crawler got it, balls. */
class ArchiveBackfillTest {
    private fun fixture(): ArchivedMatch = ArchiveClient.decode(
        "17685",
        checkNotNull(javaClass.classLoader?.getResourceAsStream("archive_backfill_17685.json")).readBytes(),
    )!!

    @Test
    fun `a backfilled file has a card and no commentary rows`() {
        val m = fixture()
        assertTrue(m.commentary().isEmpty())
        assertEquals("backfill", m.source?.substringAfter("cricbuzz-"))
        assertEquals(2, m.scorecard()?.innings?.size)
    }

    @Test
    fun `deliveries become commentary lines and a partial innings has no overs`() {
        val d = fixture().deliveries()!!
        assertEquals(listOf(1, 2), d.partial)
        assertEquals(listOf("United Arab Emirates", "Afghanistan"), d.match.t)
        // Afghanistan batted first in this match: the card's "Afg" resolves to the second listed side.
        assertEquals(1, d.match.inn[0].t)
        val lines = HistoryCommentary.entries(d.match)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.event == TimelineEventType.WICKET })
        assertTrue(scorecardOf(d.match).overs.isEmpty())
    }

    @Test
    fun `a complete innings has per-over points, a single wicket cell is read, retirements are not wickets`() {
        val json = """
            {"v":1,"id":"1","meta":{"teams":[{"teamId":"1","name":"Afghanistan"},{"teamId":"2","name":"Ireland"}]},
             "scorecard":{"innings":[{"teamName":"IRE","score":"7-1","overs":"2 Ov","batsmen":[],"bowlers":[],"fallOfWickets":[],"extras":"1","total":"7"}]},
             "balls":{"v":1,"p":[["A","1"],["B","2"],["C","3"]],
              "inn":[{"i":1,"b":[[0,1,0,1,2,4,0,0],[0,2,0,1,2,0,1,1],[1,1,0,1,2,0,0,0,["caught",0]],[1,2,2,1,0,2,0,0,["retired hurt",2]]]}]}}
        """.trimIndent()
        val d = ArchiveClient.decode("1", json.toByteArray())!!.deliveries()!!
        assertTrue(d.partial.isEmpty())
        assertEquals(1, d.match.inn[0].t)
        assertEquals(listOf(listOf(5, 0), listOf(2, 1)), d.match.inn[0].ov)
        val overs = scorecardOf(d.match).overs
        assertEquals(listOf(5, 7), overs.map { it.score })
        assertEquals(listOf(0, 1), overs.map { it.wickets })
    }

    @Test
    fun `info reads toss, officials, player of the match and squads under the side names`() {
        val json = """
            {"v":1,"id":"2","meta":{"teams":[{"teamId":"3","name":"Pakistan"},{"teamId":"96","name":"Afghanistan"}]},
             "info":{"toss":{"winnerId":"96","winnerName":"Afghanistan","decision":"Bowling"},
              "umpires":[{"id":"1","name":"U One"},{"id":"2","name":"U Two"}],"thirdUmpire":null,"referee":{"id":"3","name":"R Ref"},
              "playersOfTheMatch":[{"id":"9","name":"Shadab Khan"},{"id":"10","name":""}],
              "squads":{"3":[],"96":[{"id":"5","name":"Rashid Khan"}]}}}
        """.trimIndent()
        val info = ArchiveClient.decode("2", json.toByteArray())!!.archivedInfo()!!
        assertEquals("Afghanistan", info.tossWinner)
        assertEquals("bowl", info.tossDecision)
        assertEquals(listOf("U One", "U Two"), info.umpires)
        assertEquals("R Ref", info.referee)
        assertEquals(listOf("Shadab Khan"), info.playersOfTheMatch)
        assertEquals(listOf("Afghanistan" to listOf("Rashid Khan")), info.squads)
        assertNull(fixture().archivedInfo()?.tossWinner)
        assertNotNull(fixture().archivedInfo()?.playersOfTheMatch?.firstOrNull())
    }

    @Test
    fun `a card's short side name resolves to the listed side`() {
        val sides = listOf("United Arab Emirates", "Afghanistan")
        assertEquals("Afghanistan", fullName("Afg", sides))
        assertEquals("United Arab Emirates", fullName("UAE", sides))
        assertEquals("Afghanistan", fullName("AFG 1st Innings", sides))
        assertEquals("XYZ", fullName("XYZ", sides))
    }

    private val teams = listOf(TeamEntry("Afghanistan", "AFG"), TeamEntry("Ireland", "IRE"), TeamEntry("India", "IND"))

    private fun archiveRow(id: String, a: String, b: String) = PastMatchRow.ofArchive(
        JsonArray(
            listOf(id, "2019-03-10", "ODI", "male", a, b, "$a won by 5 wkts", "Ireland v Afghanistan", "Dehradun")
                .map(::JsonPrimitive),
        ),
    )!!

    @Test
    fun `search finds backfilled Afghanistan matches by pair, by code and by name`() {
        val index = PastMatchIndex.build(
            emptyList(),
            listOf(archiveRow("11377", "IRELAND", "AFGHANISTAN"), archiveRow("16465", "Afghanistan", "Ireland")),
        )
        val ids = { q: String -> index.search(q, emptyList(), teams).map { it.row.id }.toSet() }
        assertEquals(setOf("11377", "16465"), ids("afg v ire"))
        assertEquals(setOf("11377", "16465"), ids("afghanistan"))
        assertTrue(ids("ind v afg").isEmpty())
        assertFalse(index.search("afg v ire", emptyList(), teams).any { it.row.women })
    }

    @Test
    fun `an archive record file reads like a Cricsheet one and is keyed by the side's slug`() {
        assertEquals("afghanistan", archiveKey("Afghanistan"))
        assertEquals("united-arab-emirates", archiveKey("United Arab Emirates"))
        val file = """{"t":["Afghanistan","Ireland"],"g":"male","f":{"ODI":[3,2,1,0,0,0]},"m":[["2","2019-03-10","ODI",0,null]]}"""
        val h2h = cricsheetH2H(file, "Ireland", "Afghanistan", "ODI")!!
        assertEquals(3, h2h.stats.totalMatches)
        assertEquals(1, h2h.stats.team1Wins)
        assertEquals(2, h2h.stats.team2Wins)
        val team = HistoryRecords.parseTeam("""{"f":{"ODI":[3,2,1,0,0,0]}}""")!!.map { it.copy(fromArchive = true) }
        assertTrue(team.single().fromArchive)
    }
}
