package com.tanush.bouncer.utils

object CommentaryFormatter {
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val AI_FUSED_PREFIX = Regex("^\\[AI Fused]\\s*")
    private val MULTI_SPACE = Regex("\\s{2,}")

    fun sanitize(text: String): String =
        text.replace(AI_FUSED_PREFIX, "")
            .replace(HTML_TAG_REGEX, "")
            .replace(MULTI_SPACE, " ")
            .trim()
}
