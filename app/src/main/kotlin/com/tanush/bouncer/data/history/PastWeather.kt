package com.tanush.bouncer.data.history

/**
 * The weather a past match was played in (Open-Meteo archive, CC BY 4.0), over the match's days:
 * the builder's `w` array `[tmax, rain, humidity, wind, cloud]` (`appwrite/tools/history/src/weather.mjs`).
 */
data class PastWeather(
    val maxTempC: Double,
    val rainMm: Double,
    val humidityPercent: Int,
    val windKph: Int,
    val cloudPercent: Int,
) {
    companion object {
        /** Null for an absent, short or non-finite `w`: the match shows no weather rather than a zero. */
        fun of(w: List<Double>): PastWeather? {
            if (w.size < FIELDS || w.take(FIELDS).any { !it.isFinite() }) return null
            return PastWeather(w[0], w[1], w[2].toInt(), w[3].toInt(), w[4].toInt())
        }

        private const val FIELDS = 5
    }
}

/** A whole number without ".0", else one decimal, for the figures a weather line prints. */
fun Double.trimmed(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(java.util.Locale.ROOT, this)

/** The weather of a Cricsheet-only (`cs-`) match, read from its one file; null when the file or the weather is missing. */
suspend fun pastWeatherOf(matchId: String): PastWeather? {
    if (!matchId.startsWith(CRICSHEET_PREFIX)) return null
    return HistoryClient.match(matchId.removePrefix(CRICSHEET_PREFIX))?.let { PastWeather.of(it.w) }
}
