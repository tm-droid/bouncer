package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.Achievement
import com.tanush.bouncer.models.AchievementKind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

private val categoryLabels = listOf("All", "Batting", "Bowling", "Team", "Special")
private val categoryPrefixes = mapOf(
    "Batting" to listOf("RUNS_", "FASTEST_", "MOST_SIXES"),
    "Bowling" to listOf("WICKETS_", "MAIDEN_", "HAT_TRICK", "BEST_ECONOMY"),
    "Team" to listOf("TEAM_", "SUCCESSFUL_"),
    "Special" to listOf("PLAYER_OF_MATCH", "RECORD_PARTNERSHIP"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreenV2(
    unlockedAchievements: Set<AchievementKind>,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val allAchievements = Achievement.all
    var selectedCategory by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(categoryLabels.first()) }
    val filtered = if (selectedCategory == "All") allAchievements
    else {
        val prefixes = categoryPrefixes[selectedCategory] ?: emptyList()
        allAchievements.filter { a -> prefixes.any { p -> a.id.name.startsWith(p) } }
    }

    val unlockedCount = allAchievements.count { it.id in unlockedAchievements }
    val totalCount = allAchievements.size
    val progress = if (totalCount > 0) unlockedCount.toFloat() / totalCount else 0f

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Achievements", style = typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )
        },
        containerColor = colors.background,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // Progress header
            item(span = { GridItemSpan(2) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                ) {
                    Column(Modifier.padding(spacing.lg)) {
                        Text(
                            "🏅 $unlockedCount / $totalCount Achievements",
                            style = typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text("${(progress * 100).toInt()}% complete", style = typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }

            // Category filters
            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(spacing.sm))
                FilterChipRowV2(
                    items = categoryLabels,
                    selectedItem = selectedCategory,
                    onItemSelected = { selectedCategory = it },
                )
                Spacer(Modifier.height(spacing.sm))
            }

            items(filtered) { achievement ->
                val isUnlocked = achievement.id in unlockedAchievements
                AchievementCard(
                    title = achievement.title,
                    description = achievement.description,
                    isUnlocked = isUnlocked,
                )
            }
        }
    }
}

@Composable
private fun AchievementCard(title: String, description: String, isUnlocked: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) colors.surface else colors.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) BouncerThemeV2.elevation.card else 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (isUnlocked) Icons.Default.Check else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isUnlocked) colors.win else colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.sm))
            Text(title, style = typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
            Text(description, style = typography.labelSmall, color = colors.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = if (isUnlocked) "✓ Done" else "Locked",
                style = typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) colors.win else colors.textSecondary,
            )
        }
    }
}
