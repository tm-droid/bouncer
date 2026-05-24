package com.tanush.bouncer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.GlassSurface

/**
 * QuickStatsRow - Horizontal compact stats display
 * Shows key match statistics in a compact horizontal scrollable row
 */
@Composable
fun QuickStatsRowEnhanced(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = GlassSurface
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            stats.forEach { (label, value) ->
                QuickStatItem(label = label, value = value)
            }
        }
    }
}

@Composable
private fun QuickStatItem(label: String, value: String) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 60.dp)
    ) {
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label.uppercase(),
            color = colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
