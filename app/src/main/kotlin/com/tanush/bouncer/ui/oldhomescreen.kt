/* REFERENCE ONLY - DO NOT BUILD
package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.ui.components.FilterDropdown
import com.tanush.bouncer.ui.components.StatusChip
import com.tanush.bouncer.ui.components.SectionDivider
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stars

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: LiveActivityViewModel = viewModel(),
    onMatchClick: (CricketMatch) -> Unit,
    onMcpDemoClick: () -> Unit = {},
    onDevToolsClick: () -> Unit = {}
) {
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val selectedCompetitionType by viewModel.selectedCompetitionType.collectAsState()
    val selectedGender by viewModel.selectedGender.collectAsState()
    val currentMatches by viewModel.currentMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val statusChips = listOf("Live", "Upcoming", "Recent")
    val competitionTypes = listOf("All", "International", "League", "Domestic")
    val genderTypes = listOf("All", "Men", "Women")

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshMatches() }
    )

    val infiniteTransition = rememberInfiniteTransition(label = "bgGlow")
    val glowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowOffset"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BlackBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Animated background glows for premium feel
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ShinyGoldDeep.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        radius = size.width * 1.5f,
                        center = Offset(size.width * (0.2f + glowOffset * 0.1f), size.height * (0.1f + glowOffset * 0.05f))
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                LiveBlue.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        ),
                        radius = size.width,
                        center = Offset(size.width * (0.8f - glowOffset * 0.1f), size.height * (0.6f + glowOffset * 0.1f))
                    )
                }
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp), // Extra space for bottom nav
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Premium Header
                item {
                    com.tanush.bouncer.ui.GoldHeader(
                        matchCount = currentMatches.size,
                        selectedStatus = selectedStatus
                    )
                }

                // Quick Actions Section - More "Premium" look
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "QUICK ACTIONS",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickActionButton(
                                text = "Insights",
                                icon = Icons.Default.Stars,
                                onClick = onMcpDemoClick,
                                modifier = Modifier.weight(1f),
                                isPrimary = true
                            )
                            QuickActionButton(
                                text = "Admin",
                                icon = Icons.Default.Settings,
                                onClick = onDevToolsClick,
                                modifier = Modifier.weight(1f),
                                isPrimary = false
                            )
                        }
                    }
                }

                // Filtering Section with Glassmorphism feel
                item {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Status Selector
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(statusChips) { chip ->
                                StatusChip(
                                    text = chip,
                                    isSelected = selectedStatus == chip,
                                    onClick = { viewModel.setAndFetchMatches(chip) }
                                )
                            }
                        }

                        // Detailed Filters
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                FilterDropdown(
                                    label = "Match Type",
                                    options = competitionTypes,
                                    selectedOption = selectedCompetitionType,
                                    onOptionSelected = { viewModel.setCompetitionType(it) }
                                )
                            }
                            item {
                                FilterDropdown(
                                    label = "Category",
                                    options = genderTypes,
                                    selectedOption = selectedGender,
                                    onOptionSelected = { viewModel.setGender(it) }
                                )
                            }
                        }
                    }
                }

                // Section Divider
                if (currentMatches.isNotEmpty() || isLoading) {
                    item {
                        SectionDivider(
                            title = (selectedStatus + " matches").uppercase(),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                        )
                    }
                }

                // Match Cards / Loading State
                if (isLoading && currentMatches.isEmpty()) {
                    items(3) {
                        MatchCardSkeleton()
                    }
                } else {
                    items(currentMatches) { match ->
                        MatchCard(
                            match = match,
                            onClick = { onMatchClick(match) }
                        )
                    }
                }

                // Empty State
                if (!isLoading && currentMatches.isEmpty()) {
                    item {
                        com.tanush.bouncer.ui.EmptyMatchesState(selectedStatus = selectedStatus)
                    }
                }

                // Footer
                item {
                    com.tanush.bouncer.ui.AppFooter()
                }
            }

            // Pull Refresh Indicator
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = Color.White.copy(alpha = 0.05f),
                contentColor = ShinyGoldDeep
            )
        }
    }
}

@Composable
fun QuickActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isPrimary) Color.Transparent else Color.White.copy(alpha = 0.08f).copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (isPrimary) ShinyGoldDeep.copy(alpha = 0.5f) else CardBorder.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPrimary) Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                ShinyGoldDeep.copy(alpha = 0.15f),
                                ShinyGoldMid.copy(alpha = 0.05f)
                            )
                        )
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isPrimary) ShinyGoldMid else TextSecondary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrimary) ShinyGoldMid else TextPrimary,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
*/
