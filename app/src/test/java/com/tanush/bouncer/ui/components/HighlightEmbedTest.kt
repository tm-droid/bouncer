package com.tanush.bouncer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightEmbedTest {

    // The three newest rows of the `highlights` table on 2026-10-04, both columns of each.
    @Test
    fun `reads the id from stored embed and watch urls`() {
        assertEquals("4dnu8cT6pvw", youtubeVideoId("https://www.youtube.com/embed/4dnu8cT6pvw"))
        assertEquals("4dnu8cT6pvw", youtubeVideoId("https://www.youtube.com/watch?v=4dnu8cT6pvw"))
        assertEquals("vHTnCmwKPnY", youtubeVideoId("https://www.youtube.com/embed/vHTnCmwKPnY"))
        assertEquals("XdAyWmqdqC0", youtubeVideoId("https://www.youtube.com/watch?v=XdAyWmqdqC0"))
    }

    @Test
    fun `reads the id from other youtube link shapes`() {
        assertEquals("4dnu8cT6pvw", youtubeVideoId("https://youtu.be/4dnu8cT6pvw?t=30"))
        assertEquals("4dnu8cT6pvw", youtubeVideoId("https://m.youtube.com/watch?feature=share&v=4dnu8cT6pvw&t=5"))
        assertEquals("4dnu8cT6pvw", youtubeVideoId("https://www.youtube.com/embed/4dnu8cT6pvw?autoplay=1"))
        assertEquals("4dnu8cT6pvw", youtubeVideoId("https://www.youtube-nocookie.com/embed/4dnu8cT6pvw"))
    }

    @Test
    fun `anything that is not a youtube video is null`() {
        assertNull(youtubeVideoId(null))
        assertNull(youtubeVideoId(""))
        assertNull(youtubeVideoId("https://streamable.com/e/abc123"))
        assertNull(youtubeVideoId("https://notyoutube.com/embed/4dnu8cT6pvw"))
        assertNull(youtubeVideoId("https://www.youtube.com/embed/"))
        assertNull(youtubeVideoId("https://www.youtube.com/embed/\"><script>"))
        assertNull(youtubeVideoId("https://www.youtube.com/channel/UC1234567890"))
    }

    @Test
    fun `the embed page identifies the app to youtube`() {
        val html = youtubeEmbedHtml("4dnu8cT6pvw")
        assertTrue(html.contains("https://www.youtube.com/embed/4dnu8cT6pvw?"))
        assertTrue(html.contains("origin=$EMBED_ORIGIN"))
        assertTrue(html.contains("referrerpolicy=\"strict-origin-when-cross-origin\""))
        assertTrue(html.contains("$EMBED_BRIDGE_NAME.onError"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the embed page refuses anything but a video id`() {
        youtubeEmbedHtml("\"></iframe><script>")
    }
}
