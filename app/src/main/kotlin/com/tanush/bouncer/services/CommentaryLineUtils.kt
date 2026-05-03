package com.tanush.bouncer.services

internal object CommentaryLineUtils {
    private val ballNumberPattern = Regex("^(\\d{1,3}\\.\\d)")

    fun extractBallNumber(text: String): String? {
        return ballNumberPattern.find(text.trimStart())?.groupValues?.getOrNull(1)
    }

    fun upsertLatestBallLine(
        lines: MutableList<String>,
        line: String,
        maxSize: Int? = null
    ) {
        val normalized = line.trim()
        if (normalized.isBlank()) return

        val ballNumber = extractBallNumber(normalized)
        if (ballNumber != null) {
            val existingIndex = lines.indexOfLast { extractBallNumber(it) == ballNumber }
            if (existingIndex >= 0) {
                if (normalized.length >= lines[existingIndex].length) {
                    lines[existingIndex] = normalized
                }
                return
            }
        } else if (lines.contains(normalized)) {
            return
        }

        lines.add(normalized)
        while (maxSize != null && lines.size > maxSize) {
            lines.removeAt(0)
        }
    }
}
