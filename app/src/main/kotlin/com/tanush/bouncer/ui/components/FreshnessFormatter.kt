package com.tanush.bouncer.ui.components

/**
 * Single source of truth for relative-time formatting used by both
 * in-app [StaleDataBadge] and widget [WidgetUpdateWorker].
 *
 * Pure Kotlin utility — no Compose or Android framework dependencies.
 */
object FreshnessFormatter {

    /** Categorises how fresh a timestamp is relative to now. */
    enum class FreshnessLevel {
        /** < 30 seconds old. */
        FRESH,
        /** 30 s – 2 min old. */
        RECENT,
        /** 2 – 5 min old. */
        AGING,
        /** 5 – 15 min old. */
        STALE,
        /** > 15 min old. */
        VERY_STALE
    }

    private const val THIRTY_SECONDS_MS = 30_000L
    private const val ONE_MINUTE_MS = 60_000L
    private const val TWO_MINUTES_MS = 2 * ONE_MINUTE_MS
    private const val FIVE_MINUTES_MS = 5 * ONE_MINUTE_MS
    private const val FIFTEEN_MINUTES_MS = 15 * ONE_MINUTE_MS

    /**
     * Returns a human-readable relative time string.
     *
     * Examples: `"Just now"`, `"1m ago"`, `"3m ago"`, `"10m ago"`, `"15m+ ago"`.
     *
     * @param timestampMillis epoch millis of the event. `0` or negative → `"Unknown"`.
     * @param nowMillis current epoch millis (default [System.currentTimeMillis]).
     */
    fun format(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (timestampMillis <= 0L) return "Unknown"

        val delta = nowMillis - timestampMillis
        if (delta < 0L) return "Just now"

        return when {
            delta < THIRTY_SECONDS_MS  -> "Just now"
            delta < TWO_MINUTES_MS     -> "1m ago"
            delta < FIVE_MINUTES_MS    -> "${delta / ONE_MINUTE_MS}m ago"
            delta < FIFTEEN_MINUTES_MS -> "${delta / ONE_MINUTE_MS}m ago"
            else                       -> "15m+ ago"
        }
    }

    /**
     * Returns the [FreshnessLevel] for conditional styling/colouring.
     *
     * @param timestampMillis epoch millis of the event. `0` or negative → [FreshnessLevel.VERY_STALE].
     * @param nowMillis current epoch millis (default [System.currentTimeMillis]).
     */
    fun level(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): FreshnessLevel {
        if (timestampMillis <= 0L) return FreshnessLevel.VERY_STALE

        val delta = nowMillis - timestampMillis
        if (delta < 0L) return FreshnessLevel.FRESH

        return when {
            delta < THIRTY_SECONDS_MS  -> FreshnessLevel.FRESH
            delta < TWO_MINUTES_MS     -> FreshnessLevel.RECENT
            delta < FIVE_MINUTES_MS    -> FreshnessLevel.AGING
            delta < FIFTEEN_MINUTES_MS -> FreshnessLevel.STALE
            else                       -> FreshnessLevel.VERY_STALE
        }
    }

    /**
     * Short format for widgets with limited space.
     *
     * Examples: `"Now"`, `"1m"`, `"5m"`, `"15m+"`.
     *
     * @param timestampMillis epoch millis of the event. `0` or negative → `"—"`.
     * @param nowMillis current epoch millis (default [System.currentTimeMillis]).
     */
    fun formatCompact(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (timestampMillis <= 0L) return "—"

        val delta = nowMillis - timestampMillis
        if (delta < 0L) return "Now"

        return when {
            delta < THIRTY_SECONDS_MS  -> "Now"
            delta < TWO_MINUTES_MS     -> "1m"
            delta < FIVE_MINUTES_MS    -> "${delta / ONE_MINUTE_MS}m"
            delta < FIFTEEN_MINUTES_MS -> "${delta / ONE_MINUTE_MS}m"
            else                       -> "15m+"
        }
    }
}
