package com.tanush.bouncer.models

import com.tanush.bouncer.data.remote.MatchContextStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `mc-{matchId}` file as `bouncer-match-sync` writes it. The file below is the shape of a real
 * one (IND v WI, 151554, 2026-10-03) trimmed to two forecast hours.
 */
class MatchContextTest {

    private val file = """
        {"v":1,"matchId":"151554","format":"ODI","gender":"male","modelAsOf":"2026-09-28",
         "venue":{"id":"851","name":"Eden Gardens","city":"Kolkata","lat":22.56,"lon":88.34,
                  "known":true,"matches":20,"scoring":1.086,"chaseAdvantage":0.03,"drawAdvantage":0},
         "toss":{"winnerId":"2","winnerName":"India","decision":"Batting"},"dayNight":false,
         "teams":[{"id":"2","name":"India","known":true,"elo":1720,"joined":10,"listed":11,"members":[]}],
         "h2h":{"games":30,"firstWins":19,"secondWins":10},
         "forecast":{"source":"open-meteo","fetchedAt":1791040000000,"hours":[
            {"t":1791039600000,"tempC":29.4,"humidity":71,"dewC":23.1,"rainMm":0,"rainChance":10,"windKph":13,"cloud":55},
            {"t":1791043200000,"tempC":28.6,"humidity":75,"dewC":23.4,"rainMm":0.4,"rainChance":45,"windKph":11,"cloud":80}]},
         "rev":3,"builtAt":1791040000000}
    """.trimIndent()

    @Test
    fun `the file parses, and fields this build does not model are ignored`() {
        val context = checkNotNull(MatchContextStore.parse(file))
        assertEquals(3, context.rev)
        assertEquals("Eden Gardens", context.venue?.name)
        assertEquals(20, context.venue?.matches)
        assertEquals(9, context.venue?.scoringPercent())
        assertEquals(2, context.forecast?.hours?.size)
    }

    @Test
    fun `the summary reads the hour in progress and the worst rain chance ahead`() {
        val forecast = checkNotNull(MatchContextStore.parse(file)?.forecast)
        val summary = checkNotNull(forecast.summary(nowMs = 1791040200000))
        assertEquals(29, summary.tempC)
        assertEquals(71, summary.humidity)
        assertEquals(45, summary.maxRainChance)
        assertEquals(13, summary.windKph)
        assertEquals(2, summary.hours)
        assertEquals(1791040000000, summary.fetchedAt)
    }

    @Test
    fun `a forecast whose hours have all passed is not shown as the weather now`() {
        val forecast = checkNotNull(MatchContextStore.parse(file)?.forecast)
        assertNull(forecast.summary(nowMs = 1791043200000 + 2 * 3_600_000L))
    }

    @Test
    fun `an hour the source left a field out of keeps it absent, not zero`() {
        val sparse = """{"forecast":{"source":"open-meteo","fetchedAt":5,"hours":[{"t":10}]}}"""
        val summary = checkNotNull(MatchContextStore.parse(sparse)?.forecast?.summary(nowMs = 10))
        assertNull(summary.tempC)
        assertNull(summary.maxRainChance)
    }

    @Test
    fun `an empty or unrelated body is no context rather than a crash`() {
        assertNotNull(MatchContextStore.parse("{}"))
        assertNull(MatchContextStore.parse("""{"message":"The requested file could not be found.","code":404"""))
        assertNull(MatchContextStore.parse(""))
    }

    @Test
    fun `the file id is the one match-sync writes`() {
        assertEquals("mc-151554", MatchContextStore.fileId("151554"))
        assertEquals("mc-a_b", MatchContextStore.fileId("a/b"))
    }

    @Test
    fun `a context carries its preview and summary as text`() {
        val json = """{"preview":{"text":"India play West Indies.","provider":"template","at":5},"summary":{"text":"India won."}}"""
        val context = checkNotNull(MatchContextStore.parse(json))
        assertEquals("India play West Indies.", context.preview?.text)
        assertEquals("India won.", context.summary?.text)
        assertNull(MatchContextStore.parse("{}")?.preview)
    }

    @Test
    fun `the record is read with the names it belongs to, and withheld when it is too thin`() {
        val context = checkNotNull(MatchContextStore.parse(file))
        assertNull("one side listed in this fixture, so the second name is missing", context.recordedMeetings())

        val both = """{"teams":[{"name":"India"},{"name":"West Indies"}],"h2h":{"games":59,"firstWins":40,"secondWins":18}}"""
        assertEquals(
            Triple(59, "India" to 40, "West Indies" to 18),
            MatchContextStore.parse(both)?.recordedMeetings(),
        )
        val thin = """{"teams":[{"name":"A"},{"name":"B"}],"h2h":{"games":2,"firstWins":2,"secondWins":0}}"""
        assertNull(MatchContextStore.parse(thin)?.recordedMeetings())
    }
}
