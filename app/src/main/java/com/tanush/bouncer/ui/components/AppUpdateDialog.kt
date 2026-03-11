package com.tanush.bouncer.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.animations.BouncerMotionEasing
import com.tanush.bouncer.ui.animations.FadeSlideAnimation
import com.tanush.bouncer.ui.animations.HapticType
import com.tanush.bouncer.ui.animations.rememberHaptic
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.updates.AppUpdateUiState
import com.tanush.bouncer.ui.updates.UpdateFeature
import com.tanush.bouncer.ui.updates.UpdateFeatureType
import com.tanush.bouncer.ui.updates.UpdateInfo
import kotlinx.coroutines.delay

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onUpdateNow: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val updateInfo = when (state) {
        is AppUpdateUiState.UpdateAvailable -> state.info
        is AppUpdateUiState.Downloading -> state.info
        is AppUpdateUiState.ReadyToInstall -> state.info
        is AppUpdateUiState.Error -> state.info
        else -> null
    } ?: return

    val haptic = rememberHaptic()
    var visible by remember { mutableStateOf(false) }
    val isForceUpdate = updateInfo.forceUpdate
    val dialogContentDescription = stringResource(R.string.cd_update_dialog)

    val progressValue = when (state) {
        is AppUpdateUiState.Downloading -> state.progress
        is AppUpdateUiState.ReadyToInstall -> 100
        else -> null
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(state) {
        if (state is AppUpdateUiState.ReadyToInstall) {
            haptic.success()
        }
    }

    val enter: EnterTransition = remember {
        fadeIn(animationSpec = tween(400, easing = BouncerMotionEasing.EaseOutCubic)) +
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 2 },
                animationSpec = tween(400, easing = BouncerMotionEasing.EaseOutCubic)
            )
    }
    val exit: ExitTransition = remember {
        fadeOut(animationSpec = tween(200, easing = BouncerMotionEasing.EaseOutCubic)) +
            slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight / 3 },
                animationSpec = tween(200, easing = BouncerMotionEasing.EaseOutCubic)
            )
    }

    fun closeWithAnimation() {
        visible = false
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(200)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isForceUpdate) {
                closeWithAnimation()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = !isForceUpdate,
            dismissOnBackPress = !isForceUpdate
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(BouncerDesignSystem.colors.glassScrim.copy(alpha = 0.78f))
                .clickable(
                    enabled = !isForceUpdate,
                    onClick = { closeWithAnimation() }
                ),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                FadeSlideAnimation(
                    visible = visible,
                    modifier = modifier,
                    enter = enter,
                    exit = exit
                ) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .widthIn(max = 400.dp)
                            .semantics {
                                contentDescription = dialogContentDescription
                            }
                            .clickable(enabled = false, onClick = {}),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(0.dp),
                        useGoldBorder = true,
                        borderOpacity = 0.32f,
                        showNoiseOverlay = true
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                BouncerDesignSystem.colors.goldLight,
                                                BouncerDesignSystem.colors.goldPrimary,
                                                TeamPrimaryColors.IndiaBlue
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                UpdateHeader(updateInfo = updateInfo)
                                VersionSection(updateInfo = updateInfo)
                                FeatureList(features = updateInfo.features)
                                SizeAndTimeEstimate(updateInfo = updateInfo)

                                if (progressValue != null) {
                                    DownloadStateSection(progress = progressValue, state = state)
                                }

                                UpdateActions(
                                    state = state,
                                    isForceUpdate = isForceUpdate,
                                    onUpdateNow = {
                                        haptic.perform(HapticType.MediumImpact)
                                        onUpdateNow()
                                    },
                                    onInstall = {
                                        haptic.perform(HapticType.Success)
                                        onInstall()
                                    },
                                    onLater = {
                                        haptic.perform(HapticType.LightTap)
                                        onLater()
                                        closeWithAnimation()
                                    },
                                    onRetry = {
                                        haptic.perform(HapticType.MediumImpact)
                                        onRetry()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateHeader(updateInfo: UpdateInfo) {
    val typography = BouncerDesignSystem.typography
    val colors = BouncerDesignSystem.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.goldPrimary.copy(alpha = 0.15f))
                .border(1.dp, colors.goldPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.cd_update_bouncer_logo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(30.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.update_available_title),
                style = typography.headingMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = stringResource(
                    R.string.update_subtitle,
                    updateInfo.newVersion
                ),
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun VersionSection(updateInfo: UpdateInfo) {
    val colors = BouncerDesignSystem.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.backgroundElevated.copy(alpha = 0.8f))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.update_current_version, updateInfo.currentVersion),
            style = BouncerDesignSystem.typography.bodySmall,
            color = colors.textSecondary
        )
        Text(
            text = stringResource(R.string.update_version_arrow),
            style = BouncerDesignSystem.typography.headingSmall,
            color = TeamPrimaryColors.IndiaBlue,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.update_new_version, updateInfo.newVersion),
            style = BouncerDesignSystem.typography.bodySmall,
            color = colors.goldPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FeatureList(features: List<UpdateFeature>) {
    val colors = BouncerDesignSystem.colors

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.update_whats_new),
            style = BouncerDesignSystem.typography.headingSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )

        features.take(5).forEach { feature ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = feature.type.toIcon(),
                    contentDescription = stringResource(R.string.cd_update_feature_icon),
                    tint = colors.goldPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = feature.text,
                    style = BouncerDesignSystem.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SizeAndTimeEstimate(updateInfo: UpdateInfo) {
    val colors = BouncerDesignSystem.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = stringResource(R.string.cd_update_download_estimate),
            tint = TeamPrimaryColors.IndiaBlue,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(
                R.string.update_size_and_time,
                updateInfo.sizeMb,
                updateInfo.estimatedMinutes
            ),
            style = BouncerDesignSystem.typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun DownloadStateSection(progress: Int, state: AppUpdateUiState) {
    val colors = BouncerDesignSystem.colors

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (state) {
                    is AppUpdateUiState.ReadyToInstall -> stringResource(R.string.update_ready_to_install)
                    else -> stringResource(R.string.update_downloading)
                },
                style = BouncerDesignSystem.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.update_progress_percent, progress),
                style = BouncerDesignSystem.typography.bodySmall,
                color = colors.goldPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        GradientProgressBar(progress = progress / 100f)

        if (state is AppUpdateUiState.Downloading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = TeamPrimaryColors.IndiaBlue
                )
                Text(
                    text = stringResource(R.string.update_download_in_progress),
                    style = BouncerDesignSystem.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun GradientProgressBar(progress: Float) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(320, easing = BouncerMotionEasing.EaseOutCubic),
        label = "update_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.backgroundElevated)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(colors.goldPrimary, TeamPrimaryColors.IndiaBlue)
                    )
                )
        )
    }
}

@Composable
private fun UpdateActions(
    state: AppUpdateUiState,
    isForceUpdate: Boolean,
    onUpdateNow: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = BouncerDesignSystem.colors

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (state) {
            is AppUpdateUiState.UpdateAvailable -> {
                Button(
                    onClick = onUpdateNow,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.goldPrimary,
                        contentColor = colors.backgroundBase
                    )
                ) {
                    Text(
                        text = stringResource(R.string.update_action_now),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            is AppUpdateUiState.ReadyToInstall -> {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.goldPrimary,
                        contentColor = colors.backgroundBase
                    )
                ) {
                    Text(
                        text = stringResource(R.string.update_action_install),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            is AppUpdateUiState.Error -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TeamPrimaryColors.IndiaBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.update_action_retry),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            else -> Unit
        }

        if (!isForceUpdate && state !is AppUpdateUiState.Downloading) {
            OutlinedButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                border = BorderStroke(
                    width = 1.dp,
                    color = colors.goldPrimary.copy(alpha = 0.35f)
                )
            ) {
                Text(text = stringResource(R.string.update_action_later))
            }
        }
    }
}

private fun UpdateFeatureType.toIcon(): ImageVector {
    return when (this) {
        UpdateFeatureType.FEATURE -> Icons.Outlined.AutoAwesome
        UpdateFeatureType.FIX -> Icons.Outlined.BugReport
        UpdateFeatureType.SECURITY -> Icons.Outlined.Security
    }
}
