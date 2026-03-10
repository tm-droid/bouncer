package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tanush.bouncer.ui.components.glass.GlassCard
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// =============================================================================
// Premium Bouncer Colors (Hardcoded for Reliability)
// =============================================================================

private object UpdateDialogColors {
    val BackgroundScrim = Color(0xCC000000)
    val SurfaceDark = Color(0xFF1A1D23)
    val Background = Color(0xFF141720)
    val GoldPrimary = Color(0xFFD4AF37)
    val GoldLight = Color(0xFFE8C84A)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFAAAAAA)
    val BorderColor = Color(0xFF2A2D35)
    val ErrorRed = Color(0xFFEF4444)
}

// =============================================================================
// AppUpdateDialog — Premium Full-Screen Update Notification
// =============================================================================

/**
 * Elite app update dialog with glass-morphism and orchestrated animations.
 * Designed for Bouncer's premium aesthetic.
 *
 * @param currentVersion Current app version (e.g., "1.2.0")
 * @param newVersion Available version (e.g., "1.3.0")
 * @param releaseNotes List of bullet points describing what's new
 * @param isForceUpdate If true, hides dismiss options (critical update)
 * @param onUpdate Callback when user taps "Update Now"
 * @param onDismiss Callback when user dismisses the dialog
 * @param onRemindLater Callback when user taps "Remind Me Later"
 */
@Composable
fun AppUpdateDialog(
    currentVersion: String,
    newVersion: String,
    releaseNotes: List<String>,
    isForceUpdate: Boolean = false,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onRemindLater: () -> Unit
) {
    // Track dialog show in Sentry
    LaunchedEffect(Unit) {
        Sentry.addBreadcrumb(
            Breadcrumb.info("AppUpdateDialog shown").apply {
                category = "ui.update"
                setData("current_version", currentVersion)
                setData("new_version", newVersion)
                setData("is_force_update", isForceUpdate.toString())
                level = SentryLevel.INFO
            }
        )
    }

    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    Dialog(
        onDismissRequest = {
            if (!isForceUpdate) {
                Sentry.addBreadcrumb(
                    Breadcrumb.info("AppUpdateDialog dismissed").apply {
                        category = "ui.update"
                        level = SentryLevel.INFO
                    }
                )
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isForceUpdate,
            dismissOnClickOutside = !isForceUpdate
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UpdateDialogColors.BackgroundScrim)
                .clickable(
                    enabled = !isForceUpdate,
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialScale = 0.8f
                ) + fadeIn(animationSpec = tween(300)),
                exit = scaleOut(
                    animationSpec = tween(200),
                    targetScale = 0.9f
                ) + fadeOut(animationSpec = tween(200))
            ) {
                UpdateDialogContent(
                    currentVersion = currentVersion,
                    newVersion = newVersion,
                    releaseNotes = releaseNotes,
                    isForceUpdate = isForceUpdate,
                    onUpdate = {
                        Sentry.addBreadcrumb(
                            Breadcrumb.info("Update button tapped").apply {
                                category = "ui.update"
                                setData("version", newVersion)
                                level = SentryLevel.INFO
                            }
                        )
                        onUpdate()
                    },
                    onDismiss = {
                        Sentry.addBreadcrumb(
                            Breadcrumb.info("Update dialog close tapped").apply {
                                category = "ui.update"
                                level = SentryLevel.INFO
                            }
                        )
                        onDismiss()
                    },
                    onRemindLater = {
                        Sentry.addBreadcrumb(
                            Breadcrumb.info("Remind later tapped").apply {
                                category = "ui.update"
                                level = SentryLevel.INFO
                            }
                        )
                        onRemindLater()
                    }
                )
            }
        }
    }
}

@Composable
private fun UpdateDialogContent(
    currentVersion: String,
    newVersion: String,
    releaseNotes: List<String>,
    isForceUpdate: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onRemindLater: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .wrapContentHeight()
            .clickable(
                enabled = false,
                onClick = {},
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        useGoldBorder = true,
        showNoiseOverlay = true
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated Icon
                AnimatedUpdateIcon()

                Spacer(modifier = Modifier.height(8.dp))

                // Title
                AnimatedContent(targetState = true, label = "title_anim") {
                    Text(
                        text = "A New Bouncer Awaits",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = UpdateDialogColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }

                // Version Badge
                VersionBadge(
                    currentVersion = currentVersion,
                    newVersion = newVersion
                )

                // Release Notes
                if (releaseNotes.isNotEmpty()) {
                    ReleaseNotesList(releaseNotes)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Update Button
                GoldGradientButton(
                    text = "Update Now",
                    onClick = onUpdate
                )

                // Remind Later Button
                if (!isForceUpdate) {
                    TextButton(
                        onClick = onRemindLater,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Remind Me Later",
                            color = UpdateDialogColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Close Button (top-right)
            if (!isForceUpdate) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = UpdateDialogColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedUpdateIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "icon_rotation"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        UpdateDialogColors.GoldPrimary.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Update",
            tint = UpdateDialogColors.GoldPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun VersionBadge(
    currentVersion: String,
    newVersion: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = UpdateDialogColors.SurfaceDark.copy(alpha = 0.6f),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "v$currentVersion",
                fontSize = 14.sp,
                color = UpdateDialogColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )
            
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = UpdateDialogColors.GoldPrimary,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = "v$newVersion",
                fontSize = 14.sp,
                color = UpdateDialogColors.GoldPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReleaseNotesList(notes: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "What's New:",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = UpdateDialogColors.TextPrimary
        )
        
        notes.forEach { note ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gold bullet point
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = 6.dp)
                        .background(
                            UpdateDialogColors.GoldPrimary,
                            shape = CircleShape
                        )
                )
                
                Text(
                    text = note,
                    fontSize = 14.sp,
                    color = UpdateDialogColors.TextSecondary,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GoldGradientButton(
    text: String,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind {
                val shimmerWidth = size.width * 0.3f
                val shimmerStart = (size.width + shimmerWidth) * (shimmerOffset + 1f) / 2f - shimmerWidth

                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            UpdateDialogColors.GoldLight.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        startX = shimmerStart,
                        endX = shimmerStart + shimmerWidth
                    )
                )
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            UpdateDialogColors.GoldPrimary,
                            UpdateDialogColors.GoldLight,
                            UpdateDialogColors.GoldPrimary
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = UpdateDialogColors.Background
            )
        }
    }
}

// =============================================================================
// AppUpdateBanner — Compact Non-Intrusive Update Notification
// =============================================================================

/**
 * Compact banner that slides down from the top for non-critical updates.
 * Auto-dismisses after 8 seconds or can be swiped away.
 *
 * @param version The available version (e.g., "1.3.0")
 * @param onUpdate Callback when user taps "Update" button
 * @param onDismiss Callback when banner is dismissed
 */
@Composable
fun AppUpdateBanner(
    version: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    // Auto-dismiss after 8 seconds
    LaunchedEffect(Unit) {
        Sentry.addBreadcrumb(
            Breadcrumb.info("AppUpdateBanner shown").apply {
                category = "ui.update.banner"
                setData("version", version)
                level = SentryLevel.INFO
            }
        )
        
        delay(8000)
        if (visible) {
            visible = false
            delay(300)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Gold accent strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                UpdateDialogColors.GoldPrimary,
                                UpdateDialogColors.GoldLight,
                                UpdateDialogColors.GoldPrimary
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
            )

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (Math.abs(offsetX) > 100) {
                                    scope.launch {
                                        Sentry.addBreadcrumb(
                                            Breadcrumb
                                                .info("AppUpdateBanner swiped")
                                                .apply {
                                                    category = "ui.update.banner"
                                                    level = SentryLevel.INFO
                                                }
                                        )
                                        visible = false
                                        delay(300)
                                        onDismiss()
                                    }
                                } else {
                                    scope.launch {
                                        animate(
                                            initialValue = offsetX,
                                            targetValue = 0f,
                                            animationSpec = spring()
                                        ) { value, _ ->
                                            offsetX = value
                                        }
                                    }
                                }
                            }
                        ) { _, dragAmount ->
                            offsetX += dragAmount
                        }
                    },
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp
                ),
                contentPadding = PaddingValues(16.dp),
                useGoldBorder = false
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = UpdateDialogColors.GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Text(
                            text = "Update available v$version",
                            fontSize = 14.sp,
                            color = UpdateDialogColors.TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Update Pill Button
                    Surface(
                        onClick = {
                            Sentry.addBreadcrumb(
                                Breadcrumb.info("AppUpdateBanner update tapped").apply {
                                    category = "ui.update.banner"
                                    setData("version", version)
                                    level = SentryLevel.INFO
                                }
                            )
                            onUpdate()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = UpdateDialogColors.GoldPrimary,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Update",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = UpdateDialogColors.Background
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Previews
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun PreviewAppUpdateDialog() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UpdateDialogColors.Background)
    ) {
        AppUpdateDialog(
            currentVersion = "1.2.0",
            newVersion = "1.3.0",
            releaseNotes = listOf(
                "New live score animations with particle effects",
                "Enhanced player stats with interactive charts",
                "Performance improvements and bug fixes",
                "Dark mode refinements for OLED displays"
            ),
            isForceUpdate = false,
            onUpdate = {},
            onDismiss = {},
            onRemindLater = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun PreviewAppUpdateDialogForce() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UpdateDialogColors.Background)
    ) {
        AppUpdateDialog(
            currentVersion = "1.2.0",
            newVersion = "2.0.0",
            releaseNotes = listOf(
                "Critical security update",
                "Required API changes"
            ),
            isForceUpdate = true,
            onUpdate = {},
            onDismiss = {},
            onRemindLater = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun PreviewAppUpdateBanner() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UpdateDialogColors.Background)
    ) {
        AppUpdateBanner(
            version = "1.3.0",
            onUpdate = {},
            onDismiss = {}
        )
    }
}
