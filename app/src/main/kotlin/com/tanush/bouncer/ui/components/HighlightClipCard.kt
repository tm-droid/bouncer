package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

@Composable
fun HighlightClipCard(
    highlight: HighlightEntity,
    onClick: (HighlightEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = BouncerThemeV2.spacing
    val colors = BouncerThemeV2.colors
    val errorLogged = remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(180.dp)
            .clickable { onClick(highlight) },
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        GlassCard(modifier = Modifier.width(180.dp)) {
            SubcomposeAsyncImage(
                model = highlight.imgUrl,
                contentDescription = highlight.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentScale = ContentScale.Crop,
            ) {
                when (val state = painter.state) {
                    is AsyncImagePainter.State.Error -> {
                        if (!errorLogged.value) {
                            errorLogged.value = true
                            io.sentry.Sentry.captureMessage(
                                "Highlight image URL failed: ${highlight.imgUrl}",
                                io.sentry.SentryLevel.WARNING
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(colors.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = colors.textSecondary,
                            )
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }

        Text(
            text = highlight.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = spacing.xs)
        )

        Text(
            text = highlight.channel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = spacing.xs)
        )
    }
}
