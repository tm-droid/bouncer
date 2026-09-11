package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `MatchLiveDetailPayload` must have a compile-time serializer.
 *
 * It lost its `@Serializable` in the Gson to kotlinx migration. kotlinx does not fail that at
 * compile time — `decodeFromString` throws at runtime with "Serializer for class
 * 'MatchLiveDetailPayload' is not found" — and `AppwriteMatchDetailClient.parsePayload` wraps the
 * read in `runCatching` + `logSilentFailure`, so the entire `match-live-detail` path went dead
 * behind a warning breadcrumb: no server score, batsmen, bowler, recent balls or win probability,
 * on every match open (Sentry ANDROID-3X).
 *
 * The JSON here is the shape `bouncer-match-sync` writes.
 */
class MatchLiveDetailPayloadSerializationTest {

    private val payloadJson = """
        {
          "batsmen": [{"name":"Yashasvi Jaiswal","runs":"45","balls":"53","fours":"9","sixes":"0","strikeRate":"84.91"}],
          "bowler": {"name":"Asitha Fernando","overs":"9.2","maidens":"1","runs":"47","wickets":"3","economy":"5.03"},
          "recentBalls": ["1","0","4","W"],
          "score": "475/8",
          "currentRunRate": "3.62",
          "winProbabilityTeam1": 0.61,
          "winProbabilityDraw": 0.22
        }
    """.trimIndent()

    @Test
    fun `the server payload decodes`() {
        val payload = AppJson.decodeFromString<MatchLiveDetailPayload>(payloadJson)
        assertEquals("475/8", payload.score)
        assertEquals("Yashasvi Jaiswal", payload.batsmen.single().name)
        assertEquals("Asitha Fernando", payload.bowler?.name)
        assertEquals(listOf("1", "0", "4", "W"), payload.recentBalls)
        assertEquals(0.61, payload.winProbabilityTeam1!!, 0.0001)
        assertEquals(0.22, payload.winProbabilityDraw!!, 0.0001)
    }

    @Test
    fun `a field the server has not sent yet does not fail the whole payload`() {
        // AppJson ignores unknown keys; a new server field must not blank the live card.
        val payload = AppJson.decodeFromString<MatchLiveDetailPayload>(
            """{"score":"120/3","someFutureField":{"a":1}}"""
        )
        assertEquals("120/3", payload.score)
    }

    /**
     * `situation` shipped on the wire long before it was modelled here, so `ignoreUnknownKeys`
     * dropped it on every read and the device went on inferring the format and the batting side
     * from strings. The captured shape below is a real fourth-innings Test row.
     */
    @Test
    fun `the situation object decodes`() {
        val payload = AppJson.decodeFromString<MatchLiveDetailPayload>(
            """
            {
              "score": "63/5 (18.4)",
              "situation": {
                "inningsId": 4, "matchFormat": "TEST",
                "battingTeamName": "KENT", "bowlingTeamName": "DERBY",
                "battingTeamIsTeam1": false, "firstInningsTeamId": "144",
                "runsScored": 63, "wicketsLost": 5, "ballsBowled": 112,
                "target": 434, "runsNeeded": 371, "oversRemaining": 79.2,
                "currentRunRate": 3.38, "requiredRunRate": 4.68,
                "long": {"inningsIndex": 3, "lead": -370, "declared": true}
              }
            }
            """.trimIndent(),
        )
        val situation = payload.situation!!
        assertEquals("TEST", situation.matchFormat)
        assertEquals("KENT", situation.battingTeamName)
        assertEquals(false, situation.battingTeamIsTeam1)
        assertEquals(371, situation.runsNeeded)
        assertEquals(79.2, situation.oversRemaining!!, 0.0001)
    }

    /** Rows written before the field existed, and a miniscore with no current innings, have none. */
    @Test
    fun `a payload with no situation still decodes`() {
        val payload = AppJson.decodeFromString<MatchLiveDetailPayload>("""{"score":"120/3"}""")
        assertEquals("120/3", payload.score)
        assertNull(payload.situation)
    }

    @Test
    fun `it round-trips`() {
        val decoded = AppJson.decodeFromString<MatchLiveDetailPayload>(payloadJson)
        val again = AppJson.decodeFromString<MatchLiveDetailPayload>(AppJson.encodeToString(decoded))
        assertNotNull(again.bowler)
        assertEquals(decoded, again)
    }
}
