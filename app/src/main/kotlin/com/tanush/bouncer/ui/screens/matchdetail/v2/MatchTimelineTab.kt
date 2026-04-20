package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.TimelineEvent
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.TimelineEventExtractor

@Composable
fun MatchTimelineTab(
    commentary: List<String>,
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing

    val events = remember(commentary) {
        TimelineEventExtractor.extract(commentary)
    }

    if (events.isEmpty()) {
        EmptyStateV2(
            title = "No key events yet",
            subtitle = "Timeline will populate as the match progresses",
        )
        return
    }

    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Wickets", "Boundaries", "Milestones")

    val filteredEvents = remember(events, selectedFilter) {
        when (selectedFilter) {
            "Wickets" -> events.filter { it.type == TimelineEventType.WICKET }
            "Boundaries" -> events.filter { it.type == TimelineEventType.FOUR || it.type == TimelineEventType.SIX }
            "Milestones" -> events.filter { it.type == TimelineEventType.MILESTONE }
            else -> events.filter { it.type != TimelineEventType.OVER_END }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterChipRowV2(
            items = filterOptions,
            selectedItem = selectedFilter,
            onItemSelected = { selectedFilter = it },
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
        )

        if (filteredEvents.isEmpty()) {
            EmptyStateV2(
                title = "No events found",
                subtitle = "Try a different filter",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
            ) {
                items(filteredEvents.size, key = { "event_$it" }) { index ->
                    val event = filteredEvents[index]
                    val isLast = index == filteredEvents.lastIndex
                    TimelineEventRow(event = event, isLast = isLast)
                }
                item { Spacer(Modifier.height(spacing.xxl)) }
            }
        }
    }
}

@Composable
private fun TimelineEventRow(event: TimelineEvent, isLast: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val dotColor = when (event.type) {
        TimelineEventType.WICKET -> colors.live
        TimelineEventType.SIX -> colors.primary
        TimelineEventType.FOUR -> colors.win
        TimelineEventType.MILESTONE -> colors.primaryContainer
        TimelineEventType.OVER_END -> colors.divider
        else -> colors.textSecondary
    }

    val dotSize = 12.dp
    val lineWidth = 2.dp
    val timelineColumnWidth = 32.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
    ) {
        // Left column: dot + connecting line
        Box(
            modifier = Modifier
                .width(timelineColumnWidth)
                .height(72.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Connecting line below the dot
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(lineWidth)
                        .fillMaxSize()
                        .offset(y = dotSize / 2)
                        .background(colors.divider),
                )
            }
            // Dot
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
                    .align(Alignment.TopCenter),
            )
        }

        Spacer(Modifier.width(spacing.sm))

        // Right column: event content card
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = spacing.xs),
            shape = RoundedCornerShape(spacing.sm),
            color = colors.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = dotColor,
                        modifier = Modifier.weight(1f),
                    )
                    if (event.over > 0f) {
                        Text(
                            text = "Ov ${event.over}",
                            style = typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = event.description,
                    style = typography.bodySmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
