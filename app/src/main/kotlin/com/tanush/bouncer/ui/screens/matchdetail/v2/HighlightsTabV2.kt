package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.ui.components.HighlightClipCard
import com.tanush.bouncer.ui.components.HighlightPlayerDialog
import com.tanush.bouncer.ui.components.v2.EmptyStateV2

@Composable
fun HighlightsTabV2(
    highlights: List<HighlightEntity>,
    modifier: Modifier = Modifier
) {
    if (highlights.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyStateV2(
                icon = Icons.Outlined.Movie,
                title = "No highlights yet",
                subtitle = "Clips appear here once key moments are available for this match.",
            )
        }
        return
    }

    val selectedHighlight = remember { mutableStateOf<HighlightEntity?>(null) }

    LazyRow(modifier = modifier) {
        items(highlights, key = { it.id }) { highlight ->
            HighlightClipCard(
                highlight = highlight,
                onClick = { selectedHighlight.value = it }
            )
        }
    }

    selectedHighlight.value?.let { highlight ->
        HighlightPlayerDialog(
            highlight = highlight,
            onDismiss = { selectedHighlight.value = null }
        )
    }
}
