package com.tanush.bouncer.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ==========================================
// BOUNCER – Onboarding Screen v3.0 (SPECTACULAR)
// Design Spec: Duolingo-level quality
//
// 5-page experience with premium animations:
// 1. Welcome (animated cricket ball, particles)
// 2. Live Intelligence (building scorecard)
// 3. Personalize (team selection grid)
// 4. Smart Notifications (sliding notification cards)
// 5. Get Started (celebration burst)
//
// Features: parallax, custom pill indicators, gradient backgrounds,
// smooth crossfades, haptic feedback, SharedPreferences persistence
// ==========================================

// Premium colors
private val BlackBackground = Color(0xFF0A0C10)
private val ShinyGoldDeep = Color(0xFFD4AF37)
private val GoldLight = Color(0xFFE8C84A)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextSecondary = Color(0xFFAAAAAA)
private val CardSurface = Color(0xFF141720)

private enum class OnboardingPageType {
    WELCOME,
    LIVE_INTELLIGENCE,
    PERSONALIZE,
    SMART_NOTIFICATIONS,
    GET_STARTED
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })
    val isLastPage = pagerState.currentPage == 4

    // Gradient background variations per page
    val backgroundGradient = remember(pagerState.currentPage) {
        when (pagerState.currentPage) {
            0 -> listOf(BlackBackground, Color(0xFF0D0F15))
            1 -> listOf(BlackBackground, Color(0xFF0F1118))
            2 -> listOf(BlackBackground, Color(0xFF0E1016))
            3 -> listOf(BlackBackground, Color(0xFF0C0E13))
            4 -> listOf(Color(0xFF0F0E12), Color(0xFF0A0C10))
            else -> listOf(BlackBackground, BlackBackground)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = backgroundGradient
                )
            )
    ) {
        // Parallax particle background
        ParticleBackground(pagerState.currentPage + pagerState.currentPageOffsetFraction)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button — top right (hide on last page)
            AnimatedVisibility(
                visible = !isLastPage,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        saveOnboardingComplete(context)
                        onComplete()
                    }) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Main pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 0.dp
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        0 -> WelcomePage(pageOffset)
                        1 -> LiveIntelligencePage(pageOffset)
                        2 -> PersonalizePage(pageOffset)
                        3 -> SmartNotificationsPage(pageOffset)
                        4 -> GetStartedPage(pageOffset)
                    }
                }
            }

            // Custom pill page indicator
            PillPageIndicator(
                pageCount = 5,
                currentPage = pagerState.currentPage,
                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button (hide on first page)
                AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ShinyGoldDeep.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ShinyGoldDeep
                        )
                    ) {
                        Text(
                            text = "Back",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Continue / Get Started button
                val buttonModifier = if (pagerState.currentPage > 0) {
                    Modifier.weight(1f)
                } else {
                    Modifier.fillMaxWidth()
                }

                Button(
                    onClick = {
                        if (isLastPage) {
                            saveOnboardingComplete(context)
                            // Sentry breadcrumb
                            Sentry.addBreadcrumb(
                                Breadcrumb().apply {
                                    category = "onboarding"
                                    message = "User completed onboarding"
                                    level = SentryLevel.INFO
                                }
                            )
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = buttonModifier.height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brush.horizontalGradient(
                            listOf(ShinyGoldDeep, GoldLight)
                        ).let { ShinyGoldDeep },
                        contentColor = BlackBackground
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Continue",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// Save onboarding completion to SharedPreferences
private fun saveOnboardingComplete(context: Context) {
    context.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("onboarding_complete", true)
        .apply()
}

// ==========================================
// CUSTOM PILL PAGE INDICATOR
// ==========================================

@Composable
private fun PillPageIndicator(
    pageCount: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(pageCount) { index ->
            val isActive = currentPage == index
            val isNext = currentPage + 1 == index && currentPageOffsetFraction > 0
            val isPrev = currentPage - 1 == index && currentPageOffsetFraction < 0
            
            val width by animateDpAsState(
                targetValue = when {
                    isActive -> 32.dp
                    isNext || isPrev -> 16.dp
                    else -> 8.dp
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "pill_width_$index"
            )
            
            val color by animateColorAsState(
                targetValue = if (isActive) ShinyGoldDeep else TextSecondary.copy(alpha = 0.3f),
                animationSpec = tween(300),
                label = "pill_color_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

// ==========================================
// PARTICLE BACKGROUND
// ==========================================

@Composable
private fun ParticleBackground(scrollOffset: Float) {
    val particles = remember {
        List(20) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1f,
                speed = Random.nextFloat() * 0.5f + 0.3f
            )
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.4f }
    ) {
        particles.forEach { particle ->
            val x = size.width * particle.x + (scrollOffset * particle.speed * 100)
            val y = size.height * particle.y
            drawCircle(
                color = ShinyGoldDeep.copy(alpha = 0.2f),
                radius = particle.size,
                center = Offset(x % size.width, y)
            )
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float
)

// ==========================================
// PAGE 1: WELCOME
// ==========================================

@Composable
private fun WelcomePage(pageOffset: Float) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(150)
        isVisible = true
    }

    val ballOffset by animateDpAsState(
        targetValue = if (isVisible) 0.dp else (-400).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ball_bounce"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .graphicsLayer {
                translationX = pageOffset * 200f
                alpha = 1f - kotlin.math.abs(pageOffset * 0.5f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated cricket ball
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(y = ballOffset)
                .drawBehind {
                    // Outer glow
                    drawCircle(
                        color = ShinyGoldDeep.copy(alpha = 0.2f),
                        radius = size.minDimension / 2f + 20f
                    )
                    // Ball base
                    drawCircle(
                        color = CardSurface,
                        radius = size.minDimension / 2f
                    )
                    // Cricket seam
                    drawCircle(
                        color = ShinyGoldDeep,
                        radius = size.minDimension / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                    // Seam line
                    drawLine(
                        color = ShinyGoldDeep,
                        start = Offset(size.width * 0.3f, size.height * 0.5f),
                        end = Offset(size.width * 0.7f, size.height * 0.5f),
                        strokeWidth = 2f
                    )
                }
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Title with fade in
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(600, delayMillis = 200)) + slideInVertically { -20 }
        ) {
            Text(
                text = "Welcome to Bouncer",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subtitle with slide up
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 600, delayMillis = 600)
            ) + slideInVertically(
                animationSpec = tween(durationMillis = 600, delayMillis = 600),
                initialOffsetY = { 30 }
            )
        ) {
            Text(
                text = "Your premium cricket companion",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// PAGE 2: LIVE INTELLIGENCE
// ==========================================

@Composable
private fun LiveIntelligencePage(pageOffset: Float) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(pageOffset) {
        isVisible = kotlin.math.abs(pageOffset) < 0.5f
        if (isVisible) delay(200)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .graphicsLayer {
                translationX = pageOffset * 200f
                alpha = 1f - kotlin.math.abs(pageOffset * 0.5f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated scorecard mock-up
        AnimatedScoreCard(isVisible)

        Spacer(modifier = Modifier.height(48.dp))

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(600, delayMillis = 400)) + slideInVertically { 20 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Live Intelligence",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Live scores, ball-by-ball updates,\nand AI-powered insights",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
private fun AnimatedScoreCard(isVisible: Boolean) {
    val score by animateIntAsState(
        targetValue = if (isVisible) 287 else 0,
        animationSpec = tween(1200, delayMillis = 200),
        label = "score"
    )
    val wickets by animateIntAsState(
        targetValue = if (isVisible) 4 else 0,
        animationSpec = tween(1200, delayMillis = 400),
        label = "wickets"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + scaleIn(tween(600, easing = EaseOutBack))
    ) {
        Box(
            modifier = Modifier
                .width(280.dp)
                .height(140.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(CardSurface, CardSurface.copy(alpha = 0.8f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(1.dp, ShinyGoldDeep.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "IND",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.SportsCricket,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$score/$wickets",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldLight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "32.4 overs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

// ==========================================
// PAGE 3: PERSONALIZE
// ==========================================

@Composable
private fun PersonalizePage(pageOffset: Float) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(pageOffset) {
        isVisible = kotlin.math.abs(pageOffset) < 0.5f
    }

    val teams = listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "WI", "SL")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .graphicsLayer {
                translationX = pageOffset * 200f
                alpha = 1f - kotlin.math.abs(pageOffset * 0.5f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(400)) + slideInVertically { -20 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Personalize",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Follow your favourite teams",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Team grid with staggered animations
        Column(
            modifier = Modifier
                .width(280.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            teams.chunked(4).forEachIndexed { rowIndex, rowTeams ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowTeams.forEachIndexed { colIndex, team ->
                        val index = rowIndex * 4 + colIndex
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(
                                tween(400, delayMillis = index * 80)
                            ) + scaleIn(
                                tween(400, delayMillis = index * 80),
                                initialScale = 0.3f
                            )
                        ) {
                            TeamChip(team)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamChip(teamCode: String) {
    var isSelected by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(
                color = if (isSelected) ShinyGoldDeep.copy(alpha = 0.2f) else CardSurface,
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 2.dp,
                color = if (isSelected) ShinyGoldDeep else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { isSelected = !isSelected },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = teamCode,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) GoldLight else TextPrimary
        )
    }
}

// ==========================================
// PAGE 4: SMART NOTIFICATIONS
// ==========================================

@Composable
private fun SmartNotificationsPage(pageOffset: Float) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(pageOffset) {
        isVisible = kotlin.math.abs(pageOffset) < 0.5f
    }

    val notifications = listOf(
        NotificationData("🏏 Wicket!", "Kohli out for 89", 0),
        NotificationData("💯 Century!", "Rohit reaches 100", 200),
        NotificationData("🔔 Match Starting", "IND vs AUS in 30 min", 400)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .graphicsLayer {
                translationX = pageOffset * 200f
                alpha = 1f - kotlin.math.abs(pageOffset * 0.5f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(400)) + slideInVertically { -20 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Smart Notifications",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Never miss a moment",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Stacked notification cards
        Column(
            modifier = Modifier.width(300.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            notifications.forEach { notif ->
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(
                        tween(500, delayMillis = notif.delay)
                    ) + slideInHorizontally(
                        tween(500, delayMillis = notif.delay),
                        initialOffsetX = { 400 }
                    )
                ) {
                    NotificationCard(notification = notif)
                }
            }
        }
    }
}

private data class NotificationData(
    val title: String,
    val message: String,
    val delay: Int
)

@Composable
private fun NotificationCard(notification: NotificationData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(CardSurface, CardSurface.copy(alpha = 0.95f))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, ShinyGoldDeep.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

// ==========================================
// PAGE 5: GET STARTED
// ==========================================

@Composable
private fun GetStartedPage(pageOffset: Float) {
    var isVisible by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    
    LaunchedEffect(pageOffset) {
        isVisible = kotlin.math.abs(pageOffset) < 0.5f
        if (isVisible) {
            delay(300)
            showConfetti = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = pageOffset * 200f
                alpha = 1f - kotlin.math.abs(pageOffset * 0.5f)
            }
    ) {
        // Confetti background
        if (showConfetti) {
            ConfettiEffect()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Celebration icon
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(600)) + scaleIn(
                    tween(800, easing = EaseOutBack),
                    initialScale = 0.3f
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(
                                    ShinyGoldDeep.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎉",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(600, delayMillis = 400)) + slideInVertically(
                    tween(600, delayMillis = 400)
                ) { 30 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "You're All Set!",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Experience elite cricket intelligence\nwith Bouncer",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Pulsing "Get Started" preview
            val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(600, delayMillis = 600)) + scaleIn(
                    tween(600, delayMillis = 600)
                )
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfettiEffect() {
    val confettiPieces = remember {
        List(30) {
            ConfettiPiece(
                x = Random.nextFloat(),
                y = Random.nextFloat() * 0.3f - 0.2f,
                color = listOf(ShinyGoldDeep, GoldLight, Color(0xFFFFD700), Color(0xFFFFA500)).random(),
                size = Random.nextFloat() * 8f + 4f,
                rotation = Random.nextFloat() * 360f
            )
        }
    }

    var animationTime by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            animationTime += 0.016f
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        confettiPieces.forEach { piece ->
            val y = (piece.y + animationTime * 0.3f) % 1.2f
            val x = piece.x + sin(animationTime * 2f + piece.rotation) * 0.05f
            
            drawCircle(
                color = piece.color,
                radius = piece.size,
                center = Offset(
                    x = size.width * x,
                    y = size.height * y
                )
            )
        }
    }
}

private data class ConfettiPiece(
    val x: Float,
    val y: Float,
    val color: Color,
    val size: Float,
    val rotation: Float
)
