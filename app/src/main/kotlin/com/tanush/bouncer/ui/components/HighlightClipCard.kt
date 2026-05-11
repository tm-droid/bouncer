package com.tanush.bouncer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.ui.theme.spacing

@Composable
fun HighlightClipCard(
    highlight: HighlightEntity,
    onClick: (HighlightEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(180.dp)
            .clickable { onClick(highlight) },
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        GlassCard(
            modifier = Modifier.width(180.dp)
        ) {
            AsyncImage(
                model = highlight.imgUrl,
                contentDescription = highlight.title,
                modifier = Modifier
                    .width(180.dp)
                    .width(100.dp),
                contentScale = ContentScale.Crop,
                onError = {
                    // Log dead URL to Sentry
                    io.sentry.Sentry.captureMessage(
                        "Highlight image URL failed: ${highlight.imgUrl}",
                        io.sentry.SentryLevel.WARNING
                    )
                }
            )
        }

        Text(
            text = highlight.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = highlight.channel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
