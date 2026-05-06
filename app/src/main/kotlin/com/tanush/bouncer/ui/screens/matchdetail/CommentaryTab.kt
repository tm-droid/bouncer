package com.tanush.bouncer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BorderWidth
import com.tanush.bouncer.ui.theme.CornerRadius
import com.tanush.bouncer.ui.theme.GlassSurface
import com.tanush.bouncer.ui.theme.Gold400
import com.tanush.bouncer.ui.theme.IconSize
import com.tanush.bouncer.ui.theme.Spacing
import com.tanush.bouncer.ui.theme.StatusInfo
import com.tanush.bouncer.ui.theme.StatusSuccess
import com.tanush.bouncer.ui.theme.TextMuted
import com.tanush.bouncer.ui.theme.TextPrimary

private fun parseBoldTags(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("<b>(.*?)</b>")
        var lastIndex = 0

        regex.findAll(text).forEach { match ->
            // Add text before the bold tag
            append(text.substring(lastIndex, match.range.first))

            // Add bold text
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }

            lastIndex = match.range.last + 1
        }

        // Add remaining text after the last bold tag
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private data class CommentaryAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: androidx.compose.ui.graphics.Color,
    val onClick: () -> Unit
)

@Composable
fun CommentaryTab_Extracted(
    commentary: List<String>,
    recentBalls: List<String>,
    onOpenCommentary: () -> Unit,
    onOpenScorecard: () -> Unit,
    onOpenAnalysis: () -> Unit
) {
    val actions = listOf(
        CommentaryAction("Open Commentary", Icons.Outlined.Campaign, StatusInfo, onOpenCommentary),
        CommentaryAction("Full Scorecard", Icons.Outlined.TableChart, Gold400, onOpenScorecard),
        CommentaryAction("Match Analysis", Icons.Outlined.Analytics, StatusSuccess, onOpenAnalysis)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "COMMENTARY",
                color = Gold400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                role = Role.Button
                                contentDescription = action.label
                            },
                        shape = RoundedCornerShape(CornerRadius.md),
                        color = GlassSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            BorderWidth.hairline,
                            action.accent.copy(alpha = 0.35f)
                        ),
                        onClick = action.onClick
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = action.accent,
                                modifier = Modifier.size(IconSize.sm)
                            )
                            Text(
                                text = action.label,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerRadius.md))
                    .background(GlassSurface)
                    .border(BorderWidth.hairline, Gold400.copy(alpha = 0.18f), RoundedCornerShape(CornerRadius.md))
                    .padding(Spacing.md)
            ) {
                Text(
                    text = commentary.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Waiting for latest commentary update...",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        if (recentBalls.isNotEmpty()) {
            item {
                EliteRecentTelemetry(recentBalls)
            }
        }

        item {
            val feed = commentary.take(6)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerRadius.md))
                    .background(GlassSurface)
                    .border(BorderWidth.hairline, TextMuted.copy(alpha = 0.25f), RoundedCornerShape(CornerRadius.md))
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Recent updates",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (feed.isEmpty()) {
                    Text("Commentary not available yet.", color = TextMuted, fontSize = 12.sp)
                } else {
                    feed.forEachIndexed { index, line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${index + 1}.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = parseBoldTags(line),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}
