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
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.*

@Composable
fun PremiumAiInsightsTab(match: CricketMatch) {
    val scrollState = rememberScrollState()
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val dimens = BouncerDesignSystem.dimens
    val typography = BouncerDesignSystem.typography
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(dimens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.xl)
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
                        listOf(Color.Transparent, colors.backgroundBase.copy(alpha = 0.95f), colors.backgroundBase)
                    )
                )
                .padding(dimens.spacing.lg)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = BouncerDesignSystem.shapes.glassCard,
                    border = BorderStroke(1.dp, colors.glassBorderHighlight),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Text("FULL ANALYSIS", style = typography.labelLarge)
                }
                Button(
                    onClick = {},
                    modifier = Modifier.weight(2f).height(56.dp),
                    shape = BouncerDesignSystem.shapes.glassCard,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.goldPrimary, contentColor = Color.Black),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Default.Gavel, null, modifier = Modifier.size(dimens.iconSize.md))
                    Spacer(modifier = Modifier.width(dimens.spacing.sm))
                    Text("REQUEST REVIEW", style = typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AiNeuralBanner() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val infiniteTransition = rememberInfiniteTransition(label = "banner")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(animation = tween(4000), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        useGoldBorder = true,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CoilImage(
                model = "https://lh3.googleusercontent.com/aida-public/AB6AXuDsb8zoInOAQHjBtC0v9FTTkw8IqC3dtvi8A4nCNTV53qtEobfL9te1_VjsKiDXphFOC2kUQNmQaULYU2ELAjNUSkGWIrB0iFo8GSHMKZwYVtOO2DXQKTboZfjRW7ty2xB2HztlFdmonHfWXtnzUYMD89sYx9plVY8yDtGuFsn9rgHY2D7Qu4pTNQHEUrwrs84Wzl9jZMtReEvqZvnLM7uBgOfycETUXv-bxW21XZspH8qHPoz-2xO-M9e9XsZDwzncF2fh_LWv2bU",
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(pulse),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent))))
            
            Column(modifier = Modifier.padding(dimens.spacing.lg), verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(dimens.iconSize.sm))
                    Text("AI ANALYSIS", style = typography.labelLarge, color = hierarchy.secondary.primary)
                }
                Text("Match Critical Analysis", style = typography.headingLarge, color = colors.textPrimary)
                Text(
                    text = "Spin rate on last delivery increased by 15%. High probability of LBW on current trajectory.",
                    style = typography.bodyMedium,
                    color = colors.textPrimary.copy(alpha = 0.8f)
                )
            }
            
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(hierarchy.secondary.primary, Color.Transparent))))
        }
    }
}

@Composable
private fun DrsVisualizationCard() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val drsAccentColor = colors.statusInfo // Cyan-blue for DRS visuals

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        useGoldBorder = true,
        contentPadding = PaddingValues(0.dp)
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
                    shape = RoundedCornerShape(dimens.radius.sm),
                    border = BorderStroke(1.dp, drsAccentColor.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(dimens.spacing.lg).align(Alignment.TopStart)
                ) {
                    Row(modifier = Modifier.padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                        val pulseAnim = rememberInfiniteTransition(label = "drs_pulse")
                        val dotAlpha by pulseAnim.animateFloat(initialValue = 1f, targetValue = 0.3f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
                        Box(modifier = Modifier.size(dimens.liveDotSize).alpha(dotAlpha).background(drsAccentColor, CircleShape).shadow(4.dp, CircleShape, spotColor = drsAccentColor))
                        Text("REVIEW IN PROGRESS", style = typography.labelMedium, color = drsAccentColor)
                    }
                }
                
                Column(modifier = Modifier.padding(dimens.spacing.lg).align(Alignment.BottomEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
                    DrsIndicator("PITCHING", "IN LINE", drsAccentColor)
                    DrsIndicator("IMPACT", "IN LINE", drsAccentColor)
                    DrsIndicator("WICKETS", "HITTING", hierarchy.secondary.primary)
                }
            }
            
            Column(modifier = Modifier.padding(dimens.spacing.lg), verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
                Text("DRS Visualization", style = typography.headingMedium, color = colors.textPrimary)
                Text("Projected path indicates ball hitting top of leg stump.", style = typography.bodyMedium, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DrsIndicator(label: String, value: String, color: Color) {
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(dimens.radius.xs),
        border = BorderStroke(0.5.dp, GlassBorder)
    ) {
        Row(modifier = Modifier.padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
            Text(label, style = typography.labelSmall, color = TextSecondary)
            Text(value, style = typography.labelMedium, color = color, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun VerdictModule() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val infiniteTransition = rememberInfiniteTransition(label = "verdict")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 10.dp.value,
        targetValue = 25.dp.value,
        animationSpec = infiniteRepeatable(animation = tween(2000), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = dimens.spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(120.dp, 60.dp).background(hierarchy.secondary.primary.copy(alpha = 0.1f), CircleShape).blur(glowIntensity.dp))
            Text(
                "OUT",
                color = hierarchy.secondary.primary,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-2).sp,
                modifier = Modifier.drawBehind {
                    // Manual glow drawing if needed
                }
            )
        }
        Spacer(modifier = Modifier.height(dimens.spacing.sm))
        Text("Umpire's Decision Overturned".uppercase(), style = typography.labelMedium, color = colors.textMuted)
    }
}

@Composable
private fun DeliveryMetricsSection() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                Box(modifier = Modifier.size(4.dp, 18.dp).background(hierarchy.secondary.primary, RoundedCornerShape(2.dp)))
                Text("DELIVERY METRICS", style = typography.labelLarge, color = colors.textPrimary)
            }
            Icon(Icons.Default.BarChart, null, tint = colors.textMuted, modifier = Modifier.size(dimens.iconSize.md))
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.cardGap)) {
            MetricCard(modifier = Modifier.weight(1f), label = "Impact Speed", value = "142", unit = "kph", icon = Icons.Default.Speed)
            MetricCard(modifier = Modifier.weight(1f), label = "Turn Degree", value = "2.1°", icon = Icons.AutoMirrored.Filled.RotateRight)
            MetricCard(modifier = Modifier.weight(1f), label = "Confidence", value = "98%", icon = Icons.Default.AutoAwesome)
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, label: String, value: String, unit: String = "", icon: ImageVector) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = modifier,
        useGoldBorder = true,
        contentPadding = PaddingValues(dimens.spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
            Box(modifier = Modifier.size(36.dp).background(hierarchy.secondary.tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(dimens.iconSize.sm))
            }
            Column {
                Text(label.uppercase(), style = typography.labelSmall, color = colors.textMuted)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value, style = typography.statValue, color = colors.textPrimary)
                    if (unit.isNotEmpty()) {
                        Text(unit, style = typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PitchMapMiniCard() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Detail View */ },
        useGoldBorder = true,
        contentPadding = PaddingValues(dimens.spacing.lg)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
                Box(modifier = Modifier.size(48.dp).background(hierarchy.secondary.tint, RoundedCornerShape(dimens.radius.md)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Map, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(dimens.iconSize.lg))
                }
                Column {
                    Text("Pitch Map", style = typography.headingSmall, color = colors.textPrimary)
                    Text("Good Length", style = typography.bodySmall, color = hierarchy.secondary.primary, fontWeight = FontWeight.Bold)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = colors.textMuted)
        }
    }
}
