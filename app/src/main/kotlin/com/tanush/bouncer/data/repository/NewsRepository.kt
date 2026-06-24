package com.tanush.bouncer.data.repository

import com.google.gson.Gson
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.models.CricbuzzNewsResponse
import com.tanush.bouncer.models.NewsArticleData
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
        val body = AppwriteClient.executeFunction("bouncer-news-sync", "")
            ?: error("News is temporarily unavailable.")
        val newsResponse = Gson().fromJson(body, CricbuzzNewsResponse::class.java)
            ?: error("News is temporarily unavailable.")
        return newsResponse.storyList
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
