package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Achievements Screen
 * Achievement showcase with radar charts and progress tracking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophyRoomScreen(
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val alphaValue by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(animation = tween(5000), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )

    Box(modifier = Modifier.fillMaxSize().background(BlackBackground)) {
        // Ambient background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(GoldDark.copy(alpha = alphaValue), Color.Transparent)),
                center = Offset(size.width, 0f),
                radius = size.width
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Achievements",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = GoldDark)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBackground.copy(alpha = 0.8f))
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Hero Stats Card
                HeroProgressCardTechnical()

                // Medal Collection
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(4.dp, 18.dp).background(GoldDark, RoundedCornerShape(2.dp)))
                            Text("MEDAL COLLECTION", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                        Text("Filter", color = GoldDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TrophyItemCard(
                                modifier = Modifier.weight(1f),
                                title = "The Centurion",
                                description = "Score 100 runs in a single innings.",
                                tier = "GOLD",
                                icon = Icons.Default.SportsCricket,
                                tierColor = GoldDark
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            TrophyItemCard(
                                modifier = Modifier.weight(1f),
                                title = "The Hat-Trick",
                                description = "Take 3 wickets in 3 consecutive balls.",
                                tier = "SILVER",
                                icon = Icons.Default.SportsBaseball,
                                tierColor = Color(0xFFE0E0E0)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TrophyItemCard(
                                modifier = Modifier.weight(1f),
                                title = "Five-Wicket Haul",
                                description = "Take 5 wickets in a single match.",
                                tier = "BRONZE",
                                icon = Icons.Default.Stadium,
                                tierColor = Color(0xFFCD7F32)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            TrophyItemCard(
                                modifier = Modifier.weight(1f),
                                title = "Golden Duck",
                                description = "Get out on the first ball faced.",
                                tier = "LOCKED",
                                icon = Icons.Default.Pets,
                                isLocked = true
                            )
                        }
                    }
                }

                // Career Radar
                CareerRadarSectionTechnical()
            }
        }
        
        // Refined FAB
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingActionButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 100.dp)
                    .size(60.dp),
                containerColor = GoldDark,
                contentColor = Color.Black,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(12.dp)
            ) {
                Icon(Icons.Default.Insights, contentDescription = "Insights", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun HeroProgressCardTechnical() {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                0.5.dp, 
                Brush.linearGradient(listOf(GoldHairline, Color.White.copy(alpha = 0.05f))), 
                RoundedCornerShape(32.dp)
            )
            .padding(28.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("ACHIEVEMENT PROGRESSION", color = GoldDark, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("12", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                Text("/ 50", color = TextMuted, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("STATUS: ")
                        withStyle(style = SpanStyle(color = GoldDark, fontWeight = FontWeight.Black)) {
                            append("RISING STAR")
                        }
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.24f)
                        .fillMaxHeight()
                        .background(GoldDark)
                )
            }
        }
    }
}

@Composable
private fun TrophyItemCard(
    modifier: Modifier,
    title: String,
    description: String,
    tier: String,
    icon: ImageVector,
    tierColor: Color = Color.White,
    isLocked: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.5.dp, if (isLocked) Color.White.copy(alpha = 0.05f) else tierColor.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            .clickable { /* Detail view */ }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!isLocked) {
                    val infiniteTransition = rememberInfiniteTransition(label = "medal")
                    val pulse by infiniteTransition.animateFloat(
                        initialValue = 0.8f, targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
                        label = "pulse"
                    )
                    Box(modifier = Modifier.size(60.dp).blur(20.dp).background(tierColor.copy(alpha = 0.1f * pulse), CircleShape))
                }
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .then(
                            if (!isLocked) Modifier.background(tierColor.copy(alpha = 0.1f), CircleShape).border(1.dp, tierColor.copy(alpha = 0.3f), CircleShape)
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, 
                        null, 
                        tint = if (!isLocked) tierColor else TextMuted,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                if (isLocked) {
                    Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).padding(8.dp))
                }
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title.uppercase(), color = if (isLocked) TextMuted else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isLocked) Color.White.copy(alpha = 0.05f) else tierColor.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(tier, color = if (isLocked) TextMuted else tierColor, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                Text(description, color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CareerRadarSectionTechnical() {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(4.dp, 18.dp).background(ShinyGoldDeep, RoundedCornerShape(2.dp)))
                Text("CAREER RADAR", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ShinyGoldDeep.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("SYNCED", color = ShinyGoldDeep, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.5.dp, ShinyGoldDeep.copy(alpha = 0.15f), RoundedCornerShape(32.dp))
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    RadarChartTechnical(
                        values = listOf(0.9f, 0.7f, 0.85f, 0.6f, 0.8f, 0.95f)
                    )
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("PEAK METRIC", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("POWER HITTING", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PROJECTED", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("+12.4% GAIN", color = ShinyGoldDeep, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarChartTechnical(values: List<Float>) {
    val animatedValues = values.map { 
        val anim = remember { Animatable(0f) }
        LaunchedEffect(it) { anim.animateTo(it, tween(2000, easing = FastOutSlowInEasing)) }
        anim.value 
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 - 20.dp.toPx()
        val sides = values.size
        val angleStep = (2 * PI / sides).toFloat()

        // Background Web
        for (i in 1..4) {
            val r = radius * (i / 4f)
            val path = Path()
            for (j in 0 until sides) {
                val angle = j * angleStep - PI.toFloat() / 2
                val x = centerX + r * cos(angle)
                val y = centerY + r * sin(angle)
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Color.White.copy(alpha = 0.05f), style = Stroke(width = 1.dp.toPx()))
        }

        // Axes
        for (j in 0 until sides) {
            val angle = j * angleStep - PI.toFloat() / 2
            drawLine(Color.White.copy(alpha = 0.1f), Offset(centerX, centerY), Offset(centerX + radius * cos(angle), centerY + radius * sin(angle)), strokeWidth = 1.dp.toPx())
        }

        // Data Layer
        val dataPath = Path()
        for (j in 0 until sides) {
            val angle = j * angleStep - PI.toFloat() / 2
            val r = radius * animatedValues[j]
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)
            if (j == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()
        drawPath(dataPath, Brush.radialGradient(listOf(ShinyGoldDeep.copy(alpha = 0.2f), Color.Transparent)))
        drawPath(dataPath, ShinyGoldDeep, style = Stroke(width = 2.dp.toPx()))
    }
}
