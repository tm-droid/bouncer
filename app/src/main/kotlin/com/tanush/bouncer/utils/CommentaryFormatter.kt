package com.tanush.bouncer.utils

object CommentaryFormatter {
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val AI_FUSED_PREFIX = Regex("^\\[AI Fused]\\s*")
    private val MULTI_SPACE = Regex("\\s{2,}")

    /**
     * Openers the models use when they refuse or narrate their own reasoning.
     *
     * Kept in sync with `META` in appwrite/functions/match-sync/src/llm.js.
     *
     * The outright-refusal openers ("I cannot", "I'm sorry", "I apologise") cover a gap the
     * 2026-07-31 Hundred replay found: the list handled the models *explaining* themselves
     * ("Since there is no ball number…", 54 rows of the 750-row production cache export) but not
     * the models *declining*, which is the same failure with a different first word. No refusal of
     * that shape has reached production, so this closes the hole rather than fixing an outage.
     */
    val META_PREAMBLE = Regex(
        "^(since there|i('| wi)ll |i cannot|i can'?t|i'm (unable|sorry)|i am (unable|sorry)|" +
            "i apologi[sz]e|here('| i)s|given (the|that|there)|note:|as an ai|" +
            "based on the|to create|it seems|there is no|there's no|unfortunately)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Both ball-label forms: `48.6` (over-based) and `Ball 72` (The Hundred).
     *
     * Internal rather than private so `CommentaryConstantParityTest` can assert it still matches
     * the `LEADING_BALL_LABEL` in appwrite/functions/match-sync/src/llm.js. An over-based-only
     * pattern silently leaves the preamble attached to every Hundred line, which is what the
     * server copy did until 2026-07-30.
     */
    internal val LEADING_BALL_LABEL = Regex("((?:\\d{1,3}\\.\\d|Ball\\s+\\d{1,3}):?\\s.*)$")

    fun sanitize(text: String): String =
        text.replace(AI_FUSED_PREFIX, "")
            .replace(HTML_TAG_REGEX, "")
            .replace(MULTI_SPACE, " ")
            .trim()

    /**
     * Drops the meta/preamble the models emit instead of commentary.
     *
     * Port of `stripPreamble` in appwrite/functions/match-sync/src/llm.js, which had no client
     * equivalent — which is why 76 rows of "Since there is no specific ball number..." reached the
     * Appwrite cache from the on-device path while the server path filtered its own.
     *
     * Must run on the raw multi-line completion, **before** [sanitize] collapses newlines.
     */
    fun stripPreamble(text: String): String {
        val lines = text.split(Regex("\r?\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val kept = lines.filterNot { META_PREAMBLE.containsMatchIn(it) }
        val out = (if (kept.isNotEmpty()) kept else lines).joinToString(" ").trim()
        // Same-line preamble ("Since there is no ... 18.5: ...") — cut to the ball label.
        if (META_PREAMBLE.containsMatchIn(out)) {
            LEADING_BALL_LABEL.find(out)?.let { return it.groupValues[1].trim() }
        }
        return out
    }

    /** Whether [text] is model meta/refusal rather than commentary. */
    fun isMetaResponse(text: String): Boolean = META_PREAMBLE.containsMatchIn(text.trim())
}
