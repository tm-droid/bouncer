package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aboutShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val colors = BouncerDesignSystem.colors

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "ABOUT",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = colors.goldDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        GlassBloomBackground(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // App Logo with glow
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                        .size(140.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.3f),
                                    colors.goldPrimary.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = CircleShape,
                                spotColor = colors.goldPrimary.copy(alpha = 0.5f)
                            )
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                ),
                                CircleShape
                            )
                            .border(
                                2.dp,
                                Brush.linearGradient(
                                    colors = listOf(
                                        colors.goldLight.copy(alpha = 0.6f + shimmerOffset * 0.4f),
                                        colors.goldPrimary.copy(alpha = 0.4f),
                                        colors.goldDark.copy(alpha = 0.6f)
                                    )
                                ),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsCricket,
                            contentDescription = null,
                            tint = colors.goldPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // App Name
                Text(
                    text = "BOUNCER",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    style = LocalTextStyle.current.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colors.goldLight,
                                colors.goldDark,
                                colors.goldPrimary
                            )
                        )
                    )
                )

                Text(
                    text = "Live Cricket Scores",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    letterSpacing = 2.sp
                )

                // Version Badge
                Surface(
                    color = colors.goldPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, colors.goldPrimary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Version 1.0.0",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.goldPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Features Section
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "FEATURES",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.goldPrimary,
                            letterSpacing = 2.sp
                        )

                        FeatureItem(
                            icon = Icons.Default.Bolt,
                            title = "Real-Time Updates",
                            description = "Ball-by-ball live scores with instant notifications"
                        )

                        FeatureItem(
                            icon = Icons.Default.Analytics,
                            title = "Win Probability",
                            description = "AI-powered match predictions updated in real-time"
                        )

                        FeatureItem(
                            icon = Icons.Default.EmojiEvents,
                            title = "Achievements",
                            description = "Track milestones and celebrate cricket moments"
                        )

                        FeatureItem(
                            icon = Icons.Default.Favorite,
                            title = "Favorite Teams",
                            description = "Follow your teams and never miss a match"
                        )
                    }
                }

                // Credits Section
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    useGoldBorder = true,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "CREATED BY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textTertiary,
                            letterSpacing = 2.sp
                        )
                        
                        Text(
                            text = "A Cricket Enthusiast",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        
                        Text(
                            text = "Built with passion for the game",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Footer
                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.goldPrimary.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            RoundedCornerShape(50)
                        )
                )

                Text(
                    text = "Made with passion for cricket",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary
                )

                Text(
                    text = "2024",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.goldPrimary.copy(alpha = 0.5f),
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    val colors = BouncerDesignSystem.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(
                    colors.goldPrimary.copy(alpha = 0.12f),
                    RoundedCornerShape(14.dp)
                )
                .border(
                    0.5.dp,
                    colors.goldPrimary.copy(alpha = 0.2f),
                    RoundedCornerShape(14.dp)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.goldPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = description,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textTertiary,
                lineHeight = 18.sp
            )
        }
    }
}
