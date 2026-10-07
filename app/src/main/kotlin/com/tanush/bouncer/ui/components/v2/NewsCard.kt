package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

private val THUMBNAIL_SIZE = 72.dp
private val IMAGE_CORNER = 8.dp

/**
 * A photograph on a neutral ground, cropped to its box.
 *
 * The ground is the placeholder and the failure state: a photo that is still loading, or never
 * arrives, leaves a block of the right size instead of a hole. The images used to be drawn with
 * no ground and, in the list, no crop, so a wide photo letterboxed inside its square.
 */
@Composable
fun NewsImage(url: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(IMAGE_CORNER)).background(BouncerThemeV2.colors.surfaceVariant)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * One story in the news list: headline and byline, thumbnail on the right.
 *
 * A row, not a card. A list of same-kind things is rows on one background split by dividers
 * (`MatchCardV2` is the reference); a card each made the News tab a stack of boxes.
 */
@Composable
fun NewsRowV2(
    headline: String,
    byline: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = typography.titleSmall,
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (byline.isNotBlank()) {
                Spacer(Modifier.height(spacing.xs))
                Text(text = byline, style = typography.bodySmall, color = colors.textSecondary)
            }
        }
        if (imageUrl != null) {
            Spacer(Modifier.width(spacing.md))
            NewsImage(imageUrl, Modifier.size(THUMBNAIL_SIZE))
        }
    }
}

/** A still block in the shape of [NewsRowV2], shown while the first headlines load. */
@Composable
fun NewsRowSkeleton(modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    val bar = Modifier.clip(RoundedCornerShape(4.dp)).background(colors.surfaceVariant)

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(bar.fillMaxWidth().height(14.dp))
            Spacer(Modifier.height(spacing.sm))
            Box(bar.fillMaxWidth(fraction = 0.7f).height(14.dp))
            Spacer(Modifier.height(spacing.sm))
            Box(bar.width(96.dp).height(10.dp))
        }
        Spacer(Modifier.width(spacing.md))
        Box(Modifier.size(THUMBNAIL_SIZE).clip(RoundedCornerShape(IMAGE_CORNER)).background(colors.surfaceVariant))
    }
}
