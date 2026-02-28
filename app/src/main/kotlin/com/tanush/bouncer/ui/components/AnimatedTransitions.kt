package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// ==========================================
// BOUNCER - Advanced Animated Transitions
// Material 3 Motion Specs Implementation
// ==========================================

/**
 * Material 3 Motion Easing Constants
 */
object M3Easing {
    // Standard: General purpose easing for subtle UI changes
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    
    // StandardDecelerate: Elements entering the screen
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    
    // StandardAccelerate: Elements leaving the screen
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    
    // Emphasized: Complex or important transitions
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    
    // EmphasizedDecelerate: Important elements entering
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    
    // EmphasizedAccelerate: Important elements exiting
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * Spring Configuration Presets
 */
object SpringPresets {
    // Bouncy: High energy, playful animations
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.6f,
        stiffness = 400f
    )
    
    // Snappy: Quick, responsive interactions
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = 600f
    )
    
    // Gentle: Subtle, calm transitions
    fun <T> gentle() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = 200f
    )
    
    // Default: Standard Material motion
    fun <T> default() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Slide direction for SlideInAnimation
 */
enum class SlideDirection {
    Left, Right, Up, Down
}

/**
 * SharedElementTransition - Hero animation composable for seamless content transitions
 * 
 * @param isVisible Controls visibility with animated transition
 * @param transitionKey Unique key for the shared element
 * @param modifier Modifier for the container
 * @param content The content to animate
 */
@Composable
fun SharedElementTransition(
    isVisible: Boolean,
    transitionKey: String,
    modifier: Modifier = Modifier,
    enterDuration: Int = AnimationDuration.normal,
    exitDuration: Int = AnimationDuration.quick,
    content: @Composable () -> Unit
) {
    val transition = updateTransition(targetState = isVisible, label = "shared_$transitionKey")
    
    val scale by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(enterDuration, easing = M3Easing.EmphasizedDecelerate)
            } else {
                tween(exitDuration, easing = M3Easing.EmphasizedAccelerate)
            }
        },
        label = "scale"
    ) { visible -> if (visible) 1f else 0.85f }
    
    val alpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(enterDuration, easing = M3Easing.StandardDecelerate)
            } else {
                tween(exitDuration, easing = M3Easing.StandardAccelerate)
            }
        },
        label = "alpha"
    ) { visible -> if (visible) 1f else 0f }
    
    val offsetY by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.75f, stiffness = 400f)
            } else {
                tween(exitDuration, easing = M3Easing.StandardAccelerate)
            }
        },
        label = "offsetY"
    ) { visible -> if (visible) 0.dp else 24.dp }
    
    if (isVisible || alpha > 0f) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    translationY = offsetY.toPx()
                }
        ) {
            content()
        }
    }
}

/**
 * MorphingContainer - Smoothly transforms between different content states
 * 
 * @param targetState The current state to display
 * @param modifier Modifier for the container
 * @param contentAlignment Alignment for content within the container
 * @param content Lambda receiving the state to render appropriate content
 */
@Composable
fun <T> MorphingContainer(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    animationDuration: Int = AnimationDuration.normal,
    content: @Composable (T) -> Unit
) {
    val transition = updateTransition(targetState, label = "morphing_container")
    
    Box(modifier = modifier, contentAlignment = contentAlignment) {
        transition.AnimatedContent(
            transitionSpec = {
                val enterTransition = fadeIn(
                    animationSpec = tween(animationDuration, easing = M3Easing.StandardDecelerate)
                ) + scaleIn(
                    animationSpec = tween(animationDuration, easing = M3Easing.EmphasizedDecelerate),
                    initialScale = 0.92f
                )
                
                val exitTransition = fadeOut(
                    animationSpec = tween(animationDuration / 2, easing = M3Easing.StandardAccelerate)
                ) + scaleOut(
                    animationSpec = tween(animationDuration / 2, easing = M3Easing.EmphasizedAccelerate),
                    targetScale = 0.92f
                )
                
                enterTransition togetherWith exitTransition using SizeTransform { initialSize, targetSize ->
                    if (targetState != initialState) {
                        keyframes {
                            durationMillis = animationDuration
                            initialSize at 0 using M3Easing.Standard
                            targetSize at animationDuration using M3Easing.EmphasizedDecelerate
                        }
                    } else {
                        spring(stiffness = Spring.StiffnessMedium)
                    }
                }
            }
        ) { state ->
            content(state)
        }
    }
}

/**
 * StaggeredListAnimation - Animates list items with sequential delayed entrances
 * 
 * @param index Item index in the list
 * @param visible Whether items should be visible
 * @param staggerDelay Delay between each item's animation start
 * @param modifier Modifier for the container
 * @param content The content to animate
 */
@Composable
fun StaggeredListAnimation(
    index: Int,
    visible: Boolean,
    staggerDelay: Long = AnimationDelay.stagger.toLong(),
    modifier: Modifier = Modifier,
    useSpring: Boolean = true,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(visible, index) {
        if (visible) {
            delay(staggerDelay * index)
            isVisible = true
        } else {
            isVisible = false
        }
    }
    
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 40f,
        animationSpec = if (useSpring) {
            SpringPresets.bouncy()
        } else {
            tween(AnimationDuration.normal, easing = M3Easing.EmphasizedDecelerate)
        },
        label = "stagger_offset_$index"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(AnimationDuration.quick, easing = M3Easing.StandardDecelerate),
        label = "stagger_alpha_$index"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.95f,
        animationSpec = if (useSpring) {
            SpringPresets.snappy()
        } else {
            tween(AnimationDuration.normal, easing = M3Easing.EmphasizedDecelerate)
        },
        label = "stagger_scale_$index"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = offsetY
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
    }
}

/**
 * WaveAnimation - Animated wave effect for pull-to-refresh indicators
 * 
 * @param isActive Whether the wave animation is active
 * @param waveCount Number of wave elements
 * @param waveColor Color of the wave elements
 * @param modifier Modifier for the container
 */
@Composable
fun WaveAnimation(
    isActive: Boolean,
    waveCount: Int = 5,
    waveColor: Color = ShinyGoldDeep,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_animation")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(waveCount) { index ->
            val delay = index * 100
            
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = 24f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delay,
                        easing = M3Easing.Standard
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_height_$index"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delay,
                        easing = M3Easing.Standard
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_alpha_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (isActive) height.dp else 8.dp)
                    .alpha(if (isActive) alpha else 0.4f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(waveColor)
            )
        }
    }
}

/**
 * FlipAnimation - Card flip animation for front/back transitions
 * 
 * @param isFlipped Whether the card is flipped to show back content
 * @param modifier Modifier for the container
 * @param flipDuration Animation duration in milliseconds
 * @param frontContent Content for the front side
 * @param backContent Content for the back side
 */
@Composable
fun FlipAnimation(
    isFlipped: Boolean,
    modifier: Modifier = Modifier,
    flipDuration: Int = AnimationDuration.moderate,
    frontContent: @Composable () -> Unit,
    backContent: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(
            durationMillis = flipDuration,
            easing = M3Easing.Emphasized
        ),
        label = "flip_rotation"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
    ) {
        if (rotation <= 90f) {
            Box(
                modifier = Modifier.graphicsLayer {
                    this.alpha = if (rotation <= 90f) 1f else 0f
                }
            ) {
                frontContent()
            }
        } else {
            Box(
                modifier = Modifier.graphicsLayer {
                    rotationY = 180f
                    this.alpha = if (rotation > 90f) 1f else 0f
                }
            ) {
                backContent()
            }
        }
    }
}

/**
 * CounterAnimation - Animated number transitions for scores
 * Smoothly animates between numeric values
 * 
 * @param targetValue The target number to display
 * @param modifier Modifier for the text
 * @param textColor Color of the number text
 * @param fontSize Size of the text in sp
 * @param fontWeight Weight of the font
 * @param animationDuration Duration of the counting animation
 * @param prefix Optional prefix string (e.g., "$")
 * @param suffix Optional suffix string (e.g., "/10")
 */
@Composable
fun CounterAnimation(
    targetValue: Int,
    modifier: Modifier = Modifier,
    textColor: Color = TextPrimary,
    fontSize: Float = 32f,
    fontWeight: FontWeight = FontWeight.Bold,
    animationDuration: Int = AnimationDuration.moderate,
    prefix: String = "",
    suffix: String = ""
) {
    var previousValue by remember { mutableIntStateOf(targetValue) }
    var isAnimating by remember { mutableStateOf(false) }
    
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = M3Easing.EmphasizedDecelerate
        ),
        label = "counter_value"
    )
    
    LaunchedEffect(targetValue) {
        if (targetValue != previousValue) {
            isAnimating = true
            delay(animationDuration.toLong())
            isAnimating = false
            previousValue = targetValue
        }
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.1f else 1f,
        animationSpec = SpringPresets.bouncy(),
        label = "counter_scale"
    )
    
    val textColorAnimated by animateColorAsState(
        targetValue = if (isAnimating) ShinyGoldDeep else textColor,
        animationSpec = tween(AnimationDuration.quick),
        label = "counter_color"
    )
    
    Text(
        text = "$prefix$animatedValue$suffix",
        modifier = modifier.scale(scale),
        color = textColorAnimated,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        letterSpacing = (-0.5).sp
    )
}

/**
 * Animated score counter with emphasized updates
 * Specifically designed for cricket match scores (runs/wickets format)
 */
@Composable
fun ScoreCounterAnimation(
    runs: Int,
    wickets: Int,
    overs: String = "",
    modifier: Modifier = Modifier,
    runsColor: Color = TextPrimary,
    wicketsColor: Color = WicketRed,
    fontSize: Float = 36f
) {
    var previousRuns by remember { mutableIntStateOf(runs) }
    var previousWickets by remember { mutableIntStateOf(wickets) }
    var isRunsAnimating by remember { mutableStateOf(false) }
    var isWicketsAnimating by remember { mutableStateOf(false) }
    
    LaunchedEffect(runs) {
        if (runs != previousRuns) {
            isRunsAnimating = true
            delay(AnimationDuration.moderate.toLong())
            isRunsAnimating = false
            previousRuns = runs
        }
    }
    
    LaunchedEffect(wickets) {
        if (wickets != previousWickets) {
            isWicketsAnimating = true
            delay(AnimationDuration.moderate.toLong())
            isWicketsAnimating = false
            previousWickets = wickets
        }
    }
    
    val animatedRuns by animateIntAsState(
        targetValue = runs,
        animationSpec = tween(
            durationMillis = AnimationDuration.moderate,
            easing = M3Easing.EmphasizedDecelerate
        ),
        label = "runs_counter"
    )
    
    val runsScale by animateFloatAsState(
        targetValue = if (isRunsAnimating) 1.15f else 1f,
        animationSpec = SpringPresets.bouncy(),
        label = "runs_scale"
    )
    
    val wicketsScale by animateFloatAsState(
        targetValue = if (isWicketsAnimating) 1.2f else 1f,
        animationSpec = SpringPresets.bouncy(),
        label = "wickets_scale"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = animatedRuns.toString(),
            modifier = Modifier.scale(runsScale),
            color = if (isRunsAnimating) ShinyGoldDeep else runsColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp
        )
        
        Text(
            text = "/",
            color = TextSecondary,
            fontSize = (fontSize * 0.8f).sp,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = wickets.toString(),
            modifier = Modifier.scale(wicketsScale),
            color = if (isWicketsAnimating) WicketRed else wicketsColor,
            fontSize = (fontSize * 0.85f).sp,
            fontWeight = FontWeight.Bold
        )
        
        if (overs.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($overs)",
                color = TextSecondary,
                fontSize = (fontSize * 0.5f).sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * SlideInAnimation - Configurable slide entrance with spring physics
 * 
 * @param visible Whether the content is visible
 * @param direction Direction from which content slides in
 * @param slideDistance Distance of the slide in dp
 * @param modifier Modifier for the container
 * @param useSpring Whether to use spring physics (true) or tween (false)
 * @param content The content to animate
 */
@Composable
fun SlideInAnimation(
    visible: Boolean,
    direction: SlideDirection = SlideDirection.Up,
    slideDistance: Dp = 48.dp,
    modifier: Modifier = Modifier,
    useSpring: Boolean = true,
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(visible) {
        if (visible) {
            if (delayMs > 0) delay(delayMs.toLong())
            isVisible = true
        } else {
            isVisible = false
        }
    }
    
    val density = LocalDensity.current
    val slideDistancePx = with(density) { slideDistance.toPx() }
    
    val initialOffset = when (direction) {
        SlideDirection.Left -> Offset(-slideDistancePx, 0f)
        SlideDirection.Right -> Offset(slideDistancePx, 0f)
        SlideDirection.Up -> Offset(0f, slideDistancePx)
        SlideDirection.Down -> Offset(0f, -slideDistancePx)
    }
    
    val offset by animateOffsetAsState(
        targetValue = if (isVisible) Offset.Zero else initialOffset,
        animationSpec = if (useSpring) {
            SpringPresets.bouncy()
        } else {
            tween(AnimationDuration.normal, easing = M3Easing.EmphasizedDecelerate)
        },
        label = "slide_offset"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = AnimationDuration.quick,
            easing = M3Easing.StandardDecelerate
        ),
        label = "slide_alpha"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offset.x
                translationY = offset.y
                this.alpha = alpha
            }
    ) {
        content()
    }
}

/**
 * Animated offset state helper
 */
@Composable
private fun animateOffsetAsState(
    targetValue: Offset,
    animationSpec: AnimationSpec<Offset>,
    label: String
): State<Offset> {
    val animatedX by animateFloatAsState(
        targetValue = targetValue.x,
        animationSpec = when (animationSpec) {
            is SpringSpec<Offset> -> spring(
                dampingRatio = animationSpec.dampingRatio,
                stiffness = animationSpec.stiffness
            )
            is TweenSpec<Offset> -> tween(
                durationMillis = animationSpec.durationMillis,
                delayMillis = animationSpec.delay,
                easing = animationSpec.easing
            )
            else -> SpringPresets.default()
        },
        label = "${label}_x"
    )
    
    val animatedY by animateFloatAsState(
        targetValue = targetValue.y,
        animationSpec = when (animationSpec) {
            is SpringSpec<Offset> -> spring(
                dampingRatio = animationSpec.dampingRatio,
                stiffness = animationSpec.stiffness
            )
            is TweenSpec<Offset> -> tween(
                durationMillis = animationSpec.durationMillis,
                delayMillis = animationSpec.delay,
                easing = animationSpec.easing
            )
            else -> SpringPresets.default()
        },
        label = "${label}_y"
    )
    
    return remember(animatedX, animatedY) {
        derivedStateOf { Offset(animatedX, animatedY) }
    }
}

/**
 * Cascade entrance animation for multiple elements
 * Elements appear one after another with a cascading effect
 */
@Composable
fun CascadeEntranceAnimation(
    visible: Boolean,
    itemCount: Int,
    modifier: Modifier = Modifier,
    staggerDelayMs: Int = 50,
    content: @Composable (index: Int, animationProgress: Float) -> Unit
) {
    var targetState by remember { mutableStateOf(false) }
    
    LaunchedEffect(visible) {
        targetState = visible
    }
    
    Column(modifier = modifier) {
        repeat(itemCount) { index ->
            var itemVisible by remember { mutableStateOf(false) }
            
            LaunchedEffect(targetState) {
                if (targetState) {
                    delay((staggerDelayMs * index).toLong())
                    itemVisible = true
                } else {
                    delay((staggerDelayMs * (itemCount - index - 1)).toLong())
                    itemVisible = false
                }
            }
            
            val progress by animateFloatAsState(
                targetValue = if (itemVisible) 1f else 0f,
                animationSpec = SpringPresets.bouncy(),
                label = "cascade_progress_$index"
            )
            
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 30f
                        scaleX = 0.9f + (progress * 0.1f)
                        scaleY = 0.9f + (progress * 0.1f)
                    }
            ) {
                content(index, progress)
            }
        }
    }
}

/**
 * Pulsating scale animation for attention-grabbing elements
 */
@Composable
fun PulsatingElement(
    modifier: Modifier = Modifier,
    minScale: Float = PulseScale.minNormal,
    maxScale: Float = PulseScale.maxNormal,
    pulseDuration: Int = AnimationDuration.pulse,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsating")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = pulseDuration,
                easing = M3Easing.Standard
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    Box(
        modifier = modifier.scale(scale)
    ) {
        content()
    }
}

/**
 * Shimmer transition effect for loading states
 */
@Composable
fun ShimmerTransition(
    modifier: Modifier = Modifier,
    shimmerWidth: Dp = 150.dp,
    shimmerDuration: Int = AnimationDuration.shimmer,
    content: @Composable (shimmerOffset: Float) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_transition")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = shimmerDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    Box(modifier = modifier) {
        content(shimmerOffset)
    }
}
