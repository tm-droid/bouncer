package com.tanush.bouncer.data.repository

import com.tanush.bouncer.data.AppJson
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
 * refines the wording. See `appwrite/functions/news-sync/src/newsResult.js`.
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
) : Exception(message, cause)

interface NewsRepository {
    suspend fun fetchHeadlines(): Headlines
    suspend fun fetchArticleBody(url: String): List<String>
}

class CricbuzzNewsRepository : NewsRepository {
    override suspend fun fetchHeadlines(): Headlines {
        val body = when (val outcome = AppwriteClient.executeFunctionDetailed(FUNCTION_ID, "")) {
            is FunctionOutcome.Success -> outcome.body
            is FunctionOutcome.HttpError -> throw NewsUnavailableException(outcome.statusCode)
            FunctionOutcome.Unavailable -> throw NewsUnavailableException()
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

    override suspend fun fetchArticleBody(url: String): List<String> = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent(randomUserAgent())
            .timeout(ARTICLE_TIMEOUT_MS)
            .get()

        val bodyContainer = doc.selectFirst("div.flex.flex-col.gap-6.text-base")
            ?: doc.selectFirst("div[class*=flex][class*=flex-col][class*=gap-6][class*=text-base]")

        if (bodyContainer != null) {
            bodyContainer.children()
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
        } else {
            doc.select("div.cb-nws-dtl-itms p, div.cb-nws-intr p, article p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf("Unable to load article content. Please try again.") }
        }
    }

    private companion object {
        const val FUNCTION_ID = "bouncer-news-sync"
        const val ARTICLE_TIMEOUT_MS = 15_000
    }
}
