package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * LiveCommentaryPeek - Scrollable recent commentary overlay
 * Shows the most recent commentary entries in a compact scrollable view
 */
@Composable
fun LiveCommentaryPeek(
    commentaryItems: List<String>,
    modifier: Modifier = Modifier,
    maxVisibleItems: Int = 3
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val scrollState = rememberScrollState()
    val infiniteTransition = rememberInfiniteTransition(label = "commentaryPeek")

    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    LaunchedEffect(commentaryItems.size) {
        if (commentaryItems.isNotEmpty()) {
            scrollState.animateScrollTo(0)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = (maxVisibleItems * 60).dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface1.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    hierarchy.secondary.primary.copy(alpha = shimmer * 0.3f),
                    Color.Transparent
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CommentaryHeader(count = commentaryItems.size)
            Spacer(modifier = Modifier.height(12.dp))
            CommentaryList(items = commentaryItems, maxVisibleItems = maxVisibleItems)

            if (commentaryItems.size > maxVisibleItems) {
                Spacer(modifier = Modifier.height(8.dp))
                CommentaryMoreFooter(remaining = commentaryItems.size - maxVisibleItems)
            }
        }
    }
}

@Composable
private fun CommentaryHeader(count: Int) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel("COMMENTARY", hierarchy.secondary.primary, fontSize = 11)
        Text(
            text = "$count entries",
            color = colors.textMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CommentaryList(items: List<String>, maxVisibleItems: Int) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ((maxVisibleItems - 1) * 50).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.take(maxVisibleItems).forEachIndexed { index, item ->
            val alpha = 1f - (index * 0.2f).coerceAtMost(0.5f)

            Text(
                text = item,
                color = colors.textPrimary.copy(alpha = alpha),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            if (index < minOf(items.size - 1, maxVisibleItems - 1)) {
                HorizontalDivider(
                    color = colors.glassBorder.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun CommentaryMoreFooter(remaining: Int) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "+ $remaining more",
            color = colors.textMuted,
            fontSize = 10.sp
        )
    }
}
