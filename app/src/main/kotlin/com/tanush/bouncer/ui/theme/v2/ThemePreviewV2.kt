package com.tanush.bouncer.ui.theme.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders the palette itself, in both modes, so a colour regression is visible in the IDE.
 *
 * Every preview in this repo used to render dark — the three that existed declared no `uiMode` and
 * one wrapped itself in the force-dark legacy theme — which is a large part of why light mode could
 * ship unreadable. These are pinned to [ThemeModeV2] explicitly rather than to `uiMode`, so they
 * follow the app's own resolution rather than the preview host's.
 *
 * The Material row matters as much as the Bouncer row: roles Bouncer does not name itself still
 * paint real components (`secondaryContainer` is the navigation-bar indicator, `surfaceContainer`
 * backs every Card), and when they were left unmapped they came out of the M3 baseline purple.
 */
@Composable
private fun PalettePreview(mode: ThemeModeV2) {
    BouncerThemeV2(themeMode = mode) {
        val colors = BouncerThemeV2.colors
        Surface(color = colors.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (colors.isDark) "Dark" else "Light",
                    color = colors.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextAndRulesCard()
                SwatchRow(
                    "Bouncer",
                    listOf(
                        colors.primary to colors.onPrimary,
                        colors.primaryContainer to colors.onSurface,
                        colors.live to Color.White,
                        colors.win to Color.White,
                        colors.topBar to colors.onTopBar,
                    ),
                )
                SwatchRow(
                    "Events",
                    with(colors.cricket) {
                        listOf(wicket, four, six, wide, noBall, maiden, dotBall).map { it to onEvent }
                    },
                )
                // Unmapped Material roles are where the baseline purple used to leak in.
                SwatchRow(
                    "Material",
                    with(MaterialTheme.colorScheme) {
                        listOf(
                            secondaryContainer to onSecondaryContainer,
                            tertiaryContainer to onTertiaryContainer,
                            surfaceContainer to onSurface,
                            surfaceContainerHighest to onSurface,
                            errorContainer to onErrorContainer,
                        )
                    },
                )
            }
        }
    }
}

/** The pairs that actually failed: body text, supporting text, gold ink, and the hairlines. */
@Composable
private fun TextAndRulesCard() {
    val colors = BouncerThemeV2.colors
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("onSurface — body text", color = colors.onSurface, fontSize = 14.sp)
            Text("textSecondary — supporting", color = colors.textSecondary, fontSize = 13.sp)
            Text("primaryText — gold as ink", color = colors.primaryText, fontSize = 13.sp)
            HorizontalDivider(color = colors.divider)
            Text("divider above, border below", color = colors.textSecondary, fontSize = 11.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .padding(6.dp),
            ) {
                Text("bordered", color = colors.onSurface, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SwatchRow(label: String, swatches: List<Pair<Color, Color>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = BouncerThemeV2.colors.textSecondary, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            swatches.forEach { (fill, ink) ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(fill, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(name = "Palette — Light", showBackground = true, widthDp = 380, heightDp = 520)
@Composable
private fun PalettePreviewLight() = PalettePreview(ThemeModeV2.Light)

@Preview(name = "Palette — Dark", showBackground = true, widthDp = 380, heightDp = 520)
@Composable
private fun PalettePreviewDark() = PalettePreview(ThemeModeV2.Dark)
