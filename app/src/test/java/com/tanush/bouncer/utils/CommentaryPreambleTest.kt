package com.tanush.bouncer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Meta/refusal filtering — the Kotlin port of `stripPreamble` in match-sync/src/llm.js.
 *
 * The server path has filtered these since it was written; the on-device path never did, which is
 * how 76 rows of model refusal reached the shared Appwrite cache. The samples here are taken
 * verbatim from that cache export.
 */
class CommentaryPreambleTest {

    @Test
    fun `refusals from the cache export are recognised as meta`() {
        val fromExport = listOf(
            "Since there is no specific ball number provided, I'll write a general line.",
            "Since there's no specific ball number, here is the commentary.",
            "There is no ball number, so I cannot follow the format.",
            "It seems there's insufficient information to write commentary.",
        )
        for (line in fromExport) {
            assertTrue("should be meta: $line", CommentaryFormatter.isMetaResponse(line))
        }
    }

    @Test
    fun `outright refusals are meta, not just self-explanations`() {
        // The list handled the model explaining itself ("Since there is no ball number…") but not
        // the model declining outright — the same failure with a different first word. None of
        // these reached the 750-row production cache export; this closes the hole rather than
        // fixing an outage.
        val refusals = listOf(
            "I cannot provide commentary for this delivery.",
            "I can't write commentary without the ball number.",
            "I'm unable to generate a commentary line here.",
            "I am sorry, but I need more information about the match.",
            "I apologise, I do not have enough detail to narrate this ball.",
        )
        for (line in refusals) {
            assertTrue("should be meta: $line", CommentaryFormatter.isMetaResponse(line))
        }
    }

    @Test
    fun `real commentary is not meta`() {
        val real = listOf(
            "48.6: Douthwaite to Rhodes, worked away to deep midwicket for a single.",
            "Ball 72: Jansen bangs it in short and Banton swivels it away for four.",
            "Mosaddek Hossain to Ben Curran, no run, flatter one outside off.",
        )
        for (line in real) {
            assertFalse("should not be meta: $line", CommentaryFormatter.isMetaResponse(line))
        }
    }

    @Test
    fun `a preamble line is dropped and the commentary kept`() {
        val raw = "Since there is no specific ball number provided, here is a line:\n" +
            "48.6: Douthwaite to Rhodes, tucked away for a single."
        assertEquals(
            "48.6: Douthwaite to Rhodes, tucked away for a single.",
            CommentaryFormatter.stripPreamble(raw)
        )
    }

    @Test
    fun `same-line preamble is cut back to the ball label`() {
        val raw = "Since there is no ball number given I'll start anyway. 18.5: Filer to Beaumont, no run."
        assertEquals(
            "18.5: Filer to Beaumont, no run.",
            CommentaryFormatter.stripPreamble(raw)
        )
    }

    @Test
    fun `same-line preamble is cut back to a Hundred label too`() {
        val raw = "Here's the commentary. Ball 72: Jansen to Banton, four."
        assertEquals("Ball 72: Jansen to Banton, four.", CommentaryFormatter.stripPreamble(raw))
    }

    @Test
    fun `clean commentary survives stripping untouched`() {
        val clean = "48.6: Douthwaite to Rhodes, worked away for a single."
        assertEquals(clean, CommentaryFormatter.stripPreamble(clean))
    }

    @Test
    fun `an all-preamble response is not emptied into a blank`() {
        // Nothing survives the filter, so the original is kept and rejected downstream by
        // isMetaResponse rather than silently becoming "".
        val raw = "Since there is no specific ball number provided."
        assertEquals(raw, CommentaryFormatter.stripPreamble(raw))
        assertTrue(CommentaryFormatter.isMetaResponse(CommentaryFormatter.stripPreamble(raw)))
    }
}
