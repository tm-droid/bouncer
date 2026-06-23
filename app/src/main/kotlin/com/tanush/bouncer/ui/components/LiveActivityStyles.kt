package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerTheme

enum class LiveActivityStyle {
    COMPACT,
    EXPANDED,
    DETAILED,
    MINIMAL_PILL,
    BROADCAST
}

data class LiveActivityData(
    val team1Name: String,
    val team2Name: String,
    val team1Abbreviation: String,
    val team2Abbreviation: String,
    val team1Score: String,
    val team2Score: String,
    val team1Overs: String,
    val team2Overs: String,
    val status: String,
    val currentBatsman1: String,
    val currentBatsman2: String,
    val currentBowler: String,
    val requiredRunRate: String,
    val currentRunRate: String,
    val lastSixBalls: List<String>,
    val isLive: Boolean
)

private val BlackBackground = Color(0xFF141720)
private val CardSurface = Color(0xFF1A1D23)
private val ShinyGoldDeep = Color(0xFFD4AF37)
private val LiveRed = Color(0xFFE53935)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextSecondary = Color(0xFFAAAAAA)

@Composable
fun CompactLiveCard(
    data: LiveActivityData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        color = CardSurface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (data.isLive) {
                PulsingLiveDot()
            }
            
            Text(
                text = data.team1Abbreviation,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = data.team1Score,
                color = ShinyGoldDeep,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "•",
                color = TextSecondary,
                fontSize = 14.sp
            )
            
            Text(
                text = data.team2Abbreviation,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = data.team2Score,
                color = ShinyGoldDeep,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ExpandedLiveCard(
    data: LiveActivityData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        color = CardSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TeamScoreRow(
                abbreviation = data.team1Abbreviation,
                name = data.team1Name,
                score = data.team1Score,
                overs = data.team1Overs
            )
            
            TeamScoreRow(
                abbreviation = data.team2Abbreviation,
                name = data.team2Name,
                score = data.team2Score,
                overs = data.team2Overs
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (data.isLive) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingLiveDot()
                        Text(
                            text = "LIVE",
                            color = LiveRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Text(
                    text = data.status,
                    color = ShinyGoldDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TeamScoreRow(
    abbreviation: String,
    name: String,
    score: String,
    overs: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ShinyGoldDeep.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = abbreviation,
                color = ShinyGoldDeep,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Text(
            text = name,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = score,
            color = ShinyGoldDeep,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = overs,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
fun DetailedLiveCard(
    data: LiveActivityData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        color = CardSurface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = data.team1Name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${data.team1Score} ${data.team1Overs}",
                        color = ShinyGoldDeep,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = data.team2Name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${data.team2Score} ${data.team2Overs}",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (data.currentBatsman1.isNotEmpty() || data.currentBatsman2.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (data.currentBatsman1.isNotEmpty()) {
                        BatsmanRow(data.currentBatsman1, isStriker = true)
                    }
                    if (data.currentBatsman2.isNotEmpty()) {
                        BatsmanRow(data.currentBatsman2, isStriker = false)
                    }
                }
            }
            
            if (data.currentBowler.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Bowler:",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = data.currentBowler,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            if (data.requiredRunRate.isNotEmpty() || data.currentRunRate.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (data.requiredRunRate.isNotEmpty()) {
                        Text(
                            text = "RRR: ${data.requiredRunRate}",
                            color = ShinyGoldDeep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (data.currentRunRate.isNotEmpty()) {
                        Text(
                            text = "CRR: ${data.currentRunRate}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            if (data.lastSixBalls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                LastSixBallsRow(data.lastSixBalls)
            }
        }
    }
}

@Composable
private fun BatsmanRow(nameAndScore: String, isStriker: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isStriker) "$nameAndScore*" else nameAndScore,
            color = if (isStriker) ShinyGoldDeep else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun LastSixBallsRow(balls: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Last 6:",
            color = TextSecondary,
            fontSize = 10.sp
        )
        
        balls.takeLast(6).forEach { ball ->
            BallIndicator(ball)
        }
    }
}

@Composable
private fun BallIndicator(ball: String) {
    val backgroundColor = when {
        ball == "W" -> Color(0xFFE53935)
        ball == "6" -> ShinyGoldDeep
        ball == "4" -> Color(0xFF2196F3)
        ball.toIntOrNull() != null -> Color(0xFFF5F5F5)
        else -> Color(0xFF555555)
    }
    
    val textColor = when {
        ball == "6" -> Color.Black
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(backgroundColor.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ball,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MinimalPillCard(
    data: LiveActivityData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .widthIn(min = 200.dp),
        color = CardSurface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(100.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (data.isLive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(LiveRed)
                )
            }
            
            Text(
                text = "${data.team1Abbreviation} ${data.team1Score} vs ${data.team2Abbreviation} ${data.team2Score}",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BroadcastLiveCard(
    data: LiveActivityData,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "broadcast")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        color = CardSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        Brush.horizontalGradient(
                            0.0f to ShinyGoldDeep.copy(alpha = 0.8f),
                            0.5f to ShinyGoldDeep,
                            1.0f to ShinyGoldDeep.copy(alpha = 0.8f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T20 INTERNATIONAL",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BroadcastTeamRow(
                    name = data.team1Name,
                    score = data.team1Score,
                    overs = data.team1Overs,
                    isBatting = true
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(ShinyGoldDeep.copy(alpha = 0.3f))
                )
                
                BroadcastTeamRow(
                    name = data.team2Name,
                    score = data.team2Score,
                    overs = data.team2Overs,
                    isBatting = false
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (data.isLive) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(LiveRed.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            PulsingLiveDot()
                            Text(
                                text = "LIVE",
                                color = LiveRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    
                    Text(
                        text = data.status,
                        color = ShinyGoldDeep,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BroadcastTeamRow(
    name: String,
    score: String,
    overs: String,
    isBatting: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isBatting) 2.dp else 1.dp,
                color = if (isBatting) ShinyGoldDeep else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (isBatting) FontWeight.Bold else FontWeight.Medium
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = score,
                color = if (isBatting) ShinyGoldDeep else TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = overs,
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun PulsingLiveDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "liveDot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(LiveRed)
    )
}

@Composable
fun LiveActivityStylePicker(
    selectedStyle: LiveActivityStyle,
    onStyleSelected: (LiveActivityStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleData = LiveActivityData(
        team1Name = "India",
        team2Name = "Australia",
        team1Abbreviation = "IND",
        team2Abbreviation = "AUS",
        team1Score = "245/3",
        team2Score = "198/7",
        team1Overs = "(38.2)",
        team2Overs = "(40.0)",
        status = "IND need 48 runs from 70 balls",
        currentBatsman1 = "V Kohli 87* (52)",
        currentBatsman2 = "S Iyer 34 (28)",
        currentBowler = "P Cummins 2/45 (7.2)",
        requiredRunRate = "6.17",
        currentRunRate = "6.42",
        lastSixBalls = listOf("1", "4", "0", "W", "6", "2"),
        isLive = true
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Choose Live Score Style",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        StyleOption(
            style = LiveActivityStyle.COMPACT,
            isSelected = selectedStyle == LiveActivityStyle.COMPACT,
            onClick = { onStyleSelected(LiveActivityStyle.COMPACT) }
        ) {
            CompactLiveCard(data = sampleData)
        }
        
        StyleOption(
            style = LiveActivityStyle.EXPANDED,
            isSelected = selectedStyle == LiveActivityStyle.EXPANDED,
            onClick = { onStyleSelected(LiveActivityStyle.EXPANDED) }
        ) {
            ExpandedLiveCard(data = sampleData)
        }
        
        StyleOption(
            style = LiveActivityStyle.DETAILED,
            isSelected = selectedStyle == LiveActivityStyle.DETAILED,
            onClick = { onStyleSelected(LiveActivityStyle.DETAILED) }
        ) {
            DetailedLiveCard(data = sampleData)
        }
        
        StyleOption(
            style = LiveActivityStyle.MINIMAL_PILL,
            isSelected = selectedStyle == LiveActivityStyle.MINIMAL_PILL,
            onClick = { onStyleSelected(LiveActivityStyle.MINIMAL_PILL) }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                MinimalPillCard(data = sampleData, modifier = Modifier.align(Alignment.Center))
            }
        }
        
        StyleOption(
            style = LiveActivityStyle.BROADCAST,
            isSelected = selectedStyle == LiveActivityStyle.BROADCAST,
            onClick = { onStyleSelected(LiveActivityStyle.BROADCAST) }
        ) {
            BroadcastLiveCard(data = sampleData)
        }
    }
}

@Composable
private fun StyleOption(
    style: LiveActivityStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ShinyGoldDeep else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(if (isSelected) 8.dp else 0.dp)
    ) {
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun CompactLiveCardPreview() {
    BouncerTheme {
        CompactLiveCard(
            data = LiveActivityData(
                team1Name = "India",
                team2Name = "Australia",
                team1Abbreviation = "IND",
                team2Abbreviation = "AUS",
                team1Score = "245/3",
                team2Score = "198/7",
                team1Overs = "(38.2)",
                team2Overs = "(40.0)",
                status = "IND need 48 runs",
                currentBatsman1 = "",
                currentBatsman2 = "",
                currentBowler = "",
                requiredRunRate = "",
                currentRunRate = "",
                lastSixBalls = emptyList(),
                isLive = true
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun ExpandedLiveCardPreview() {
    BouncerTheme {
        ExpandedLiveCard(
            data = LiveActivityData(
                team1Name = "India",
                team2Name = "Australia",
                team1Abbreviation = "IND",
                team2Abbreviation = "AUS",
                team1Score = "245/3",
                team2Score = "198/7",
                team1Overs = "(38.2)",
                team2Overs = "(40.0)",
                status = "IND need 48 runs from 70 balls",
                currentBatsman1 = "",
                currentBatsman2 = "",
                currentBowler = "",
                requiredRunRate = "",
                currentRunRate = "",
                lastSixBalls = emptyList(),
                isLive = true
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun DetailedLiveCardPreview() {
    BouncerTheme {
        DetailedLiveCard(
            data = LiveActivityData(
                team1Name = "India",
                team2Name = "Australia",
                team1Abbreviation = "IND",
                team2Abbreviation = "AUS",
                team1Score = "245/3",
                team2Score = "198/7",
                team1Overs = "(38.2)",
                team2Overs = "(40.0)",
                status = "IND need 48 runs from 70 balls",
                currentBatsman1 = "V Kohli 87* (52)",
                currentBatsman2 = "S Iyer 34 (28)",
                currentBowler = "P Cummins 2/45 (7.2)",
                requiredRunRate = "6.17",
                currentRunRate = "6.42",
                lastSixBalls = listOf("1", "4", "0", "W", "6", "2"),
                isLive = true
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun MinimalPillCardPreview() {
    BouncerTheme {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            MinimalPillCard(
                data = LiveActivityData(
                    team1Name = "India",
                    team2Name = "Australia",
                    team1Abbreviation = "IND",
                    team2Abbreviation = "AUS",
                    team1Score = "245/3",
                    team2Score = "198/7",
                    team1Overs = "(38.2)",
                    team2Overs = "(40.0)",
                    status = "IND need 48 runs",
                    currentBatsman1 = "",
                    currentBatsman2 = "",
                    currentBowler = "",
                    requiredRunRate = "",
                    currentRunRate = "",
                    lastSixBalls = emptyList(),
                    isLive = true
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun BroadcastLiveCardPreview() {
    BouncerTheme {
        BroadcastLiveCard(
            data = LiveActivityData(
                team1Name = "India",
                team2Name = "Australia",
                team1Abbreviation = "IND",
                team2Abbreviation = "AUS",
                team1Score = "245/3",
                team2Score = "198/7",
                team1Overs = "(38.2)",
                team2Overs = "(40.0)",
                status = "IND need 48 runs from 70 balls",
                currentBatsman1 = "V Kohli 87* (52)",
                currentBatsman2 = "S Iyer 34 (28)",
                currentBowler = "P Cummins 2/45 (7.2)",
                requiredRunRate = "6.17",
                currentRunRate = "6.42",
                lastSixBalls = listOf("1", "4", "0", "W", "6", "2"),
                isLive = true
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun LiveActivityStylePickerPreview() {
    BouncerTheme {
        var selectedStyle by remember { mutableStateOf(LiveActivityStyle.EXPANDED) }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BlackBackground)
                .padding(16.dp)
        ) {
            item {
                LiveActivityStylePicker(
                    selectedStyle = selectedStyle,
                    onStyleSelected = { selectedStyle = it }
                )
            }
        }
    }
}
