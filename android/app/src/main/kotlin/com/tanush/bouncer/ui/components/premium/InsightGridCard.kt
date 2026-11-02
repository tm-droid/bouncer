package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium Insight Grid Card.
 * Ported from Legacy Suite & Enhanced for Broadcast Elite.
 */
@Composable
fun InsightGridCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    progress: Float? = null,
    accentColor: Color = GoldPrimary,
    isHighPriority: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = modifier,
        useGoldBorder = true,
        borderOpacity = if (isHighPriority) GlassOpacity.BORDER_HIGHLIGHT else GlassOpacity.BORDER,
        contentPadding = PaddingValues(dimens.spacing.md)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    style = typography.labelSmall,
                    color = colors.textMuted
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(dimens.iconSize.sm)
                )
            }
            
            Spacer(modifier = Modifier.height(dimens.spacing.sm))
            
            Text(
                text = value,
                style = typography.scoreCompact,
                color = colors.textPrimary
            )
            
            Text(
                text = subValue,
                style = typography.bodySmall,
                color = colors.textSecondary
            )
            
            progress?.let {
                Spacer(modifier = Modifier.height(dimens.spacing.sm))
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(dimens.radius.xs)),
                    color = accentColor,
                    trackColor = hierarchy.secondary.tint
                )
            }
        }
    }
}
