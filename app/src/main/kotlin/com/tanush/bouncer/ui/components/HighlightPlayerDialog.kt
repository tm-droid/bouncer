package com.tanush.bouncer.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import com.tanush.bouncer.data.local.entities.HighlightEntity

@Composable
fun HighlightPlayerDialog(
    highlight: HighlightEntity,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val videoUrl = highlight.embedUrl ?: highlight.fallbackUrl
    if (videoUrl.isNullOrBlank()) {
        onDismiss()
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = WebViewClient()
                        loadUrl(videoUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(highlight.id) {
                onDispose {
                    // WebView cleanup happens implicitly via the Box composable disposal
                }
            }
        }
    }
}
