package com.tanush.bouncer.services

import com.tanush.bouncer.data.remote.parseCommentaryEvent
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.utils.CommentaryFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build when a constant duplicated in `appwrite/functions/match-sync/src/llm.js` stops
 * matching its Kotlin counterpart.
 *
 * The same delivery can be narrated by either the on-device path or `bouncer-match-sync`, so the
 * two must agree on what a refusal is, what a ball label looks like, and how the model is
 * instructed. Keeping them in step was a review item until now, and that failed twice:
 *
 *  - the `(e.g., 18.5: ...)` example survived in both prompts until Phase 2, and became 33% of the
 *    commentary cache;
 *  - `META` and `LEADING_BALL_LABEL` drifted out of parity and stayed that way, the latter leaving
 *    every Hundred refusal attached to the line `match-sync` wrote to production.
 *
 * These run in the normal `testDebugUnitTest` task, so an edit to one side without the other stops
 * a build instead of waiting to be noticed.
 */
class CommentaryConstantParityTest {

    @Test
    fun `the llm_js source is found and parsed, not silently skipped`() {
        // Guards the guard. If the function is moved or renamed and the locator quietly returned
        // empty text, every comparison below would pass vacuously — the exact shape of failure
        // this file exists to prevent.
        assertTrue("llm.js appears empty", LlmJsSource.text.length > 500)
        assertTrue(
            "llm.js does not look like the expected module",
            LlmJsSource.text.contains("export function stripPreamble")
        )
    }

    // `SYSTEM_INSTRUCTION` had a Kotlin copy until CommentaryFusionService was deleted. That
    // service was a second, fully-wired AI-commentary path with no production call site, and its
    // prompt was the only thing on the device that had to match llm.js. The prompt now exists
    // server-side only, where `LlmJsVendorParityTest` pins the two vendored copies against each
    // other; there is no third copy left to drift.

    @Test
    fun `the meta refusal pattern is identical across Kotlin and llm_js`() {
        val js = LlmJsSource.regexConstant("META")

        assertEquals(
            "META drifted from CommentaryFormatter.META_PREAMBLE. Whichever copy is missing an " +
                "opener will cache the model's refusal as commentary.",
            CommentaryFormatter.META_PREAMBLE.pattern,
            js.source
        )
        // Case sensitivity is part of the contract: models capitalise their openers.
        assertEquals("llm.js META must be case-insensitive", "i", js.flags)
        assertTrue(
            "CommentaryFormatter.META_PREAMBLE must be case-insensitive",
            RegexOption.IGNORE_CASE in CommentaryFormatter.META_PREAMBLE.options
        )
    }

    @Test
    fun `the leading ball label pattern is identical across Kotlin and llm_js`() {
        val js = LlmJsSource.regexConstant("LEADING_BALL_LABEL")

        assertEquals(
            "LEADING_BALL_LABEL drifted. An over-based-only pattern leaves the preamble attached " +
                "to every Hundred delivery, which is exactly the 2026-07-30 defect.",
            CommentaryFormatter.LEADING_BALL_LABEL.pattern,
            js.source
        )
        assertEquals("llm.js LEADING_BALL_LABEL takes no flags", "", js.flags)
        assertTrue(
            "CommentaryFormatter.LEADING_BALL_LABEL takes no options",
            CommentaryFormatter.LEADING_BALL_LABEL.options.isEmpty()
        )
    }

    @Test
    fun `both ball-label forms are actually matched by the shared pattern`() {
        // Parity alone would be satisfied by two identically-wrong copies. This pins the property
        // the pattern exists for, on the side the test can execute.
        val pattern = CommentaryFormatter.LEADING_BALL_LABEL
        assertTrue("over-based label", pattern.containsMatchIn("preamble text 18.5: Kohli drives."))
        assertTrue("Hundred label", pattern.containsMatchIn("preamble text Ball 72: Banton lofts."))
    }

    // ── classifyEvent: the `event` column's vocabulary ────────────────────────
    //
    // This one is not a text constant but a wire contract. `classifyEvent` decides the string
    // written to `match-commentary.event`, and `parseCommentaryEvent` is the only thing that reads
    // it. A value added on the JS side and not taught to Kotlin does not throw — it parses to null,
    // which the commentary tab reads as "the server had no opinion" and answers by classifying the
    // AI's own prose. That is precisely the failure this column was added to remove, returning
    // silently and only for the new event type.

    @Test
    fun `every event classifyEvent can write is understood by the Kotlin parser`() {
        val written = LlmJsSource.returnedStringLiterals("classifyEvent")

        // Guards the guard: an empty list would make the loop below vacuous.
        assertTrue(
            "no return literals found in classifyEvent — did it stop returning string literals?",
            written.size >= 2
        )
        written.forEach { value ->
            assertNotNull(
                "match-sync writes event=\"$value\" and parseCommentaryEvent does not know it, so " +
                    "the tab falls back to classifying broadcast prose. Add it to the when.",
                parseCommentaryEvent(value)
            )
        }
    }

    @Test
    fun `an ordinary delivery is written as a verdict, not as an absence`() {
        val written = LlmJsSource.returnedStringLiterals("classifyEvent")

        assertTrue(
            "classifyEvent must be total — a dot ball with no `event` sends the device back to " +
                "reading the prose, where \"needs six from four\" is a boundary.",
            "NONE" in written
        )
        assertEquals(
            "NONE must parse to a non-boundary verdict that is distinct from null (= no verdict).",
            TimelineEventType.OTHER,
            parseCommentaryEvent("NONE")
        )
    }

    @Test
    fun `the event regexes stay case-sensitive`() {
        // Cricbuzz capitalises the event and lowercases the prose around it. An `i` flag here makes
        // "sixth", "fourth" and "outside off" into events, which is most of an innings.
        listOf("UPPER_SIX_RE", "UPPER_FOUR_RE", "UPPER_WICKET_RE").forEach { name ->
            assertFalse(
                "$name must not take the i flag — see the comment above it in llm.js",
                'i' in LlmJsSource.regexConstant(name).flags
            )
        }
    }
}
