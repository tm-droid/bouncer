package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Toss Result Card - Minimalist Technical Readout.
 * Downgraded to MINIMAL importance as it is a one-time meta-event.
 */

data class TossDetails(
    val team1Name: String,
    val team2Name: String,
    val team1Logo: String,
    val team2Logo: String,
    val tossWinner: String,
    val decision: String, // "bat" or "bowl"
    val team1Color: Color = StatusInfo,
    val team2Color: Color = StatusLive,
)

@Composable
fun TossResultCard(
    tossDetails: TossDetails,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val isTeam1Winner = tossDetails.tossWinner == tossDetails.team1Name
    val winnerColor = if (isTeam1Winner) tossDetails.team1Color else tossDetails.team2Color

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.MINIMAL,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Subdued
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(14.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.toss_toss_result),
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Decision Banner - Clean & Focused
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(winnerColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (tossDetails.decision == "bat") Icons.Default.Sports else Icons.Default.GpsFixed,
                        contentDescription = if (tossDetails.decision == "bat") "Batting" else "Bowling",
                        tint = winnerColor,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = tossDetails.tossWinner.uppercase(),
                        color = winnerColor.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "elected to ${tossDetails.decision.uppercase()} first",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
