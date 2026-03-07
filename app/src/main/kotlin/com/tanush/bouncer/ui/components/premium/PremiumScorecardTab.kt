package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.ui.theme.*
import io.sentry.Sentry

@Composable
fun PremiumScorecardTab(
    scorecard: Scorecard?,
    modifier: Modifier = Modifier
) {
    if (scorecard == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GoldPrimary)
        }
        return
    }

    var selectedInningsIndex by remember { mutableIntStateOf(0) }
    val innings = scorecard.innings.getOrNull(selectedInningsIndex)

    Column(modifier = modifier.fillMaxSize()) {
        // Quick Score Summary (matching mockup)
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${innings?.teamName ?: ""} ${innings?.score ?: ""} (${innings?.overs ?: ""})",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            // Calculate CRR from actual innings data
            val currentRunRate = calculateCurrentRunRate(innings)
            val requiredRunRate = calculateRequiredRunRate(scorecard, selectedInningsIndex)
            Text(
                text = if (requiredRunRate != null) {
                    "CRR: $currentRunRate • REQ: $requiredRunRate"
                } else {
                    "CRR: $currentRunRate"
                },
                color = GoldPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }

        // Innings Switcher (Mockup style)
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF121212))
                .border(0.5.dp, GlassSurface, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            scorecard.innings.forEachIndexed { index, inn ->
                val isSelected = selectedInningsIndex == index
                Button(
                    onClick = { selectedInningsIndex = index },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) GoldPrimary else Color.Transparent,
                        contentColor = if (isSelected) Color.Black else TextSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(
                        text = "${inn.teamName} Inning",
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp) // Standardized clearance
        ) {
            // Batting Section
            item { 
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(2.dp).height(16.dp).background(GoldPrimary))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Batting", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = Color(0xFF121212), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, GlassSurface)) {
                        Text("Live", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            
            item { BattingTableHeader() }
            
            items(innings?.batsmen ?: emptyList()) { batsman ->
                BattingRow(batsman)
            }
            
            // Extras
            item {
                ExtrasRow(innings?.extras ?: "0 (wd 0, nb 0, lb 0, b 0)", innings)
            }

            // Fall of Wickets
            if (innings != null && innings.fallOfWickets.isNotEmpty()) {
                item { SectionHeader("Fall of Wickets") }
                item {
                    FallOfWicketsCarousel(innings.fallOfWickets.map { "${it.score} (${it.batsman}, ${it.overs})" })
                }
            }

            // Bowling Section
            item { SectionHeader("Bowling") }
            item { BowlingTableHeader() }
            items(innings?.bowlers ?: emptyList()) { bowler ->
                BowlingRow(bowler)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(2.dp).height(16.dp).background(GoldPrimary))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BattingTableHeader() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(GlassSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("BATTER", modifier = Modifier.weight(3f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("R", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("B", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("4s", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("6s", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("SR", modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BattingRow(batsman: BatsmanStats) {
    val isNotOut = batsman.dismissal.lowercase().contains("not out") || batsman.dismissal.isEmpty()
    
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(if (isNotOut) GlassSurface else Color(0xFF121212))
            .border(0.5.dp, GlassSurface)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        if (isNotOut) {
            Box(modifier = Modifier.matchParentSize().drawBehind {
                drawRect(GoldPrimary, size = Size(2.dp.toPx(), size.height))
            })
        }
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(3f)) {
                Text(
                    text = batsman.name + if (isNotOut) " *" else "",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isNotOut) "Batting" else batsman.dismissal,
                    color = if (isNotOut) Color(0xFF4ADE80) else TextMuted,
                    fontSize = 10.sp,
                    fontStyle = if (!isNotOut) FontStyle.Italic else FontStyle.Normal
                )
            }
            Text(batsman.runs, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(batsman.balls, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
            Text(batsman.fours, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
            Text(batsman.sixes, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
            Text(batsman.strikeRate, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = GoldPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExtrasRow(extras: String, innings: Innings?) {
    val crr = calculateCurrentRunRate(innings)
    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GlassSurface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Extras: $extras", color = TextMuted, fontSize = 12.sp)
        Text("CRR $crr", color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Calculate current run rate from innings data
 */
private fun calculateCurrentRunRate(innings: Innings?): String {
    try {
        if (innings == null) return "-"
    
        // Parse score (e.g., "245/7" -> 245)
        val runs = innings.score.split("/").firstOrNull()?.toIntOrNull() ?: 0
    
        // Parse overs (e.g., "35.4" -> 35.67 overs)
        val oversStr = innings.overs.replace("Ov", "").trim()
        val oversParts = oversStr.split(".")
        val completedOvers = oversParts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val balls = oversParts.getOrNull(1)?.toIntOrNull() ?: 0
        val totalOvers = completedOvers + (balls / 6.0)
    
        if (totalOvers <= 0) return "-"
    
        val crr = runs / totalOvers
        return String.format("%.2f", crr)
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Calculate required run rate for second innings
 */
private fun calculateRequiredRunRate(scorecard: Scorecard, currentInningsIndex: Int): String? {
    try {
        // RRR only applies to second innings (chasing)
        if (currentInningsIndex != 1 || scorecard.innings.size < 2) return null
    
        val firstInnings = scorecard.innings.getOrNull(0) ?: return null
        val secondInnings = scorecard.innings.getOrNull(1) ?: return null
    
        // Get target (first innings score + 1)
        val targetRuns = (firstInnings.score.split("/").firstOrNull()?.toIntOrNull() ?: 0) + 1
    
        // Get current score
        val currentRuns = secondInnings.score.split("/").firstOrNull()?.toIntOrNull() ?: 0
        val runsNeeded = targetRuns - currentRuns
    
        if (runsNeeded <= 0) return null
    
        // Calculate remaining overs (assuming 50 or 20 over match)
        val oversStr = secondInnings.overs.replace("Ov", "").trim()
        val oversParts = oversStr.split(".")
        val completedOvers = oversParts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val balls = oversParts.getOrNull(1)?.toIntOrNull() ?: 0
        val bowledOvers = completedOvers + (balls / 6.0)
    
        // Estimate total overs from first innings
        val firstOversStr = firstInnings.overs.replace("Ov", "").trim()
        val firstOversParts = firstOversStr.split(".")
        val firstCompletedOvers = firstOversParts.getOrNull(0)?.toDoubleOrNull() ?: 50.0
    
        val remainingOvers = firstCompletedOvers - bowledOvers
    
        if (remainingOvers <= 0) return null
    
        val rrr = runsNeeded / remainingOvers
        return String.format("%.2f", rrr)
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

@Composable
private fun FallOfWicketsCarousel(fow: List<String>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(fow) { entry ->
            val parts = entry.split("(")
            val score = parts.getOrNull(0)?.trim() ?: "-"
            val detail = parts.getOrNull(1)?.replace(")", "") ?: ""
            
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121212))
                    .border(0.5.dp, GlassSurface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("Wicket", color = TextMuted, fontSize = 10.sp)
                    Text(score, color = GoldPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(detail, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BowlingTableHeader() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(GlassSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("BOWLER", modifier = Modifier.weight(3.5f), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("O", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("M", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("R", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("W", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("ECON", modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BowlingRow(bowler: BowlerStats) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .border(0.5.dp, GlassSurface)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(3.5f)) {
            Text(bowler.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            // Sparkline
            Canvas(modifier = Modifier.size(64.dp, 16.dp).padding(top = 4.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.5f)
                    lineTo(size.width * 0.1f, size.height * 0.8f)
                    lineTo(size.width * 0.2f, size.height * 0.2f)
                    lineTo(size.width * 0.3f, size.height * 0.5f)
                    lineTo(size.width * 0.4f, size.height * 0.4f)
                    lineTo(size.width * 0.5f, size.height * 0.6f)
                    lineTo(size.width * 0.6f, size.height * 0.3f)
                    lineTo(size.width * 0.7f, size.height * 0.5f)
                    lineTo(size.width * 0.8f, size.height * 0.2f)
                    lineTo(size.width * 0.9f, size.height * 0.8f)
                    lineTo(size.width, size.height * 0.5f)
                }
                drawPath(path, GoldPrimary, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Text(bowler.overs, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextPrimary, fontSize = 13.sp)
        Text(bowler.maidens, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
        Text(bowler.runs, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
        Text(bowler.wickets, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(bowler.economy, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
