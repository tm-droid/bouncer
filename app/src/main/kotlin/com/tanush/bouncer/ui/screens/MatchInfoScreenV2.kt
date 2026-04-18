package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchInfoScreenV2(
    match: CricketMatch,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Match Info", style = typography.titleMedium) },
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
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item { StatRowV2("Series", match.seriesId) }
            item { StatRowV2("Match", match.matchTitle) }
            item { StatRowV2("Format", match.matchType) }
            item { StatRowV2("Date", match.date) }
            item { StatRowV2("Time", match.matchTime) }
            item { StatRowV2("Venue", match.venue) }
            item { StatRowV2("Status", match.currentStatus.ifBlank { match.status }) }
            if (match.info.isNotBlank()) {
                item { StatRowV2("Info", match.info) }
            }
        }
    }
}
