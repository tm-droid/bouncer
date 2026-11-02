package com.tanush.bouncer.data

import com.tanush.bouncer.managers.NotificationItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [AppJson] against the JSON that reflective Gson used to write into SharedPreferences.
 *
 * Every migrated store reads blobs written by an older build of the app. Gson was lenient in three
 * ways that a default `Json {}` is not — unknown keys, an explicit null where a default exists, and
 * relaxed quoting — so the migration only holds if those stay configured. These tests fail if
 * someone "simplifies" AppJson back to defaults, which would present as installed users silently
 * losing their favourites and notification inbox on upgrade.
 */
class AppJsonGsonCompatTest {

    @Test
    fun `reads a favourites list written by Gson`() {
        val fromGson = """["India","Australia","Mumbai Indians"]"""
        assertEquals(
            listOf("India", "Australia", "Mumbai Indians"),
            AppJson.decodeFromString<List<String>>(fromGson),
        )
    }

    @Test
    fun `reads a team acronym map written by Gson`() {
        val fromGson = """{"India":"IND","Australia":"AUS"}"""
        assertEquals(
            mapOf("India" to "IND", "Australia" to "AUS"),
            AppJson.decodeFromString<Map<String, String>>(fromGson),
        )
    }

    @Test
    fun `reads a notification inbox written by Gson`() {
        val fromGson = """
            [{"id":"abc","matchId":"m1","title":"WICKET! Kohli b Starc",
              "body":"India 120/3","type":"wicket","timestamp":1755900000000,"isRead":false}]
        """.trimIndent()
        val items = AppJson.decodeFromString<List<NotificationItem>>(fromGson)
        assertEquals(1, items.size)
        assertEquals("abc", items[0].id)
        assertEquals("wicket", items[0].type)
        assertEquals(1_755_900_000_000L, items[0].timestamp)
    }

    @Test
    fun `an unknown field added upstream does not throw`() {
        // Gson dropped unknown fields silently. A strict parser would throw here, which on a
        // device means an inbox that empties itself the day a field is added.
        val withNewField = """
            [{"id":"abc","matchId":"m1","title":"t","body":"b","type":"six",
              "timestamp":1,"isRead":true,"priority":"high"}]
        """.trimIndent()
        assertEquals(1, AppJson.decodeFromString<List<NotificationItem>>(withNewField).size)
    }

    @Test
    fun `an explicit null for a defaulted field falls back to the default`() {
        val withNull = """[{"matchId":"m1","title":"t","body":"b","type":"update","isRead":null}]"""
        val items = AppJson.decodeFromString<List<NotificationItem>>(withNull)
        assertEquals(false, items[0].isRead)
    }

    @Test
    fun `round trips through encode and decode`() {
        val original = NotificationItem(
            id = "x", matchId = "m", title = "t", body = "b", type = "four",
            timestamp = 42L, isRead = true,
        )
        val encoded = AppJson.encodeToString(listOf(original))
        assertEquals(listOf(original), AppJson.decodeFromString<List<NotificationItem>>(encoded))
    }

    @Test
    fun `a default Json would reject what AppJson accepts`() {
        // Guards the guard: if this ever stops throwing, the leniency settings on AppJson have
        // stopped being load-bearing and the tests above would pass for the wrong reason.
        val withNewField = """
            [{"id":"a","matchId":"m","title":"t","body":"b","type":"six",
              "timestamp":1,"isRead":true,"priority":"high"}]
        """.trimIndent()
        val threw = runCatching { Json.decodeFromString<List<NotificationItem>>(withNewField) }.isFailure
        assertTrue("default Json should reject an unknown key", threw)
    }
}
