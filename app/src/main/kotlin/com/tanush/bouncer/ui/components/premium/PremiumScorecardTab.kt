package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.MatchProjections
import com.tanush.bouncer.ui.components.BowlerPerformance
import com.tanush.bouncer.ui.components.BowlingPerformanceCard
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.parseRuns
import java.util.Locale

@Composable
fun PremiumScorecardTab(
    scorecard: Scorecard?,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    if (scorecard == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = hierarchy.secondary.primary)
        }
        return
    }
    if (scorecard.innings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_no_scorecard_available), color = TextSecondary)
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
                color = hierarchy.secondary.primary,
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
                .background(BouncerThemeV2.colors.surfaceVariant)
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
                        contentColor = if (isSelected) BouncerThemeV2.colors.onPrimary else TextSecondary
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
                        Box(modifier = Modifier.width(2.dp).height(16.dp).background(hierarchy.secondary.primary))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.premium_batting), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = BouncerThemeV2.colors.surfaceVariant, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, GlassSurface)) {
                        Text(
                            stringResource(R.string.premium_live),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            item { BattingTableHeader() }
            
            // Indexed keys: a scorecard can legitimately repeat a name (and a scraped row can
            // duplicate one outright), and a batsman and a bowler in the same LazyColumn collided
            // on the bare name — a duplicate key is a hard crash, not a rendering glitch.
            itemsIndexed(
                innings?.batsmen ?: emptyList(),
                key = { index, batsman -> "bat_${index}_${batsman.name}" },
            ) { _, batsman ->
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
                    FallOfWicketsCarousel(innings.fallOfWickets)
                }
            }

            // Partnerships
            if (innings != null && innings.partnerships.isNotEmpty()) {
                item { SectionHeader("Partnerships") }
                item {
                    PartnershipBreakdownCard(
                        teamName = innings.teamName,
                        // This mapping had never run: partnerships parsed empty until the scraper
                        // was pointed at scorecard-pship-grid. Run rate and wicket number were
                        // constants here, and the card renders the first of them ("RR: 0.00") —
                        // both are derivable from what the row actually carries.
                        partnerships = innings.partnerships.mapIndexed { index, p ->
                            val totalRuns = p.runs.toIntOrNull() ?: 0
                            val totalBalls = p.balls.toIntOrNull() ?: 0
                            PartnershipEntry(
                                batter1Name = p.player1,
                                batter1Runs = p.contribution1.toIntOrNull() ?: 0,
                                batter1Balls = 0,
                                batter2Name = p.player2,
                                batter2Runs = p.contribution2.toIntOrNull() ?: 0,
                                batter2Balls = 0,
                                totalRuns = totalRuns,
                                totalBalls = totalBalls,
                                runRate = MatchProjections.currentRunRate(totalRuns, totalBalls) ?: 0.0,
                                wicketNumber = index + 1,
                            )
                        }
                    )
                }
            }

            // Bowling Section
            item { SectionHeader("Bowling") }
            item { BowlingTableHeader() }
            itemsIndexed(
                innings?.bowlers ?: emptyList(),
                key = { index, bowler -> "bowl_${index}_${bowler.name}" },
            ) { _, bowler ->
                BowlingRow(bowler)
            }

            // Premium bowling analytics card
            val bowlers = innings?.bowlers
            if (!bowlers.isNullOrEmpty()) {
                item(key = "bowling_performance_card") {
                    BowlingPerformanceCard(
                        teamName = innings?.teamName ?: "",
                        bowlers = bowlers.map { b ->
                            BowlerPerformance(
                                name = b.name,
                                imageUrl = b.profileUrl.ifBlank { null },
                                overs = b.overs.toFloatOrNull() ?: 0f,
                                maidens = b.maidens.toIntOrNull() ?: 0,
                                runs = b.runs.toIntOrNull() ?: 0,
                                wickets = b.wickets.toIntOrNull() ?: 0,
                                economy = b.economy.toFloatOrNull() ?: 0f,
                                dots = 0,
                                wides = b.wides.toIntOrNull() ?: 0,
                                noBalls = b.noBalls.toIntOrNull() ?: 0,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(2.dp).height(16.dp).background(hierarchy.secondary.primary))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BattingTableHeader() {
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(GlassSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.premium_batter),
            modifier = Modifier.weight(3f),
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text("R", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("B", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("4s", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("6s", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("SR", modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = hierarchy.secondary.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BattingRow(batsman: BatsmanStats) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val isNotOut = batsman.dismissal.lowercase().contains("not out") || batsman.dismissal.isEmpty()
    
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(if (isNotOut) GlassSurface else BouncerThemeV2.colors.surfaceVariant)
            .border(0.5.dp, GlassSurface)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        if (isNotOut) {
            Box(modifier = Modifier.matchParentSize().drawBehind {
                drawRect(hierarchy.secondary.primary, size = Size(2.dp.toPx(), size.height))
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
                    color = if (isNotOut) BouncerThemeV2.colors.win else TextMuted,
                    fontSize = 10.sp,
                    fontStyle = if (!isNotOut) FontStyle.Italic else FontStyle.Normal
                )
            }
            Text(batsman.runs, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(batsman.balls, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
            Text(batsman.fours, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
            Text(batsman.sixes, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
            Text(batsman.strikeRate, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = hierarchy.secondary.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExtrasRow(extras: String, innings: Innings?) {
    val hierarchy = BouncerDesignSystem.hierarchy
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
        Text("CRR $crr", color = hierarchy.secondary.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun calculateCurrentRunRate(innings: Innings?): String {
    if (innings == null) return "-"
    val runs = parseRuns(innings.score)
    val oversParts = innings.overs.replace("Ov", "").trim().split(".")
    val completedOvers = oversParts.getOrNull(0)?.toIntOrNull() ?: 0
    val balls = oversParts.getOrNull(1)?.toIntOrNull() ?: 0
    val ballsBowled = completedOvers * CricketConstants.BALLS_PER_OVER + balls
    return MatchProjections.currentRunRate(runs, ballsBowled)
        ?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
}

private fun calculateRequiredRunRate(scorecard: Scorecard, currentInningsIndex: Int): String? {
    if (currentInningsIndex != 1 || scorecard.innings.size < 2) return null
    val firstInnings = scorecard.innings.getOrNull(0) ?: return null
    val secondInnings = scorecard.innings.getOrNull(1) ?: return null
    val targetRuns = parseRuns(firstInnings.score) + 1
    val currentRuns = parseRuns(secondInnings.score)
    // Infer format from first innings overs: ≤20 completed overs → T20, else ODI
    val firstInningsOvers = firstInnings.overs.replace("Ov", "").trim()
        .split(".").getOrNull(0)?.toIntOrNull() ?: 50
    val totalBalls = if (firstInningsOvers <= CricketConstants.T20_INNINGS_BALLS / CricketConstants.BALLS_PER_OVER) {
        CricketConstants.T20_INNINGS_BALLS
    } else {
        CricketConstants.ODI_INNINGS_BALLS
    }
    val secondOversParts = secondInnings.overs.replace("Ov", "").trim().split(".")
    val completedOvers = secondOversParts.getOrNull(0)?.toIntOrNull() ?: 0
    val balls = secondOversParts.getOrNull(1)?.toIntOrNull() ?: 0
    val ballsBowled = completedOvers * CricketConstants.BALLS_PER_OVER + balls
    return MatchProjections.requiredRunRate(targetRuns, currentRuns, ballsBowled, totalBalls)
        ?.let { String.format(Locale.US, "%.2f", it) }
}

// Takes the model, not a formatted string. The caller used to format "score (batsman, overs)" and
// this split it back apart on "(" — a format-then-parse round trip whose only possible outcomes are
// the original data or a worse version of it, and which loses the batsman on any name with a
// bracket in it.
@Composable
private fun FallOfWicketsCarousel(fow: List<FallOfWicket>) {
    val hierarchy = BouncerDesignSystem.hierarchy
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(fow, key = { index, entry -> "fow_${index}_${entry.score}_${entry.batsman}" }) { _, entry ->
            val score = entry.score.ifBlank { "-" }
            val detail = listOf(entry.batsman, entry.overs).filter { it.isNotBlank() }.joinToString(", ")

            Box(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BouncerThemeV2.colors.surfaceVariant)
                    .border(0.5.dp, GlassSurface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(stringResource(R.string.premium_wicket), color = TextMuted, fontSize = 10.sp)
                    Text(score, color = hierarchy.secondary.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
        Text(
            stringResource(R.string.premium_bowler),
            modifier = Modifier.weight(3.5f),
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text("O", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("M", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("R", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("W", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("ECON", modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BowlingRow(bowler: BowlerStats) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(BouncerThemeV2.colors.surfaceVariant)
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
                drawPath(path, hierarchy.secondary.primary, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Text(bowler.overs, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextPrimary, fontSize = 13.sp)
        Text(bowler.maidens, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
        Text(bowler.runs, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextMuted, fontSize = 12.sp)
        Text(bowler.wickets, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(bowler.economy, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = hierarchy.secondary.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
