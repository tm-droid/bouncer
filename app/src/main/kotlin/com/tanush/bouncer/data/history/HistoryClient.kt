package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.serialization.json.JsonArray

/**
 * Reads the historical-match site: every Cricsheet match, published weekly to GitHub Pages by
 * `.github/workflows/build-history.yml` (`.claude/rules/history-data.md`). Requests go through
 * [StaticSite].
 *
 * The URL is a constant, not a `local.properties` id, because it is public and a blank template
 * value would only switch the feature off in the Play build.
 *
 * Every failure is null. A missing file is a match Cricsheet does not have, the normal case for
 * most fixtures, and the caller falls back to what it showed before.
 */
object HistoryClient {
    const val BASE_URL = "https://tm-droid.github.io/bouncer-history/"

    // The site is rebuilt weekly and a match file never changes once its match is over, so the
    // request-side max-age is a day.
    private const val MATCH_MAX_AGE_SECONDS = 24 * 60 * 60
    private const val INDEX_MAX_AGE_SECONDS = 24 * 60 * 60
    private const val INDEX_MAX_STALE_SECONDS = 7 * 24 * 60 * 60

    suspend fun match(id: String): HistoryMatch? {
        if (!id.all(Char::isDigit)) return null
        val bytes = fetch("m/$id.json", MATCH_MAX_AGE_SECONDS) ?: return null
        return runCatching { AppJson.decodeFromString<HistoryMatch>(bytes.decodeToString()) }
            .onFailure { sentryBreadcrumb("history", "Unreadable match $id: ${it.message}", SentryLevel.WARNING) }
            .getOrNull()
    }

    /** The matches Cricsheet has for one month, `yyyy-MM`. Empty when it has none. */
    suspend fun month(yearMonth: String): List<HistoryIndexRow>? {
        if (!MONTH.matches(yearMonth)) return null
        val bytes = fetch("idx/m/$yearMonth.json", INDEX_MAX_AGE_SECONDS) ?: return null
        return runCatching {
            AppJson.parseToJsonElement(bytes.decodeToString()).let { it as JsonArray }
                .mapNotNull { (it as? JsonArray)?.let(HistoryIndexRow::of) }
        }.getOrNull()
    }

    /**
     * The players whose surname starts with [letter] (`s/p/names/<letter>.json`, ~43 KB). Weekly data,
     * so a success is kept for the process and a copy up to 7 days stale stands in offline; a failure
     * is null and not remembered.
     */
    suspend fun names(letter: Char): List<HistoryNameRow>? {
        val key = letter.lowercaseChar()
        if (key !in 'a'..'z') return null
        names[key]?.let { return it }
        val bytes = StaticSite.fetch(BASE_URL, "s/p/names/$key.json", INDEX_MAX_AGE_SECONDS, INDEX_MAX_STALE_SECONDS)
            ?: return null
        return CareerStats.parseNames(bytes.decodeToString())?.also { names[key] = it }
    }

    /** A player's Cricsheet record by registry id; null when there is none or it cannot be read. */
    suspend fun player(id: String): PlayerCareer? {
        if (!id.all { it.isLetterOrDigit() }) return null
        val bytes = fetch("s/p/$id.json", INDEX_MAX_AGE_SECONDS) ?: return null
        return CareerStats.parseCareer(bytes.decodeToString())
            ?: null.also { sentryBreadcrumb("history", "Unreadable player $id", SentryLevel.WARNING) }
    }

    private val names = java.util.concurrent.ConcurrentHashMap<Char, List<HistoryNameRow>>()
    private val MONTH = Regex("""\d{4}-\d{2}""")

    private suspend fun fetch(path: String, maxAgeSeconds: Int): ByteArray? =
        StaticSite.fetch(BASE_URL, path, maxAgeSeconds)
}
