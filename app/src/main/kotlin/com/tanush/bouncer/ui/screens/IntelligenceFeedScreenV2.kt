package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.LoadingState

private enum class AlertTypeV2 { WICKET, MILESTONE, INFO, WARNING }

private data class ActivityAlertV2(
    val title: String, val subtitle: String, val time: String, val type: AlertTypeV2,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceFeedScreenV2(
    match: CricketMatch? = null,
    liveActivityViewModel: LiveActivityViewModel? = null,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    // Build alerts from ViewModel probability events
    val probabilityEvents by liveActivityViewModel?.probabilityEvents?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyMap()) }

    val loadingState by liveActivityViewModel?.loadingState?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(LoadingState.Idle) }

    val alerts by remember(probabilityEvents) {
        derivedStateOf {
            buildList {
                probabilityEvents.forEach { (matchId, events) ->
                    events.takeLast(10).forEachIndexed { i, event ->
                        add(
                            ActivityAlertV2(
                                title = event.description,
                                subtitle = "Match: $matchId",
                                time = "${10 - i}m ago",
                                type = when {
                                    event.description.contains("wicket", true) -> AlertTypeV2.WICKET
                                    event.description.contains("50") || event.description.contains("100") || event.description.contains("milestone", true) -> AlertTypeV2.MILESTONE
                                    event.description.contains("boundary", true) || event.description.contains("six", true) -> AlertTypeV2.WARNING
                                    else -> AlertTypeV2.INFO
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Activity Feed", style = typography.titleMedium) },
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
        if (alerts.isEmpty()) {
            val errorState = loadingState as? LoadingState.Error
            if (errorState != null) {
                EmptyStateV2(
                    title = "Couldn't load activity",
                    subtitle = errorState.message,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onRetry = { liveActivityViewModel?.refreshMatches() },
                )
            } else {
                EmptyStateV2(
                    title = "No Activity Yet",
                    subtitle = "Activity from live matches will appear here",
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(alerts, key = { "${it.subtitle}_${it.type}_${it.title}" }) { alert ->
                    AlertCard(alert)
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: ActivityAlertV2) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val borderColor = when (alert.type) {
        AlertTypeV2.WICKET -> colors.live
        AlertTypeV2.MILESTONE -> colors.win
        AlertTypeV2.WARNING -> colors.primary
        AlertTypeV2.INFO -> colors.textSecondary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colored left border
            Spacer(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(borderColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
            )
            Column(
                modifier = Modifier.weight(1f).padding(spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(alert.title, style = typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface, modifier = Modifier.weight(1f))
                    Text(alert.time, style = typography.labelSmall, color = colors.textSecondary)
                }
                if (alert.subtitle.isNotEmpty()) {
                    Text(alert.subtitle, style = typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
    }
}
