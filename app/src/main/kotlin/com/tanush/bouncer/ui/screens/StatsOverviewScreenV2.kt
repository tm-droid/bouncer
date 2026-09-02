package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsOverviewScreenV2(onBackClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.common_analytics), style = typography.titleMedium) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            // Top Impact Players
            item(key = "players_header") { SectionHeaderV2("Top Impact Players") }
            item(key = "players") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                ) {
                    Column(Modifier.padding(spacing.lg)) {
                        listOf(
                            Triple("1.", "V Kohli", "9.4"),
                            Triple("2.", "S Smith", "8.7"),
                            Triple("3.", "K Williamson", "8.2"),
                            Triple("4.", "J Root", "8.0"),
                            Triple("5.", "B Stokes", "7.8"),
                        ).forEach { (rank, name, rating) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("$rank $name", style = typography.bodyMedium, color = colors.onSurface)
                                Text("⭐ $rating", style = typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
                            }
                        }
                    }
                }
            }

            // Match Trends
            item(key = "trends_header") { SectionHeaderV2("Match Trends") }
            item(key = "trends") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TrendCard("Avg Score", "168.4", Modifier.weight(1f))
                    TrendCard("Wicket Rate", "7.2", Modifier.weight(1f))
                }
            }

            // Win Probability
            item(key = "winprob_header") { SectionHeaderV2("Win Probability") }
            item(key = "winprob") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                ) {
                    Column(Modifier.padding(spacing.lg)) {
                        ProbabilityBar("India", 0.62f)
                        Spacer(Modifier.height(spacing.sm))
                        ProbabilityBar("Australia", 0.38f)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Column(Modifier.padding(spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
            Text(label, style = typography.labelSmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun ProbabilityBar(team: String, probability: Float) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(team, style = typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(0.3f))
        LinearProgressIndicator(
            progress = { probability },
            modifier = Modifier.weight(0.5f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = colors.primaryText,
            trackColor = colors.surfaceVariant,
        )
        Text(
            "${(probability * 100).toInt()}%",
            style = typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            modifier = Modifier.weight(0.2f).padding(start = spacing.sm),
        )
    }
}
