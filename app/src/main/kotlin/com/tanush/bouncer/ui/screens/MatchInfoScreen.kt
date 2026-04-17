package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchInfoScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val dimens = BouncerDesignSystem.dimens
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("MATCH CENTER", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Check out ${match.teams.joinToString(" vs ")} on Bouncer! \uD83C\uDFCF")
                        }, "Share Match")
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.MoreHoriz, null, tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Venue Header
                VenueHeader(match)

                // Live Conditions
                WeatherSection(match)

                // Pitch Report
                PitchReportSection(match)

                // Venue History
                VenueHistoryGrid(match)

                // Toss Impact
                TossImpactSection(match)

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun VenueHeader(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.backgroundBase)
    ) {
        // High-res stadium image mapping based on venue name
        val venueImage = remember(match.venue) {
            when {
                match.venue.contains("Lords", true) -> "https://resources.pulse.icc-cricket.com/ICC/photo/2023/06/07/9b3b8c3a-2a4e-4f1a-8c0a-0b5c9b6b6b6b/Lords.jpg"
                match.venue.contains("Melbourne", true) || match.venue.contains("MCG", true) -> "https://www.melbournemcg.org.au/-/media/images/mcg/stadium/mcg-night-aerial-2018.jpg"
                match.venue.contains("Eden Gardens", true) -> "https://static.independent.co.uk/2023/11/05/11/Eden%20Gardens.jpg"
                match.venue.contains("Wankhede", true) -> "https://mumbaimirror.indiatimes.com/photo/74567405.cms"
                else -> "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?q=80&w=1000&auto=format&fit=crop" // Generic Cricket Stadium
            }
        }

        CoilImage(
            model = venueImage,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.6f),
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, colors.backgroundBase)))
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(16.dp))
                Text("VENUE", color = hierarchy.secondary.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Text(match.venue, color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            
            val capacityText = remember(match.venue) {
                when {
                    match.venue.contains("Melbourne", true) -> "Capacity: 100,024 • International Standard"
                    match.venue.contains("Eden Gardens", true) -> "Capacity: 66,000 • Iconic Atmosphere"
                    match.venue.contains("Lords", true) -> "Capacity: 31,100 • Home of Cricket"
                    match.venue.contains("Wankhede", true) -> "Capacity: 33,108 • Vibrant Hub"
                    else -> "International Cricket Venue • Elite Standards"
                }
            }
            Text(capacityText, color = colors.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun WeatherSection(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Live Conditions")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Cloud, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("24°C", color = hierarchy.secondary.primary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("Clear Skies", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(colors.glassBackground))
                Column(modifier = Modifier.weight(1f).padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeatherStat(Icons.Default.WaterDrop, "52% Humidity")
                    WeatherStat(Icons.Default.Air, "14 km/h Wind")
                }
            }
        }
    }
}

@Composable
private fun WeatherStat(icon: ImageVector, value: String) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Text(value, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PitchReportSection(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Pitch Report")
            Surface(color = hierarchy.secondary.primary.copy(alpha = 0.1f), shape = CircleShape, border = BorderStroke(0.5.dp, hierarchy.secondary.primary.copy(alpha = 0.2f))) {
                Text("EXPERT ANALYSIS", color = hierarchy.secondary.primary, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(40.dp).background(colors.surface2, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SportsCricket, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        val surface = if (match.matchType == "Test") "Hard & Abrasive" else "Flat & Hard"
                        Text("Surface: $surface", color = hierarchy.secondary.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Updated at $currentTime", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
                
                val pitchCommentary = remember(match.venue, match.matchType) {
                    when (match.matchType) {
                        "Test" -> "Early moisture expected to assist seamers. As the match progresses, the surface will dry out, bringing spinners into play from Day 3 onwards. Footmarks will become crucial."
                        "T20" -> "A true batting paradise with minimal lateral movement. Short boundaries at this venue will make it a high-scoring encounter. Bowlers will need to rely on variations."
                        else -> "Balanced surface offering something for both batsmen and bowlers. Good carry for the pacers early on, stabilizing for the middle overs."
                    }
                }
                
                Text(
                    pitchCommentary,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bounce = if (match.venue.contains("Australia", true)) "High Bounce" else "Even Bounce"
                    val turn = if (match.venue.contains("India", true) || match.venue.contains("Sri Lanka", true)) "High Turn" else "Moderate Turn"
                    
                    PitchMetric("Pace", bounce, Modifier.weight(1f))
                    PitchMetric("Spin", turn, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PitchMetric(label: String, value: String, modifier: Modifier) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(colors.backgroundBase.copy(alpha = 0.4f)).padding(12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = hierarchy.secondary.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VenueHistoryGrid(match: CricketMatch) {
    val stats = remember(match.venue) {
        when {
            match.venue.contains("Melbourne", true) -> Pair("295", "252")
            match.venue.contains("Eden Gardens", true) -> Pair("315", "278")
            match.venue.contains("Lords", true) -> Pair("280", "240")
            else -> Pair("275", "245")
        }
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Venue History")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VenueStatCard("Avg 1st Inn", stats.first, true, Modifier.weight(1f))
            VenueStatCard("Avg 2nd Inn", stats.second, false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun VenueStatCard(label: String, value: String, isPrimary: Boolean, modifier: Modifier) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = modifier,
        useGoldBorder = isPrimary
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label.uppercase(), color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = if (isPrimary) hierarchy.secondary.primary else colors.textSecondary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TossImpactSection(match: CricketMatch) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val tossStats = remember(match.venue) {
        when {
            match.venue.contains("India", true) -> Pair(0.55f, 0.45f)
            match.venue.contains("Australia", true) -> Pair(0.52f, 0.48f)
            match.venue.contains("England", true) -> Pair(0.48f, 0.52f)
            else -> Pair(0.50f, 0.50f)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Toss Impact")
        GlassCard(modifier = Modifier.fillMaxWidth(), useGoldBorder = true) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().background(hierarchy.secondary.primary.copy(alpha = 0.1f)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Win Probability by Toss", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Info, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(16.dp))
                }
                
                Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    TossStat("Bat 1st Win", tossStats.first, hierarchy.secondary.primary, Modifier.weight(1f))
                    TossStat("Bowl 1st Win", tossStats.second, colors.textTertiary, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TossStat(label: String, progress: Float, color: Color, modifier: Modifier) {
    val colors = BouncerDesignSystem.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = colors.textSecondary, fontSize = 11.sp)
            Text("${(progress * 100).toInt()}%", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.glassBackground)) {
            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(hierarchy.secondary.secondary, hierarchy.secondary.primary, hierarchy.secondary.primary)
                    )
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(title.uppercase(), color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
