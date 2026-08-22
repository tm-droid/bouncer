package com.tanush.bouncer.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

/**
 * Data models for Cricbuzz News API responses.
 * Fetched from: https://cricbuzz-cricket.p.rapidapi.com/news/v1/index
 */

@Serializable
@Immutable
data class CricbuzzNewsResponse(
    // Nullable + default: Gson leaves this null when the payload omits storyList (error/empty
    // response), and a non-null declaration would NPE on the first iteration. See NewsRepository.
    @SerialName("storyList") val storyList: List<StoryListItem>? = null,
    @SerialName("lastUpdatedTime") val lastUpdatedTime: String? = null,

    // Written by bouncer-news-sync when it could not reach RapidAPI and served its cache instead.
    // The flag and the age are separate on purpose: a cache whose `fetchedAt` the function could
    // not read is still served, but no age is claimed for it, so the UI must key "is this stale?"
    // off the flag and treat the age as optional detail. Absent on a live response.
    @SerialName("_bouncerStale") val isStale: Boolean = false,
    @SerialName("_bouncerCacheAgeMs") val cacheAgeMs: Long? = null
)

@Serializable
@Immutable
data class StoryListItem(
    @SerialName("story") val story: CricbuzzStory? = null,
    @SerialName("ad") val ad: CricbuzzAd? = null
)

// Every field is nullable with a default, for the same reason storyList is: Gson allocates through
// Unsafe and leaves an absent field null regardless of what the Kotlin type says, so a non-null
// declaration here buys a crash at the first dereference rather than a guarantee. `pubTime` is the
// one that actually bit (see 638c0cc). fromCricbuzzStory supplies the fallbacks in one place.
@Serializable
@Immutable
data class CricbuzzStory(
    @SerialName("id") val id: Long? = null,
    @SerialName("hline") val headline: String? = null,
    @SerialName("intro") val intro: String? = null,
    @SerialName("pubTime") val pubTime: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("storyType") val storyType: String? = null,
    @SerialName("imageId") val imageId: Long? = null,
    @SerialName("seoHeadline") val seoHeadline: String? = null,
    @SerialName("context") val context: String? = null,
    @SerialName("coverImage") val coverImage: CricbuzzCoverImage? = null
)

@Serializable
@Immutable
data class CricbuzzCoverImage(
    @SerialName("id") val id: String? = null,
    @SerialName("caption") val caption: String? = null,
    @SerialName("source") val source: String? = null
)

@Serializable
@Immutable
data class CricbuzzAd(
    @SerialName("name") val name: String? = null,
    @SerialName("layout") val layout: String? = null,
    @SerialName("position") val position: Int? = null
)

/**
 * UI-friendly news article model converted from API response.
 */
@Serializable
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
         * Convert an API story to the UI model, or null when it carries nothing to show.
         *
         * An id and a headline are the two fields a card cannot be rendered without — the id is
         * the list key and the article URL, the headline is the card. Everything else degrades to
         * a blank or "now". Returning null for the unusable ones drops them from the list instead
         * of rendering an untappable empty card.
         */
        fun fromCricbuzzStory(story: CricbuzzStory, isFirst: Boolean = false): NewsArticleData? {
            val id = story.id ?: return null
            val headline = story.headline?.takeIf { it.isNotBlank() } ?: return null
            return NewsArticleData(
                id = id,
                headline = headline,
                intro = story.intro.orEmpty(),
                imageUrl = buildImageUrl(story.imageId, story.coverImage?.caption),
                publishedTime = story.pubTime?.toLongOrNull() ?: System.currentTimeMillis(),
                source = story.source.orEmpty(),
                storyType = story.storyType.orEmpty(),
                context = story.context,
                articleUrl = buildArticleUrl(id, headline),
                isTopStory = isFirst
            )
        }
    }
}
