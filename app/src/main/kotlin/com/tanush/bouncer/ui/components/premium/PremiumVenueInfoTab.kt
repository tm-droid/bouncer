package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ErrorRed

@Composable
fun PremiumVenueInfoTab(match: CricketMatch) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Venue Header Image
        item {
            VenueHeaderCard(match)
        }

        // Live Conditions (Weather)
        item {
            LiveConditionsCard()
        }

        // Pitch Report
        item {
            PitchReportCard()
        }

        // Venue History (Stats Grid)
        item {
            VenueHistorySection()
        }

        // Toss Impact
        item {
            TossImpactCard()
        }
        
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
private fun VenueHeaderCard(match: CricketMatch) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
    ) {
        CoilImage(
            model = "https://lh3.googleusercontent.com/aida-public/AB6AXuD8JtNjK_OctwRthKVq0CGylWOPerY5NTb_miGHk-UfKcpJ05JCRm0gcWed2Ofs5Y16bPGizwi-1JXU0azvOsbbPUcRRPeRFC2hboRMTejxsaw_mIUxG1LlSEUby_tbS4Ad960gwGH53AMBF9d-w3yNTzKj6niM1sRKRYdaBt-klK1WhXMKi7TjyoMFRNwuQNhjrUJ1jPp43lP3SiioUsn1IzQBIX5ugeazmWAcxB3qohLDwaKJ_FXx4p0RaOp2zhX2c7gIg53V5cc",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.95f))
                    )
                )
        )
        
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = ShinyGoldDeep, modifier = Modifier.size(18.dp))
                Text("VENUE", color = ShinyGoldDeep, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
            Text(match.venue, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, lineHeight = 36.sp, letterSpacing = (-1).sp)
            Text("London, UK • Capacity: 30,000", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveConditionsCard() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Live Conditions")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141414).copy(alpha = 0.7f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.Cloud, null, tint = ShinyGoldDeep, modifier = Modifier.size(40.dp))
                    Column {
                        Text("22°", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                        Text("Sunny Intervals", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Box(modifier = Modifier.width(1.dp).height(48.dp).background(Color.White.copy(alpha = 0.1f)))
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConditionItem(Icons.Default.WaterDrop, "45%")
                    ConditionItem(Icons.Default.Air, "12 km/h")
                }
            }
        }
    }
}

@Composable
private fun ConditionItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PitchReportCard() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Pitch Report")
            Surface(color = ShinyGoldDeep.copy(alpha = 0.15f), shape = CircleShape, border = BorderStroke(1.dp, ShinyGoldDeep.copy(alpha = 0.3f))) {
                Text("EXPERT ANALYSIS", color = ShinyGoldDeep, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), letterSpacing = 1.sp)
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141414).copy(alpha = 0.7f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(56.dp).background(Color(0xFF2A2A2A), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SportsCricket, null, tint = ShinyGoldDeep, modifier = Modifier.size(28.dp))
                    }
                    Column {
                        Text("Surface: Dry", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("Updated 15 mins ago by Simon Doull", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Text(
                    text = "The pitch is notably dry with visible cracks developing around the good length area. Spinners are expected to dominate the middle overs as the ball grips. However, it remains a good batting surface initially.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PitchStatBox("PACE", "Good Bounce", Modifier.weight(1f))
                    PitchStatBox("SPIN", "High Turn", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PitchStatBox(label: String, value: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun VenueHistorySection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Venue History")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            VenueStatCard("Avg 1st Innings", "284", Modifier.weight(1f), isGold = true)
            VenueStatCard("Avg 2nd Innings", "245", Modifier.weight(1f))
        }
    }
}

@Composable
private fun VenueStatCard(label: String, value: String, modifier: Modifier, isGold: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141414).copy(alpha = 0.7f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .then(if (isGold) Modifier.drawBehind {
                drawLine(ShinyGoldDeep, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 3.dp.toPx())
            } else Modifier)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, letterSpacing = 1.sp)
            Text(value, color = if (isGold) Color.White else Color.White.copy(alpha = 0.6f), fontSize = 36.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TossImpactCard() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Toss Impact")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141414).copy(alpha = 0.7f))
                .border(1.dp, ShinyGoldDeep.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().background(ShinyGoldDeep.copy(alpha = 0.12f)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Win Probability by Toss", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Icon(Icons.Default.Info, null, tint = ShinyGoldDeep, modifier = Modifier.size(18.dp))
                }
                
                Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    TossProgressBar("Bat 1st Win", 0.6f, ShinyGoldDeep, Modifier.weight(1f))
                    TossProgressBar("Bowl 1st Win", 0.4f, Color.White.copy(alpha = 0.35f), Modifier.weight(1f))
                }
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    TossHistoryRow("vs AUS", "Bat", "Won", isWin = true)
                    TossHistoryRow("vs IND", "Bowl", "Lost", isWin = false)
                    TossHistoryRow("vs NZ", "Bowl", "Won", isWin = true)
                }
                
                TextButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Text("VIEW ALL HISTORY", color = ShinyGoldDeep, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}

@Composable
private fun TossProgressBar(label: String, progress: Float, color: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${(progress * 100).toInt()}%", color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun TossHistoryRow(team: String, decision: String, result: String, isWin: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(team, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(decision, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(result, color = if (isWin) ShinyGoldDeep else ErrorRed, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(4.dp, 18.dp).background(ShinyGoldDeep, RoundedCornerShape(2.dp)))
        Text(title.uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    }
}
