package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.theme.BackgroundSkinType
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.ThemeVariant

@Composable
fun AppearanceScreen(
    onBackClick: () -> Unit,
    currentThemeVariant: ThemeVariant,
    currentBackgroundSkin: BackgroundSkinType,
    onThemeVariantChanged: (ThemeVariant) -> Unit,
    onBackgroundSkinChanged: (BackgroundSkinType) -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenHorizontal, vertical = dimens.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBackClick,
                    contentDescription = "Back",
                    useGoldTint = false
                )
                Spacer(modifier = Modifier.width(dimens.md))
                Column {
                    Text(
                        text = "Appearance",
                        style = typography.headingLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Theme variants and background skins",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dimens.screenHorizontal,
                    end = dimens.screenHorizontal,
                    top = dimens.sm,
                    bottom = dimens.bottomNavHeight + dimens.xl
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.lg)
            ) {
                item {
                    SectionHeader(
                        title = "Theme Variant",
                        subtitle = "Switch between Dark, Midnight, and AMOLED."
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.md)) {
                        items(ThemeVariant.entries) { variant ->
                            ThemeVariantCard(
                                variant = variant,
                                selected = currentThemeVariant == variant,
                                onClick = { onThemeVariantChanged(variant) }
                            )
                        }
                    }
                }
                item {
                    SectionHeader(
                        title = "Background Skin",
                        subtitle = "Pick a visual backdrop for the whole app."
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.md)) {
                        items(BackgroundSkinType.entries) { skin ->
                            BackgroundSkinCard(
                                skin = skin,
                                selected = currentBackgroundSkin == skin,
                                onClick = { onBackgroundSkinChanged(skin) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = typography.headingMedium,
            color = colors.textPrimary
        )
        Text(
            text = subtitle,
            style = typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ThemeVariantCard(
    variant: ThemeVariant,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val accent = if (selected) colors.goldPrimary else colors.textSecondary

    GlassCard(
        modifier = Modifier
            .width(200.dp)
            .height(108.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        useGoldBorder = selected
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = variant.displayName,
                style = typography.headingSmall.copy(color = accent)
            )
            Text(
                text = variant.description,
                style = typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BackgroundSkinCard(
    skin: BackgroundSkinType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val accent = if (selected) colors.goldPrimary else colors.textSecondary

    GlassCard(
        modifier = Modifier
            .width(240.dp)
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        useGoldBorder = selected
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = skin.displayName,
                    style = typography.headingSmall.copy(color = accent)
                )
                if (skin.isPremium) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Premium skin",
                        tint = colors.goldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = skin.description,
                style = typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
