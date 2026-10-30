package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.ui.components.HighlightClipCard
import com.tanush.bouncer.ui.components.HighlightPlayerDialog
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R

@Composable
fun HighlightsTabV2(
    highlights: List<HighlightEntity>,
    modifier: Modifier = Modifier
) {
    if (highlights.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyStateV2(
                icon = Icons.Outlined.Movie,
                title = stringResource(R.string.highlights_empty_title),
                subtitle = stringResource(R.string.highlights_empty_subtitle),
            )
        }
        return
    }

    val selectedHighlight = remember { mutableStateOf<HighlightEntity?>(null) }

    // A grid that scrolls down, not a row that scrolls sideways: a horizontal list inside the
    // tabs' horizontal pager fought it for every swipe.
    val spacing = BouncerThemeV2.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CLIP_WIDTH),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        items(highlights, key = { it.id }) { highlight ->
            Box(contentAlignment = Alignment.TopCenter) {
                HighlightClipCard(
                    highlight = highlight,
                    onClick = { selectedHighlight.value = it }
                )
            }
        }
    }

    selectedHighlight.value?.let { highlight ->
        HighlightPlayerDialog(
            highlight = highlight,
            onDismiss = { selectedHighlight.value = null }
        )
    }
}

/** The width [HighlightClipCard] draws itself at. */
private val CLIP_WIDTH = 180.dp
