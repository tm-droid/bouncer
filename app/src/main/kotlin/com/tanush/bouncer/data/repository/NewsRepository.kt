package com.tanush.bouncer.data.repository

import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.network.ScrapeFetcher
import com.tanush.bouncer.network.isUpstreamOutage
import com.tanush.bouncer.data.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.FunctionOutcome
import com.tanush.bouncer.models.CricbuzzNewsResponse
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.network.randomUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Headlines, plus how old they are.
 *
 * [staleAgeMs] is non-null only when the server served its cache because the live source was
 * unreachable *and* it could date that cache. [isStale] is the flag to branch on; the age only
 * refines the wording. See `appwrite/functions/api/src/news/newsResult.js`.
 */
data class Headlines(
    val articles: List<NewsArticleData>,
    val isStale: Boolean = false,
    val staleAgeMs: Long? = null,
)

/**
 * The news source could not be reached.
 *
 * [statusCode] is the function's HTTP status when it answered at all, and null when it did not
 * (SDK not initialised, quota breaker latched, execution failed). Carrying it is the point of the
 * type: without it a 429 from an exhausted Cricbuzz plan and a latched breaker were the same
 * message, and the screen was reduced to substring-matching that message to guess which it was.
 */
class NewsUnavailableException(
    val statusCode: Int? = null,
    message: String = "News is temporarily unavailable.",
    cause: Throwable? = null,
    /**
     * True when the call never reached the function at all — no network, SDK not initialised, or
     * the quota breaker is latched. That is a state of the device or the plan, not a defect, and
     * it is the one shape of this exception that must not be reported to Sentry: it fires on
     * every refresh for as long as the condition lasts, and it buries the shapes that do mean
     * something. Everything else — a 4xx, a 5xx, a response that would not parse — is reported.
     */
    val transportFailure: Boolean = false,
) : Exception(message, cause)

interface NewsRepository {
    suspend fun fetchHeadlines(): Headlines

    /**
     * The article's body paragraphs.
     *
     * @param storyId the Cricbuzz story id, which is what the server caches the body under.
     * @param articleUrl the derived cricbuzz.com URL, used only by the on-device fallback scrape.
     * @throws NewsUnavailableException when no body could be obtained. An empty list is never
     *   returned for a failure — the screen renders "no paragraphs" and "could not load" very
     *   differently, and collapsing them is what made a dead scraper look like a short article.
     */
    suspend fun fetchArticleBody(storyId: Long, articleUrl: String): List<String>
}

/** The article-body half of `bouncer-news-sync`'s response. */
@Serializable
private data class ArticleBodyResponse(
    val paragraphs: List<String> = emptyList(),
)

class CricbuzzNewsRepository : NewsRepository {
    override suspend fun fetchHeadlines(): Headlines {
        val body = when (val outcome = AppwriteClient.executeFunctionDetailed(FUNCTION_ID, "")) {
            is FunctionOutcome.Success -> outcome.body
            is FunctionOutcome.HttpError -> throw NewsUnavailableException(outcome.statusCode)
            FunctionOutcome.Unavailable -> throw NewsUnavailableException(transportFailure = true)
        }

        val response = try {
            AppJson.decodeFromString<CricbuzzNewsResponse>(body)
        } catch (e: SerializationException) {
            // A 200 carrying something that is not the news shape is a source-format change, not a
            // transient outage, and it must not read as an empty news day.
            throw NewsUnavailableException(message = "News response could not be read.", cause = e)
        } ?: throw NewsUnavailableException(message = "News response was empty.")

        val articles = response.storyList.orEmpty()
            .mapNotNull { it.story }
            .mapIndexedNotNull { index, story ->
                NewsArticleData.fromCricbuzzStory(story, isFirst = index == 0)
            }

        return Headlines(
            articles = articles,
            isStale = response.isStale,
            staleAgeMs = response.cacheAgeMs,
        )
    }

    /**
     * Server first, device scrape second.
     *
     * The body used to be scraped from the device on every tap, and that scrape was the only
     * source. Cricbuzz renamed the body container (`gap-6` → `gap-4`) and every selector missed at
     * once, leaving an article that was a headline and nothing else. `bouncer-news-sync` now holds
     * the body, fetched from RapidAPI once per story and cached permanently — a published article's
     * text does not change, so a story costs at most one upstream call ever.
     *
     * The scrape stays as the fallback because it costs no RapidAPI quota and covers an upstream
     * outage; it is no longer the thing the feature depends on.
     */
    override suspend fun fetchArticleBody(storyId: Long, articleUrl: String): List<String> {
        val failures = mutableListOf<Throwable>()
        val fromServer = attempt(failures) { fetchBodyFromFunction(storyId) }
        if (!fromServer.isNullOrEmpty()) return fromServer

        val scraped = attempt(failures) { scrapeArticleBody(articleUrl) }
        if (!scraped.isNullOrEmpty()) return scraped

        // Both sources failing because the device is offline (or Cricbuzz and the function are
        // both down) is a state, not a defect; both answering with no text is drift. The report
        // used to carry neither the cause nor the distinction (ANDROID-5J).
        throw NewsUnavailableException(
            message = "Article body could not be loaded.",
            cause = failures.firstOrNull(),
            transportFailure = failures.size == 2 && failures.all(::isUnreachable),
        )
    }

    private suspend fun <T> attempt(failures: MutableList<Throwable>, block: suspend () -> T): T? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        failures += e
        null
    }

    private fun isUnreachable(e: Throwable): Boolean =
        (e is NewsUnavailableException && (e.transportFailure || e.statusCode != null)) ||
            HttpClientProvider.isConnectivityException(e) || isUpstreamOutage(e)

    private suspend fun fetchBodyFromFunction(storyId: Long): List<String> {
        val body = when (
            val outcome = AppwriteClient.executeFunctionDetailed(FUNCTION_ID, """{"storyId":$storyId}""")
        ) {
            is FunctionOutcome.Success -> outcome.body
            // A 503 here means the server has no body and could not fetch one. That is not the same
            // as an article with no text, which is why the function never answers with an empty 200.
            is FunctionOutcome.HttpError -> throw NewsUnavailableException(outcome.statusCode)
            FunctionOutcome.Unavailable -> throw NewsUnavailableException(transportFailure = true)
        }
        return AppJson.decodeFromString<ArticleBodyResponse>(body).paragraphs
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Body paragraphs from the article page itself.
     *
     * The container is matched WITHOUT its `gap-N` class. That class is the one that changed, and it
     * carries no meaning the selector needs: `div.flex.flex-col.text-base` is unique on the page —
     * the other `text-base` divs are `text-base font-bold*` and have neither `flex` nor `flex-col`.
     * The legacy `cb-*` selectors are kept for older markup; they match nothing on the App Router
     * pages Cricbuzz serves today.
     */
    private suspend fun scrapeArticleBody(url: String): List<String> = withContext(Dispatchers.IO) {
        val doc = ScrapeFetcher.get(url, randomUserAgent(), ARTICLE_TIMEOUT_MS)

        val bodyContainer = doc.selectFirst("div.flex.flex-col.text-base")
        if (bodyContainer != null) {
            bodyContainer.children()
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
        } else {
            doc.select("div.cb-nws-dtl-itms p, div.cb-nws-intr p, article p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
        }
    }

    private companion object {
        const val FUNCTION_ID = "bouncer-api/news"
        const val ARTICLE_TIMEOUT_MS = 15_000
    }
}
