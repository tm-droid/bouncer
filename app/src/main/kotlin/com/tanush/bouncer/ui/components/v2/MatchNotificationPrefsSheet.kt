package com.tanush.bouncer.ui.components.v2

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import io.sentry.Sentry
import io.sentry.Breadcrumb

data class MatchNotificationPrefs(
    val wickets: Boolean = true,
    val boundaries: Boolean = true,
    val milestones: Boolean = true,
    val scoreUpdates: Boolean = true,
    val inningsBreak: Boolean = true,
) {
    companion object {
        fun load(context: Context, matchId: String): MatchNotificationPrefs {
            val prefs = context.getSharedPreferences("bouncer_match_notif_prefs", Context.MODE_PRIVATE)
            val prefix = "notif_${matchId}_"
            return MatchNotificationPrefs(
                wickets = prefs.getBoolean("${prefix}wickets", true),
                boundaries = prefs.getBoolean("${prefix}boundaries", true),
                milestones = prefs.getBoolean("${prefix}milestones", true),
                scoreUpdates = prefs.getBoolean("${prefix}score_updates", true),
                inningsBreak = prefs.getBoolean("${prefix}innings_break", true),
            )
        }

        fun save(context: Context, matchId: String, prefs: MatchNotificationPrefs) {
            val sharedPrefs = context.getSharedPreferences("bouncer_match_notif_prefs", Context.MODE_PRIVATE)
            val prefix = "notif_${matchId}_"
            sharedPrefs.edit()
                .putBoolean("${prefix}wickets", prefs.wickets)
                .putBoolean("${prefix}boundaries", prefs.boundaries)
                .putBoolean("${prefix}milestones", prefs.milestones)
                .putBoolean("${prefix}score_updates", prefs.scoreUpdates)
                .putBoolean("${prefix}innings_break", prefs.inningsBreak)
                .apply()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchNotificationPrefsSheet(
    matchId: String,
    matchTitle: String,
    onDismiss: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = androidx.compose.ui.platform.LocalContext.current

    var prefs by remember { mutableStateOf(MatchNotificationPrefs.load(context, matchId)) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = {
            MatchNotificationPrefs.save(context, matchId, prefs)
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Notifications", style = typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    MatchNotificationPrefs.save(context, matchId, prefs)
                    onDismiss()
                }) {
                    Icon(Icons.Default.Close, "Close", tint = colors.textSecondary)
                }
            }

            Text(
                matchTitle,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = spacing.lg),
            )

            // Quick presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedButton(
                    onClick = {
                        prefs = MatchNotificationPrefs(
                            wickets = true, boundaries = true, milestones = true,
                            scoreUpdates = true, inningsBreak = true,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("All On", style = typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        prefs = MatchNotificationPrefs(
                            wickets = true, boundaries = false, milestones = true,
                            scoreUpdates = false, inningsBreak = false,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Key Only", style = typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        prefs = MatchNotificationPrefs(
                            wickets = false, boundaries = false, milestones = false,
                            scoreUpdates = false, inningsBreak = false,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Mute", style = typography.labelMedium)
                }
            }

            Spacer(Modifier.height(spacing.lg))

            NotifToggle("Wickets", "Get notified on every wicket", prefs.wickets) { prefs = prefs.copy(wickets = it) }
            NotifToggle("Boundaries", "Fours and sixes alerts", prefs.boundaries) { prefs = prefs.copy(boundaries = it) }
            NotifToggle("Milestones", "50s, 100s, 5-wicket hauls", prefs.milestones) { prefs = prefs.copy(milestones = it) }
            NotifToggle("Score Updates", "Periodic score summaries", prefs.scoreUpdates) { prefs = prefs.copy(scoreUpdates = it) }
            NotifToggle("Innings Break", "Innings break notifications", prefs.inningsBreak) { prefs = prefs.copy(inningsBreak = it) }

            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

@Composable
private fun NotifToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = typography.bodyLarge, color = colors.onSurface)
            Text(subtitle, style = typography.bodySmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceVariant,
            ),
        )
    }
}
