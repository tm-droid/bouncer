package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * Data models for Cricbuzz News API responses.
 * Fetched from: https://cricbuzz-cricket.p.rapidapi.com/news/v1/index
 */

@Immutable
data class CricbuzzNewsResponse(
    @SerializedName("storyList") val storyList: List<StoryListItem>,
    @SerializedName("lastUpdatedTime") val lastUpdatedTime: String? = null
)

@Immutable
data class StoryListItem(
    @SerializedName("story") val story: CricbuzzStory? = null,
    @SerializedName("ad") val ad: CricbuzzAd? = null
)

@Immutable
data class CricbuzzStory(
    @SerializedName("id") val id: Long,
    @SerializedName("hline") val headline: String,
    @SerializedName("intro") val intro: String,
    @SerializedName("pubTime") val pubTime: String,
    @SerializedName("source") val source: String,
    @SerializedName("storyType") val storyType: String,
    @SerializedName("imageId") val imageId: Long? = null,
    @SerializedName("seoHeadline") val seoHeadline: String? = null,
    @SerializedName("context") val context: String? = null,
    @SerializedName("coverImage") val coverImage: CricbuzzCoverImage? = null
)

@Immutable
data class CricbuzzCoverImage(
    @SerializedName("id") val id: String,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("source") val source: String? = null
)

@Immutable
data class CricbuzzAd(
    @SerializedName("name") val name: String,
    @SerializedName("layout") val layout: String,
    @SerializedName("position") val position: Int
)

/**
 * UI-friendly news article model converted from API response.
 */
data class NewsArticleData(      // intentionally unannotated: timeAgo uses System.currentTimeMillis()
    val id: Long,
    val headline: String,
    val intro: String,
    val imageUrl: String?,
    val publishedTime: Long,
    val source: String,
    val storyType: String,
    val context: String?,
    val articleUrl: String,
    val isTopStory: Boolean = false
) {
    /**
     * Format time as relative (e.g., "15 mins ago", "2h ago")
     */
    val timeAgo: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - publishedTime
            val minutes = diff / (1000 * 60)
            val hours = diff / (1000 * 60 * 60)
            val days = diff / (1000 * 60 * 60 * 24)

            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days < 7 -> "${days}d ago"
                else -> "${days / 7}w ago"
            }
        }

    companion object {
        /**
         * Build image URL from Cricbuzz image data.
         * Format: https://static.cricbuzz.com/a/img/v1/2048x1152/i1/c{imageId}/{caption-hyphenated}.jpg
         */
        fun buildImageUrl(imageId: Long?, caption: String?): String? {
            if (imageId == null) return null
            val captionSlug = caption
                ?.lowercase()
                ?.replace(Regex("[^a-z0-9\\s-]"), "")
                ?.trim()
                ?.replace(Regex("\\s+"), "-")
                ?: "image"
            return "https://static.cricbuzz.com/a/img/v1/2048x1152/i1/c${imageId}/${captionSlug}.jpg"
        }

        /**
         * Build article URL from Cricbuzz story data.
         * Format: https://www.cricbuzz.com/cricket-news/{id}/{headline-hyphenated}
         */
        fun buildArticleUrl(id: Long, headline: String): String {
            val headlineSlug = headline
                .lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .trim()
                .replace(Regex("\\s+"), "-")
            return "https://www.cricbuzz.com/cricket-news/${id}/${headlineSlug}"
        }

        /**
         * Convert API response story to UI model.
         */
        fun fromCricbuzzStory(story: CricbuzzStory, isFirst: Boolean = false): NewsArticleData {
            return NewsArticleData(
                id = story.id,
                headline = story.headline,
                intro = story.intro,
                imageUrl = buildImageUrl(story.imageId, story.coverImage?.caption),
                publishedTime = story.pubTime.toLongOrNull() ?: System.currentTimeMillis(),
                source = story.source,
                storyType = story.storyType,
                context = story.context,
                articleUrl = buildArticleUrl(story.id, story.headline),
                isTopStory = isFirst
            )
        }
    }
}
