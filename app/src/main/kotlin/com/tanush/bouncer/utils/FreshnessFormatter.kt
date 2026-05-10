package com.tanush.bouncer.utils

/**
 * Centralized relative-time formatting for data freshness indicators.
 *
 * format()        — verbose labels ("Just now", "1m ago", … "Stale")
 * formatCompact() — compact labels ("<1m", "2m", "10m+")
 */
object FreshnessFormatter {

    /**
     * Returns a human-readable relative time string for data staleness.
     * @param staleAtMillis epoch millis when the data was last fresh
     */
    fun format(staleAtMillis: Long): String {
        val diffSeconds = (System.currentTimeMillis() - staleAtMillis) / 1000
        return when {
            diffSeconds < 30  -> "Just now"
            diffSeconds < 90  -> "1m ago"
            diffSeconds < 150 -> "2m ago"
            diffSeconds < 210 -> "3m ago"
            diffSeconds < 270 -> "4m ago"
            diffSeconds < 330 -> "5m ago"
            else              -> "Stale"
        }
    }

    /**
     * Returns a compact relative time string suitable for chips/badges.
     * @param timestampMillis epoch millis of the reference point
     */
    fun formatCompact(timestampMillis: Long): String {
        val elapsed = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
        val minutes = elapsed / 60_000
        return when {
            minutes < 1  -> "<1m"
            minutes < 10 -> "${minutes}m"
            else         -> "10m+"
        }
    }
}
