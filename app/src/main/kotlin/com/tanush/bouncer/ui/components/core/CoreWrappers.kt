package com.tanush.bouncer.ui.components.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.tanush.bouncer.ui.components.CoilImage as BaseCoilImage
import com.tanush.bouncer.ui.components.MatchListSkeleton as BaseMatchListSkeleton
import com.tanush.bouncer.ui.components.MatchCardSkeleton as BaseMatchCardSkeleton

/**
 * Thin delegation wrappers to centralize imports under ui.components.core
 * These simply forward to the existing implementations to avoid duplication.
 */

@Composable
fun CoilImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    BaseCoilImage(model = model, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}

@Composable
fun MatchListSkeleton(count: Int = 5, modifier: Modifier = Modifier) {
    BaseMatchListSkeleton(count = count, modifier = modifier)
}

@Composable
fun MatchCardSkeleton(modifier: Modifier = Modifier) {
    BaseMatchCardSkeleton(modifier = modifier)
}
