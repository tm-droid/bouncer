package com.tanush.bouncer.models

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsArticleDataTest {

    private fun story(pubTime: String? = "1790000000000", source: String? = "Cricbuzz") = CricbuzzStory(
        id = 42,
        headline = "A headline",
        pubTime = pubTime,
        source = source,
        imageId = 1005796,
        coverImage = CricbuzzCoverImage(caption = "Kane Williamson retires", source = "Getty"),
    )

    @Test
    fun `the image is asked for at the size it is drawn`() {
        val article = NewsArticleData.fromCricbuzzStory(story())!!
        assertEquals(
            "https://static.cricbuzz.com/a/img/v1/2048x1152/i1/c1005796/kane-williamson-retires.jpg",
            article.imageUrl,
        )
        assertEquals(
            "https://static.cricbuzz.com/a/img/v1/288x288/i1/c1005796/kane-williamson-retires.jpg",
            article.imageUrlAt(288, 288),
        )
    }

    @Test
    fun `a story with no image has no sized image either`() {
        val article = NewsArticleData.fromCricbuzzStory(story().copy(imageId = null))!!
        assertNull(article.imageUrlAt(288, 288))
    }

    @Test
    fun `the photo caption and credit are carried`() {
        val article = NewsArticleData.fromCricbuzzStory(story())!!
        assertEquals("Kane Williamson retires", article.imageCaption)
        assertEquals("Getty", article.imageCredit)
    }

    @Test
    fun `a story with a time has a date and an age`() {
        val article = NewsArticleData.fromCricbuzzStory(story())!!
        assertTrue(article.publishedTimeKnown)
        assertEquals("21 Sep 2026", article.publishedDate(ZoneOffset.UTC))
        assertTrue(article.byline.startsWith("Cricbuzz · "))
    }

    @Test
    fun `a story with no time claims neither a date nor an age`() {
        // It used to be stamped with the moment it was parsed, and read "Just now".
        val article = NewsArticleData.fromCricbuzzStory(story(pubTime = null))!!
        assertFalse(article.publishedTimeKnown)
        assertNull(article.publishedDate(ZoneOffset.UTC))
        assertEquals("Cricbuzz", article.byline)
    }

    @Test
    fun `a story with no source has no stray separator`() {
        val article = NewsArticleData.fromCricbuzzStory(story(pubTime = null, source = null))!!
        assertEquals("", article.byline)
    }
}
