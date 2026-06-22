package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.viewmodels.PointsTableViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.PointsTableEntry
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.ShimmerTableRows
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Points table screen showing tournament standings with team stats.
 */
@Composable
fun PointsTableScreen(
    seriesId: String,
    onBackClick: () -> Unit,
    viewModel: PointsTableViewModel = viewModel(),
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing

    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }

    Scaffold(
        topBar = {
            BouncerTopBarV2(title = "Points Table", showBack = true, onBack = onBackClick)
        },
        containerColor = colors.background,
    ) { innerPadding ->
        when {
            isLoading -> {
                ShimmerTableRows(modifier = Modifier.fillMaxSize().padding(innerPadding))
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyStateV2(
                        icon = Icons.Outlined.TableChart,
                        title = "No standings available",
                        subtitle = error ?: "",
                    )
                }
            }
            else -> {
                val showTiedNR = entries.any { it.tied > 0 || it.noResult > 0 }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(vertical = spacing.md),
                ) {
                    // Header row
                    item(key = "header") {
                        PointsTableHeader(showTiedNR = showTiedNR)
                    }

                    itemsIndexed(entries, key = { _, e -> e.teamName }) { index, entry ->
                        PointsTableRow(entry = entry, isAlternate = index % 2 == 1, showTiedNR = showTiedNR)
                        if (index < entries.lastIndex) {
                            HorizontalDivider(color = colors.divider)
                        }
                    }

                    item(key = "bottom") { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PointsTableHeader(showTiedNR: Boolean = false) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val headerStyle = typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.textSecondary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = headerStyle, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        Text("Team", style = headerStyle, modifier = Modifier.weight(1f))
        Text("M", style = headerStyle, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        Text("W", style = headerStyle, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        Text("L", style = headerStyle, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        if (showTiedNR) {
            Text("T", style = headerStyle, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
            Text("NR", style = headerStyle, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        }
        Text("NRR", style = headerStyle, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
        Text("Pts", style = headerStyle, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun PointsTableRow(entry: PointsTableEntry, isAlternate: Boolean, showTiedNR: Boolean = false) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val cellStyle = typography.bodySmall.copy(color = colors.onSurface)
    val qualifiedColor = colors.win

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isAlternate) Modifier.background(colors.surfaceVariant.copy(alpha = 0.3f))
                else Modifier
            )
            .then(
                if (entry.qualified) Modifier.drawBehind {
                    drawLine(
                        color = qualifiedColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Butt,
                    )
                } else Modifier
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${entry.position}",
                style = cellStyle.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.teamName,
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = colors.onSurface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${entry.matches}", style = cellStyle,
                modifier = Modifier.width(28.dp), textAlign = TextAlign.Center,
            )
            Text(
                "${entry.won}", style = cellStyle.copy(color = colors.win),
                modifier = Modifier.width(28.dp), textAlign = TextAlign.Center,
            )
            Text(
                "${entry.lost}", style = cellStyle.copy(color = colors.error),
                modifier = Modifier.width(28.dp), textAlign = TextAlign.Center,
            )
            if (showTiedNR) {
                Text(
                    "${entry.tied}", style = cellStyle,
                    modifier = Modifier.width(24.dp), textAlign = TextAlign.Center,
                )
                Text(
                    "${entry.noResult}", style = cellStyle,
                    modifier = Modifier.width(28.dp), textAlign = TextAlign.Center,
                )
            }
            Text(
                String.format("%.3f", entry.netRunRate),
                style = cellStyle.copy(
                    color = if (entry.netRunRate >= 0) colors.win else colors.error
                ),
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End,
            )
            Text(
                "${entry.points}",
                style = cellStyle.copy(fontWeight = FontWeight.Bold, color = colors.primary),
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End,
            )
        }

        // Recent form badges
        if (entry.recentForm.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.padding(start = 24.dp + spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                entry.recentForm.takeLast(5).forEach { result ->
                    val (bg, fg) = when (result.uppercase()) {
                        "W" -> colors.win.copy(alpha = 0.18f) to colors.win
                        "L" -> colors.error.copy(alpha = 0.18f) to colors.error
                        else -> colors.surfaceVariant to colors.textSecondary
                    }
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = result.take(1).uppercase(),
                            style = typography.labelSmall.copy(fontSize = 8.sp),
                            color = fg,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
