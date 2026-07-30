package com.tanush.bouncer.services

import com.tanush.bouncer.utils.CommentaryFormatter
import org.junit.Assert.assertEquals
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

    @Test
    fun `SYSTEM_INSTRUCTION is byte-identical across Kotlin and llm_js`() {
        assertEquals(
            "The prompt drifted. The same ball can be narrated by either path, so a difference " +
                "here is two house styles in one match — reconcile both copies, do not relax this.",
            CommentaryFusionService.SYSTEM_INSTRUCTION,
            LlmJsSource.stringConstant("SYSTEM_INSTRUCTION")
        )
    }

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
}
