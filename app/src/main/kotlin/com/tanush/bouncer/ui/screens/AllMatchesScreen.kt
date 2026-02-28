package com.tanush.bouncer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.ui.components.PremiumMatchListCard
import com.tanush.bouncer.ui.components.StatusFilterChip
import com.tanush.bouncer.ui.components.MatchCardSkeleton
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.viewmodels.LiveActivityViewModel

/**
 * AllMatchesScreen - Full list of all cricket matches with filtering.
 * Clean, organized view for browsing matches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllMatchesScreen(
    viewModel: LiveActivityViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val currentMatches by viewModel.currentMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    Scaffold(
        containerColor = BlackBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "All Matches",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${currentMatches.size} matches available",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshMatches() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = ShinyGoldDeep
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BlackBackground.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filter Chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(listOf("Live", "Upcoming", "Recent")) { status ->
                        StatusFilterChip(
                            text = status,
                            isSelected = selectedStatus == status,
                            onClick = { viewModel.setAndFetchMatches(status) }
                        )
                    }
                }
            }
            
            // Match count header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedStatus Matches".uppercase(),
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${currentMatches.size} results",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            
            // Match list
            if (isLoading && currentMatches.isEmpty()) {
                items(5) {
                    MatchCardSkeleton()
                }
            } else if (currentMatches.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.SportsCricket,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                "No matches found",
                                color = TextSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Pull down to refresh or try a different filter",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(currentMatches, key = { it.id }) { match ->
                    PremiumMatchListCard(
                        match = match,
                        onClick = { /* Navigate to match detail */ }
                    )
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
