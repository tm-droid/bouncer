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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
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
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

private data class TrophyItem(
    val name: String, val requirement: String, val tier: String, val isLocked: Boolean,
)

private val sampleTrophies = listOf(
    TrophyItem("Centurion", "Score 100 runs", "GOLD", false),
    TrophyItem("Hat-Trick", "3 wickets in 3 balls", "SILVER", false),
    TrophyItem("Five-Wicket Haul", "Take 5 wickets", "BRONZE", false),
    TrophyItem("Double Century", "Score 200 runs", "GOLD", true),
    TrophyItem("Golden Duck", "Out for 0", "BRONZE", true),
    TrophyItem("Match Winner", "Win a close game", "SILVER", true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophyRoomScreenV2(onBackClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val unlocked = sampleTrophies.count { !it.isLocked }
    val total = sampleTrophies.size
    val progress = unlocked.toFloat() / total

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Trophy Room", style = typography.titleMedium) },
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
            // Progress header (full-span)
            item(span = { GridItemSpan(2) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                ) {
                    Column(Modifier.padding(spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, Modifier.size(28.dp), tint = colors.primary)
                            Spacer(Modifier.padding(start = spacing.sm))
                            Text("$unlocked / $total Trophies", style = typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        }
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
                Spacer(Modifier.height(spacing.md))
            }

            items(sampleTrophies) { trophy ->
                TrophyCard(trophy)
            }
        }
    }
}

@Composable
private fun TrophyCard(trophy: TrophyItem) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (trophy.isLocked) colors.surfaceVariant else colors.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (trophy.isLocked) 0.dp else BouncerThemeV2.elevation.card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (trophy.isLocked) Icons.Default.Lock else Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (trophy.isLocked) colors.textSecondary else colors.primary,
            )
            Spacer(Modifier.height(spacing.sm))
            Text(trophy.name, style = typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
            Text(trophy.requirement, style = typography.labelSmall, color = colors.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = if (trophy.isLocked) "LOCKED" else trophy.tier,
                style = typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (trophy.isLocked) colors.textSecondary else colors.primary,
                modifier = Modifier
                    .background(
                        if (trophy.isLocked) colors.divider else colors.primaryContainer,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
