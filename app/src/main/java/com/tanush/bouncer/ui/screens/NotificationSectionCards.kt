package com.tanush.bouncer.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

@Composable
fun SettingSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isExpandable: Boolean = false,
    isExpanded: Boolean = true,
    onExpandToggle: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val rotation = animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "sectionChevronRotation"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        useGoldBorder = true,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                hierarchy.secondary.primary.copy(alpha = 0.9f),
                                colors.statusInfo.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isExpandable && onExpandToggle != null) {
                        onExpandToggle?.invoke()
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = hierarchy.secondary.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (isExpandable) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(id = R.string.notification_cd_expand_section),
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotation.value)
                    )
                }
            }

            subtitle?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            if (!isExpandable || isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = hierarchy.secondary.primary.copy(alpha = 0.2f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))
                content()
            }
        }
    }
}

@Composable
fun NotificationToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val switchScale = animateFloatAsState(
        targetValue = if (checked) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "toggleScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.backgroundElevated)
                .border(1.dp, hierarchy.secondary.primary.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) colors.statusInfo else colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(width = 54.dp, height = 32.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.statusInfo,
                uncheckedTrackColor = colors.textTertiary.copy(alpha = 0.6f),
                uncheckedThumbColor = colors.backgroundElevated
            )
        )
    }
}

@Composable
fun TimePickerField(
    label: String,
    minuteOfDay: Int,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = BouncerDesignSystem.colors
    val (hour, minute) = minuteOfDay / 60 to minuteOfDay % 60

    GlassCardSmall(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                TimePickerDialog(
                    context,
                    { _, selectedHour, selectedMinute ->
                        onMinuteSelected(selectedHour * 60 + selectedMinute)
                    },
                    hour,
                    minute,
                    false
                ).show()
            },
        useGoldBorder = false,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                Text(
                    text = minuteOfDay.toTwelveHourFormat(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textSecondary
            )
        }
    }
}

@Composable
fun FrequencySelector(
    selected: NotificationFrequency,
    onSelect: (NotificationFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NotificationFrequency.entries.forEach { frequency ->
            val selectedState = frequency == selected
            AssistChip(
                onClick = { onSelect(frequency) },
                label = {
                    Text(
                        text = stringResource(id = frequency.labelRes),
                        color = if (selectedState) colors.backgroundBase else colors.textPrimary
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selectedState) colors.statusInfo else colors.backgroundElevated,
                    labelColor = if (selectedState) colors.backgroundBase else colors.textPrimary
                ),
                border = if (selectedState) null else BorderStroke(1.dp, hierarchy.secondary.primary.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
fun TeamPreferenceCard(
    team: TeamPreference,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val accent = runCatching { Color(android.graphics.Color.parseColor(team.colorHex)) }
        .getOrElse { colors.statusInfo }

    GlassCardSmall(
        modifier = modifier
            .width(190.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelectedChange(!selected) },
        useGoldBorder = false,
        contentPadding = PaddingValues(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.22f))
                        .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = team.emoji)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = team.nameRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Switch(
                checked = selected,
                onCheckedChange = onSelectedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.statusInfo,
                    uncheckedTrackColor = colors.textTertiary.copy(alpha = 0.6f),
                    uncheckedThumbColor = colors.backgroundElevated
                )
            )
        }
    }
}

@Composable
fun SoundPreview(
    isLoading: Boolean,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors

    GlassCardSmall(
        modifier = modifier,
        useGoldBorder = false,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onPreviewClick, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.statusInfo
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(id = R.string.notification_preview_sound),
                    tint = colors.statusInfo
                )
            }
        }
    }
}
