package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Series detail screen with tabs for Schedule, Points Table, etc.
 */
@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onPointsTableClick: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography

    val tabs = listOf("Schedule", "Points Table", "Stats")
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            BouncerTopBarV2(title = "Series Detail", showBack = true, onBack = onBackClick)
        },
        containerColor = colors.background,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.surface,
                contentColor = colors.primary,
                edgePadding = 16.dp,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index == 1) {
                                onPointsTableClick(seriesId)
                            }
                        },
                        text = {
                            Text(
                                title,
                                style = typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) colors.primary else colors.textSecondary,
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> SeriesTabPlaceholder(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "Schedule coming soon",
                    subtitle = "Fixtures for this series will appear here.",
                )
                2 -> SeriesTabPlaceholder(
                    icon = Icons.Outlined.BarChart,
                    title = "Stats coming soon",
                    subtitle = "Series statistics will appear here.",
                )
            }
        }
    }
}

@Composable
private fun SeriesTabPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyStateV2(icon = icon, title = title, subtitle = subtitle)
    }
}
