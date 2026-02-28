package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BallPrediction
import com.tanush.bouncer.ui.components.PredictiveBallCard
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.TextMuted

/**
 * Elite Forecast Carousel (v1.0).
 * Horizontal stream of AI-predicted ball outcomes.
 * Integrated into MatchDetailView Overview.
 */

@Composable
fun EliteForecastCarousel(
    modifier: Modifier = Modifier,
    predictions: List<BallPrediction> = emptyList() // Default mock data if empty
) {
    val displayPredictions = if (predictions.isEmpty()) {
        listOf(
            BallPrediction("15.1", 72, "Dot Ball"),
            BallPrediction("15.2", 85, "1 Run"),
            BallPrediction("15.3", 94, "Wicket", isCritical = true),
            BallPrediction("15.4", 68, "Boundary"),
            BallPrediction("15.5", 45, "2 Runs"),
            BallPrediction("15.6", 30, "Dot")
        )
    } else predictions

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MCP PREDICTIVE STREAM",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "NEXT OVER",
                color = ShinyGoldDeep,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 20.dp)
        ) {
            items(displayPredictions) { prediction ->
                PredictiveBallCard(prediction = prediction)
            }
        }
    }
}
