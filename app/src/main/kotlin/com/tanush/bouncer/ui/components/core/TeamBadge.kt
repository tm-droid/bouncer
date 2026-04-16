package com.tanush.bouncer.ui.components.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.theme.GlassBorderHighlight
import com.tanush.bouncer.ui.theme.GlassSurface
import com.tanush.bouncer.ui.theme.TextPrimary
import com.tanush.bouncer.ui.theme.TextSecondary

@Composable
fun TeamBadge(
    teamName: String?,
    logoUrl: String?,
    isBatting: Boolean,
    modifier: Modifier = Modifier,
    teamTint: Color? = null,
    showBattingIndicator: Boolean = true
) {
    val badgeColor = teamTint ?: TextPrimary
    val resolvedTeamName = teamName ?: "TBD"
    val resolvedLogoUrl = TeamLogoManager.getBestLogoUrl(teamName, logoUrl, 72, 72)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background((teamTint ?: GlassSurface).copy(alpha = if (teamTint != null) 0.14f else 1f))
                .border(
                    width = 0.5.dp,
                    color = if (teamTint != null) badgeColor.copy(alpha = 0.45f) else GlassBorderHighlight,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            CoilImage(
                model = resolvedLogoUrl,
                contentDescription = resolvedTeamName,
                modifier = Modifier.size(26.dp),
                contentScale = ContentScale.Fit
            )
        }

        Text(
            text = resolvedTeamName,
            color = if (isBatting) (teamTint ?: TextPrimary) else TextSecondary,
            fontWeight = if (isBatting) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        if (showBattingIndicator && isBatting) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
        }
    }
}

