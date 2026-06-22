package com.tanush.bouncer.data.repository

import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.network.RetrofitClient
import com.tanush.bouncer.network.randomUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

interface NewsRepository {
    suspend fun fetchHeadlines(): List<NewsArticleData>
    suspend fun fetchArticleBody(url: String): List<String>
}

class CricbuzzNewsRepository : NewsRepository {
    override suspend fun fetchHeadlines(): List<NewsArticleData> {
        val response = RetrofitClient.cricbuzzNewsApi.getNewsIndex()
        if (!response.isSuccessful) {
            val msg = when (response.code()) {
                401, 403 -> "News is temporarily unavailable."
                429 -> "News is temporarily rate limited. Please try again later."
                in 500..599 -> "News service is unavailable. Please try again later."
                else -> "Failed to load news (HTTP ${response.code()})."
            }
            error(msg)
        }
        val body = response.body() ?: error("News is temporarily unavailable.")
        return body.storyList
            .mapNotNull { it.story }
            .mapIndexed { index, story -> NewsArticleData.fromCricbuzzStory(story, isFirst = index == 0) }
    }

    override suspend fun fetchArticleBody(url: String): List<String> = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent(randomUserAgent())
            .timeout(15000)
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
}
