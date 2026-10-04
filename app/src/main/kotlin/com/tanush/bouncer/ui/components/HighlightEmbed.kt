package com.tanush.bouncer.ui.components

/**
 * The origin the YouTube embed is loaded under. YouTube identifies an embedding app by the
 * request's Referer and answers "error 153" when there is none — which is what a bare
 * `WebView.loadUrl(embedUrl)` sends. For a WebView client the documented form is the application
 * id as an https origin.
 */
const val EMBED_ORIGIN = "https://com.tanush.bouncer"

/** Name of the JS interface the embed page reports player errors to. */
const val EMBED_BRIDGE_NAME = "BouncerPlayer"

private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
private val YOUTUBE_HOSTS = setOf("youtube.com", "youtube-nocookie.com")
private const val SHORT_HOST = "youtu.be"
private val URL_PARTS = Regex("^https?://([^/?#]+)(/[^?#]*)?(?:\\?([^#]*))?")

/** The video id of a YouTube embed, watch or short link; null for anything else. */
fun youtubeVideoId(url: String?): String? {
    val match = URL_PARTS.find(url?.trim().orEmpty()) ?: return null
    val host = match.groupValues[1].lowercase().removePrefix("www.").removePrefix("m.")
    val segments = match.groupValues[2].split('/').filter { it.isNotEmpty() }
    val candidate = when {
        host == SHORT_HOST -> segments.firstOrNull()
        host !in YOUTUBE_HOSTS -> null
        segments.firstOrNull() in setOf("embed", "shorts", "live") -> segments.getOrNull(1)
        segments.firstOrNull() == "watch" -> match.groupValues[3].split('&')
            .firstOrNull { it.startsWith("v=") }
            ?.removePrefix("v=")
        else -> null
    }
    return candidate?.takeIf { VIDEO_ID.matches(it) }
}

fun youtubeWatchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

/**
 * A page holding YouTube's own player in an iframe. It must be loaded with
 * `loadDataWithBaseURL(EMBED_ORIGIN, …)`: the base URL is what gives the iframe request a Referer.
 * The iframe is static so the video still shows if the IFrame API script fails to load; the script
 * only attaches an error listener.
 */
fun youtubeEmbedHtml(videoId: String): String {
    require(VIDEO_ID.matches(videoId)) { "Not a YouTube video id" }
    val src = "https://www.youtube.com/embed/$videoId" +
        "?playsinline=1&rel=0&enablejsapi=1&origin=$EMBED_ORIGIN"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style>
        </head>
        <body>
        <iframe id="player" src="$src"
            referrerpolicy="strict-origin-when-cross-origin"
            allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
            allowfullscreen></iframe>
        <script>
        function onYouTubeIframeAPIReady() {
            new YT.Player('player', { events: { onError: function (e) { $EMBED_BRIDGE_NAME.onError(e.data); } } });
        }
        </script>
        <script src="https://www.youtube.com/iframe_api"></script>
        </body>
        </html>
    """.trimIndent()
}
