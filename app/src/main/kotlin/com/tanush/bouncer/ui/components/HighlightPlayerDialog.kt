package com.tanush.bouncer.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tanush.bouncer.R
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel

private const val VIDEO_ASPECT = 16f / 9f

/**
 * Plays a highlight in YouTube's own player. The clips are YouTube embeds and nothing else can
 * play them: there is no stream to hand a native player, and YouTube's terms allow only its own.
 */
@Composable
fun HighlightPlayerDialog(
    highlight: HighlightEntity,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sourceUrl = highlight.embedUrl ?: highlight.fallbackUrl
    val videoId = remember(highlight.id) {
        youtubeVideoId(highlight.embedUrl) ?: youtubeVideoId(highlight.fallbackUrl)
    }

    if (videoId == null) {
        // Not a YouTube clip: there is no embed to wrap, so hand the page to whatever opens it.
        LaunchedEffect(highlight.id) {
            if (!sourceUrl.isNullOrBlank()) openExternally(context, sourceUrl)
            onDismiss()
        }
        return
    }

    val watchUrl = youtubeWatchUrl(videoId)
    var playerError by remember(highlight.id) { mutableStateOf<Int?>(null) }
    var fullscreen by remember(highlight.id) { mutableStateOf(false) }
    val colors = BouncerThemeV2.colors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        val player = remember(highlight.id) {
            EmbedPlayer(
                context = context,
                videoId = videoId,
                onError = { code ->
                    sentryBreadcrumb(
                        category = "highlights",
                        message = "YouTube player error",
                        level = SentryLevel.WARNING,
                        data = mapOf("code" to code, "videoId" to videoId)
                    )
                    playerError = code
                },
                onFullscreen = { fullscreen = it }
            )
        }
        DisposableEffect(player) { onDispose { player.destroy() } }
        BackHandler(enabled = fullscreen) { player.exitFullscreen() }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(if (fullscreen) Color.Black else colors.background)
        ) {
            if (!fullscreen) {
                PlayerTopBar(onClose = onDismiss, onOpenYoutube = { openExternally(context, watchUrl) })
            }
            PlayerSurface(
                player = player,
                failed = playerError != null,
                fullscreen = fullscreen,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun PlayerTopBar(onClose: () -> Unit, onOpenYoutube: () -> Unit) {
    val colors = BouncerThemeV2.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.highlight_player_close),
                tint = colors.onSurface
            )
        }
        TextButton(onClick = onOpenYoutube) {
            Text(stringResource(R.string.highlight_player_open_youtube), color = colors.primaryText)
        }
    }
}

@Composable
private fun PlayerSurface(
    player: EmbedPlayer,
    failed: Boolean,
    fullscreen: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        if (failed) {
            Text(
                text = stringResource(R.string.highlight_player_error),
                style = MaterialTheme.typography.bodyMedium,
                color = BouncerThemeV2.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            val wide = maxWidth / maxHeight > VIDEO_ASPECT
            AndroidView(
                factory = { player.container },
                modifier = if (fullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.aspectRatio(VIDEO_ASPECT, matchHeightConstraintsFirst = wide)
                }
            )
        }
    }
}

/**
 * The WebView and the frame it sits in. The frame is what Compose hosts, so the player's own
 * fullscreen view can be laid over the WebView without Compose re-creating either.
 */
private class EmbedPlayer(
    context: Context,
    videoId: String,
    onError: (Int) -> Unit,
    private val onFullscreen: (Boolean) -> Unit
) {
    val container = FrameLayout(context)
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled") // The page is our own wrapper around YouTube's player.
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        setBackgroundColor(android.graphics.Color.BLACK)
        addJavascriptInterface(Bridge(onError), EMBED_BRIDGE_NAME)
        webViewClient = object : WebViewClient() {
            // A tap on the YouTube logo or a suggested video leaves the embed: open it outside
            // rather than turning the dialog into a browser.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                openExternally(view.context, request.url.toString())
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView?.let { container.removeView(it) }
                customView = view
                customViewCallback = callback
                container.addView(view, MATCH, MATCH)
                onFullscreen(true)
            }

            override fun onHideCustomView() {
                customView?.let { container.removeView(it) }
                customView = null
                customViewCallback = null
                onFullscreen(false)
            }
        }
        loadDataWithBaseURL(EMBED_ORIGIN, youtubeEmbedHtml(videoId), "text/html", "utf-8", null)
    }

    init {
        container.addView(webView, MATCH, MATCH)
    }

    fun exitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        webView.webChromeClient?.onHideCustomView()
    }

    /** Without this the audio keeps playing after the dialog is gone. */
    fun destroy() {
        container.removeAllViews()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
    }

    private class Bridge(private val onError: (Int) -> Unit) {
        @JavascriptInterface
        fun onError(code: Int) = onError.invoke(code)
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}

private fun openExternally(context: Context, url: String) {
    if (!isOpenableWebUrl(url)) {
        sentryBreadcrumb(
            category = "highlights",
            message = "Refused to open a highlight link that is not a web address",
            level = SentryLevel.WARNING,
            data = mapOf("url" to url)
        )
        return
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        sentryBreadcrumb(
            category = "highlights",
            message = "Nothing on the device opens a highlight link",
            level = SentryLevel.WARNING,
            data = mapOf("url" to url, "error" to e.toString())
        )
    }
}
