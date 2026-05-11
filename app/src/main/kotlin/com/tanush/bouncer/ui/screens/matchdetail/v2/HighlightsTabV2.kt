package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.ui.components.HighlightClipCard
import com.tanush.bouncer.ui.components.HighlightPlayerDialog

@Composable
fun HighlightsTabV2(
    highlights: List<HighlightEntity>,
    modifier: Modifier = Modifier
) {
    val selectedHighlight = remember { mutableStateOf<HighlightEntity?>(null) }

    LazyRow(modifier = modifier) {
        items(highlights) { highlight ->
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
