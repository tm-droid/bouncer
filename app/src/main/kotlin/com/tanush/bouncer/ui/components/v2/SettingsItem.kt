package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper

/**
 * Settings list item — icon, title, subtitle, trailing widget (toggle/chevron/text).
 */

sealed class SettingsTrailing {
    data class Toggle(val checked: Boolean) : SettingsTrailing()
    data object Navigation : SettingsTrailing()
    data class Value(val text: String) : SettingsTrailing()
}

@Composable
fun SettingsItemV2(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: SettingsTrailing? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }

    val clickMod = if (onClick != null) {
        Modifier.clickable(onClick = {
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        })
    } else {
        Modifier
    }

    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(clickMod)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colors.primary,
                )
                Spacer(Modifier.width(spacing.lg))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.titleMedium,
                    color = colors.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            when (trailing) {
                is SettingsTrailing.Toggle -> {
                    Switch(
                        checked = trailing.checked,
                        onCheckedChange = { onClick?.invoke() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                        ),
                    )
                }
                is SettingsTrailing.Navigation -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textSecondary,
                    )
                }
                is SettingsTrailing.Value -> {
                    Text(
                        text = trailing.text,
                        style = typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                null -> {}
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) (56.dp + spacing.lg) else spacing.lg),
                color = colors.divider,
            )
        }
    }
}
