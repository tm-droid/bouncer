package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsHubScreenV2(
    onBackClick: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenAchievements: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Stats Hub", style = typography.titleMedium) },
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
            // Navigation cards
            item(key = "nav_header") {
                SectionHeaderV2("Explore")
            }
            item(key = "analytics") {
                SettingsItemV2(
                    icon = Icons.Default.Analytics,
                    title = "Analytics",
                    subtitle = "Deep dive into match data",
                    trailing = SettingsTrailing.Navigation,
                    onClick = onOpenAnalytics,
                )
            }
            item(key = "achievements") {
                SettingsItemV2(
                    icon = Icons.Default.SportsCricket,
                    title = "Achievements",
                    subtitle = "Unlock badges & rewards",
                    trailing = SettingsTrailing.Navigation,
                    onClick = onOpenAchievements,
                )
            }
        }
    }
}

