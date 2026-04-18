package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.ThemeModeV2

/**
 * AppearanceScreenV2 — Light / Dark / System theme picker with live preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreenV2(
    onBackClick: () -> Unit,
    currentMode: ThemeModeV2,
    onModeChanged: (ThemeModeV2) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Appearance", style = typography.titleMedium) },
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
            item(key = "theme_label") {
                Text("Theme", style = typography.titleMedium, color = colors.onSurface)
            }
            item(key = "theme_cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    ThemeOptionCard(
                        icon = Icons.Default.LightMode,
                        label = "Light",
                        isSelected = currentMode == ThemeModeV2.Light,
                        onClick = { onModeChanged(ThemeModeV2.Light) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeOptionCard(
                        icon = Icons.Default.DarkMode,
                        label = "Dark",
                        isSelected = currentMode == ThemeModeV2.Dark,
                        onClick = { onModeChanged(ThemeModeV2.Dark) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeOptionCard(
                        icon = Icons.Default.PhoneAndroid,
                        label = "System",
                        isSelected = currentMode == ThemeModeV2.System,
                        onClick = { onModeChanged(ThemeModeV2.System) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Card(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = BouncerThemeV2.shapes.medium,
        border = if (isSelected) BorderStroke(2.dp, colors.primary) else null,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(spacing.md)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = label, tint = if (isSelected) colors.primary else colors.textSecondary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(spacing.sm))
                Text(label, style = typography.labelMedium, color = colors.onSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp).align(Alignment.TopEnd),
                )
            }
        }
    }
}
