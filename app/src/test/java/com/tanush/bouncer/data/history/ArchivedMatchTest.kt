package com.tanush.bouncer.data.history

import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivedMatchTest {

    // The shape `appwrite/tools/archive` writes: rows under the table's column names, in sort_key
    // order, plus the rest of the match as JSON. Two rows of 170697 (2026-10-13), a wicket added.
    private val json = """
        {"v":1,"id":"170697","final":true,"finalisedAt":"2026-10-15T00:08:49.903Z",
         "rows":[
          {"id":"e593","innings":1,"ball":"0.1","sort_key":1791896507007,"commentary":"0.1: Fernando to Farhan, no run.",
           "ai_generated":true,"event":"NONE","runs":0,"provider":"gemini-flash-lite-2","created_at":"2026-10-13T13:03:15.684Z"},
          {"id":"1228","innings":1,"ball":"0.2","sort_key":1791896574843,"commentary":"0.2: Fernando to Farhan, OUT, caught.",
           "ai_generated":true,"event":"WICKET","runs":0,"extra":"NOBALL","provider":"groq","created_at":"2026-10-13T13:04:15.329Z"},
          {"id":"blank","innings":1,"ball":"0.3","sort_key":1791896600000,"commentary":"  "}
         ],
         "meta":{"id":"170697","title":"Pakistan vs Sri Lanka"},
         "detail":{"status":"Sri Lanka won by 8 wkts"},
         "scorecard":{"innings":[{"teamName":"PAK 1st Innings","score":"168-8","overs":"20 Ov","batsmen":[],"bowlers":[],
           "fallOfWickets":[],"extras":"9","total":"168"}]},
         "context":null,
         "evalLog":{"version":1}}
    """.trimIndent()

    private val match = ArchiveClient.decode("170697", json.toByteArray())!!

    @Test
    fun `rows read as feed lines, newest first, with the table's verdict columns`() {
        val lines = match.commentary()
        assertEquals(listOf("0.2", "0.1"), lines.map { it.ball })
        assertEquals(TimelineEventType.WICKET, lines[0].event)
        assertEquals("NOBALL", lines[0].extra)
        assertEquals(0, lines[0].runs)
        assertEquals(1, lines[0].innings)
        assertEquals(1791896574843.0, lines[0].sortKey!!, 0.0)
        assertNull(lines[1].extra)
    }

    @Test
    fun `the final card decodes, an absent context is null, unknown keys are ignored`() {
        assertEquals("PAK 1st Innings", match.scorecard()?.innings?.single()?.teamName)
        assertNull(match.context())
        assertTrue(match.isFinal)
    }

    @Test
    fun `the win-probability log reads as oriented overs, and a thin or unoriented one as none`() {
        val log = ArchiveClient.decode(
            "1",
            """{"id":"1","evalLog":{"team1Id":"7","points":[
              {"innings":1,"over":1,"score":8,"wickets":0,"p":0.5},
              {"innings":1,"over":2,"score":15,"wickets":1,"p":0.45}]}}""".toByteArray(),
        )!!.evalOvers()!!
        assertEquals("7", log.probabilityTeamId)
        assertEquals(listOf(0.5, 0.45), log.overs.map { it.p })
        assertEquals(15, log.overs.last().score)
        assertNull(match.evalOvers()) // {"version":1}: no team, no points
        val one = """{"id":"1","evalLog":{"team1Id":"7","points":[{"innings":1,"over":1,"p":0.5}]}}"""
        assertNull(ArchiveClient.decode("1", one.toByteArray())!!.evalOvers())
    }

    @Test
    fun `an unreadable file is null, not a crash`() {
        assertNull(ArchiveClient.decode("1", "<html>404</html>".toByteArray()))
    }
}
