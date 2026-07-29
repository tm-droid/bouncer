package com.tanush.bouncer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity with `sanitizeMatchId` in `appwrite/functions/match-sync/src/main.js`:
 *
 * ```js
 * const sanitizeMatchId = (id) => String(id).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 36);
 * ```
 *
 * A divergence here does not throw — it produces a Realtime subscription to a row id that will
 * never receive an event, so the live card silently stops updating. Hence the explicit table.
 */
class MatchRowIdTest {

    /** Reference implementation of the JS regex + slice, kept separate from the one under test. */
    private fun jsEquivalent(id: String): String {
        val kept = id.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }
        return if (kept.length <= 36) kept else kept.substring(0, 36)
    }

    private val cases = listOf(
        "12345",
        "already-clean_id.v2",
        "with spaces in it",
        "slash/and:colon",
        "emoji🏏inside",
        "ünïcödé-áccents",
        "a".repeat(36),
        "b".repeat(37),
        "c".repeat(200),
        "!!!",
        "",
        "cricbuzz#98765?x=1",
        "  leading and trailing  ",
        "tab\tnewline\nreturn\r",
        "ID_WITH_UPPER-123.4",
    )

    @Test
    fun `matches the javascript implementation for every case`() {
        cases.forEach { input ->
            assertEquals("input=<$input>", jsEquivalent(input), sanitizeMatchId(input))
        }
    }

    @Test
    fun `never exceeds the appwrite row id length cap`() {
        cases.forEach { input ->
            assertTrue("input=<$input>", sanitizeMatchId(input).length <= 36)
        }
    }

    @Test
    fun `strips characters appwrite rejects in a row id`() {
        assertEquals("withspacesinit", sanitizeMatchId("with spaces in it"))
        assertEquals("slashandcolon", sanitizeMatchId("slash/and:colon"))
        assertEquals("", sanitizeMatchId("!!!"))
    }

    @Test
    fun `preserves the characters appwrite allows`() {
        assertEquals("already-clean_id.v2", sanitizeMatchId("already-clean_id.v2"))
    }

    @Test
    fun `truncates only past the cap`() {
        assertEquals(36, sanitizeMatchId("b".repeat(37)).length)
        assertEquals(36, sanitizeMatchId("a".repeat(36)).length)
    }

    @Test
    fun `strips multi-byte characters before truncating`() {
        // If truncation ran first, a stripped multi-byte char would shift the cut point and the
        // two implementations would disagree on long unicode ids.
        val input = "🏏".repeat(50) + "x".repeat(40)
        assertEquals(jsEquivalent(input), sanitizeMatchId(input))
        assertEquals("x".repeat(36), sanitizeMatchId(input))
    }
}
