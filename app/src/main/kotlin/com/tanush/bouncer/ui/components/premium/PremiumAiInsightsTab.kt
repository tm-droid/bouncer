package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.BlackBackground

@Composable
fun PremiumAiInsightsTab(match: CricketMatch) {
    val scrollState = rememberScrollState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // AI Neural Banner with subtle animation
            AiNeuralBanner()

            // DRS Visualization Card
            DrsVisualizationCard()

            // Verdict Module with premium styling
            VerdictModule()

            // Delivery Metrics with entry animations
            DeliveryMetricsSection()

            // Pitch Map Mini Card
            PitchMapMiniCard()
            
            Spacer(modifier = Modifier.height(140.dp))
        }

        // Refined Bottom Action Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, BlackBackground.copy(alpha = 0.95f), BlackBackground)
                    )
                )
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("FULL ANALYSIS", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
                Button(
                    onClick = {},
                    modifier = Modifier.weight(2f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShinyGoldDeep, contentColor = Color.Black),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Default.Gavel, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("REQUEST REVIEW", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}

@Composable
private fun AiNeuralBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "banner")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(animation = tween(4000), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
            .border(1.dp, ShinyGoldDeep.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
    ) {
        CoilImage(
            model = "https://lh3.googleusercontent.com/aida-public/AB6AXuDsb8zoInOAQHjBtC0v9FTTkw8IqC3dtvi8A4nCNTV53qtEobfL9te1_VjsKiDXphFOC2kUQNmQaULYU2ELAjNUSkGWIrB0iFo8GSHMKZwYVtOO2DXQKTboZfjRW7ty2xB2HztlFdmonHfWXtnzUYMD89sYx9plVY8yDtGuFsn9rgHY2D7Qu4pTNQHEUrwrs84Wzl9jZMtReEvqZvnLM7uBgOfycETUXv-bxW21XZspH8qHPoz-2xO-M9e9XsZDwzncF2fh_LWv2bU",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(pulse),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent))))
        
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = ShinyGoldDeep, modifier = Modifier.size(18.dp))
                Text("AI ANALYSIS", color = ShinyGoldDeep, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
            }
            Text("Match Critical Analysis", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
            Text(
                text = "Spin rate on last delivery increased by 15%. High probability of LBW on current trajectory.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(ShinyGoldDeep, Color.Transparent))))
    }
}

@Composable
private fun DrsVisualizationCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(260.dp).background(Color.Black)) {
                CoilImage(
                    model = "https://lh3.googleusercontent.com/aida-public/AB6AXuA6T6d-zv1BRdhsmpp-0BhV0odojwS53qBMSmUdT26TTAQmTFcr7lg3xz6HHFAcndWgcixlE62Igp8YaT1UgnEBr9e8GQs8vsdEz7nAKeSKHh9iOTua2lvg4c5dYm0Q_lSbxNfIqmshj6OfwSKLEoLPqYmf6R_pKDONro2cplEIzQBariJGYewQ_mL0dyGQtpeVRX-9xdJrI3HuOyzwuFfBlfRDcT98mlOuCBWkZ0KgQoseTgVICmyV4f7LicKbT8vrVnA7i5EHXVY",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().alpha(0.95f),
                    contentScale = ContentScale.Crop
                )
                
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.4f)),
                    modifier = Modifier.padding(20.dp).align(Alignment.TopStart)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pulseAnim = rememberInfiniteTransition(label = "drs_pulse")
                        val dotAlpha by pulseAnim.animateFloat(initialValue = 1f, targetValue = 0.3f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
                        Box(modifier = Modifier.size(8.dp).alpha(dotAlpha).background(Color(0xFF00F0FF), CircleShape).shadow(4.dp, CircleShape, spotColor = Color(0xFF00F0FF)))
                        Text("REVIEW IN PROGRESS", color = Color(0xFF00F0FF), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    }
                }
                
                Column(modifier = Modifier.padding(20.dp).align(Alignment.BottomEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DrsIndicator("PITCHING", "IN LINE", Color(0xFF00F0FF))
                    DrsIndicator("IMPACT", "IN LINE", Color(0xFF00F0FF))
                    DrsIndicator("WICKETS", "HITTING", ShinyGoldDeep)
                }
            }
            
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DRS Visualization", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("Projected path indicates ball hitting top of leg stump.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DrsIndicator(label: String, value: String, color: Color) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun VerdictModule() {
    val infiniteTransition = rememberInfiniteTransition(label = "verdict")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 10.dp.value,
        targetValue = 25.dp.value,
        animationSpec = infiniteRepeatable(animation = tween(2000), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(120.dp, 60.dp).background(ShinyGoldDeep.copy(alpha = 0.1f), CircleShape).blur(glowIntensity.dp))
            Text(
                "OUT",
                color = ShinyGoldDeep,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-2).sp,
                modifier = Modifier.drawBehind {
                    // Manual glow drawing if needed
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Umpire's Decision Overturned".uppercase(), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

@Composable
private fun DeliveryMetricsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(4.dp, 18.dp).background(ShinyGoldDeep, RoundedCornerShape(2.dp)))
                Text("DELIVERY METRICS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
            Icon(Icons.Default.BarChart, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MetricCard(modifier = Modifier.weight(1f), label = "Impact Speed", value = "142", unit = "kph", icon = Icons.Default.Speed)
            MetricCard(modifier = Modifier.weight(1f), label = "Turn Degree", value = "2.1°", icon = Icons.Default.RotateRight)
            MetricCard(modifier = Modifier.weight(1f), label = "Confidence", value = "98%", icon = Icons.Default.AutoAwesome)
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, label: String, value: String, unit: String = "", icon: ImageVector) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.06f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = ShinyGoldDeep, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(label.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    if (unit.isNotEmpty()) {
                        Text(unit, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PitchMapMiniCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable { /* Detail View */ }
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Map, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                }
                Column {
                    Text("Pitch Map", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("Good Length", color = ShinyGoldDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
        }
    }
}
