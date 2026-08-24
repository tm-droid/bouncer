package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun `it round-trips`() {
        val decoded = AppJson.decodeFromString<MatchLiveDetailPayload>(payloadJson)
        val again = AppJson.decodeFromString<MatchLiveDetailPayload>(AppJson.encodeToString(decoded))
        assertNotNull(again.bowler)
        assertEquals(decoded, again)
    }
}
