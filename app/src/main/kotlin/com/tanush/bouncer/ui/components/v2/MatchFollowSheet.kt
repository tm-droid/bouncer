package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.MatchAlertPrefs
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * What following one match means, set in one place: the Live Update card (one ongoing notification
 * that updates in place) and the alerts (a new notification per wicket, boundary, milestone, and
 * for the start and result). They were one bell doing one thing before — the card — while the
 * alert switches lived in a detail-screen sheet whose preferences nothing ever read.
 *
 * Every switch applies immediately; there is nothing to save on dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchFollowSheet(
    matchId: String,
    matchTitle: String,
    onDismiss: () -> Unit,
    /** False for a match with nothing left to show live — the card row is then omitted. */
    canShowLiveCard: Boolean = true,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val cards by MatchSubscriptionManager.subscriptions.collectAsStateWithLifecycle()
    val allAlerts by MatchSubscriptionManager.alertPrefs.collectAsStateWithLifecycle()
    val alerts = allAlerts[matchId] ?: MatchAlertPrefs.NONE
    val setAlerts = { next: MatchAlertPrefs -> MatchSubscriptionManager.setAlerts(matchId, next) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xxl)) {
            Text(
                text = stringResource(R.string.match_follow_title, matchTitle),
                style = typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            )
            HorizontalDivider(color = colors.divider)

            if (canShowLiveCard) {
                FollowToggle(
                    title = stringResource(R.string.match_follow_live_card),
                    subtitle = stringResource(R.string.match_follow_live_card_subtitle),
                    checked = matchId in cards,
                ) { on ->
                    if (on) MatchSubscriptionManager.subscribe(matchId) else MatchSubscriptionManager.unsubscribe(matchId)
                }
                HorizontalDivider(color = colors.divider)
            }

            Text(
                text = stringResource(R.string.match_follow_alerts),
                style = typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, top = spacing.md),
            )
            FollowToggle(stringResource(R.string.match_follow_wickets), null, alerts.wickets) {
                setAlerts(alerts.copy(wickets = it))
            }
            FollowToggle(stringResource(R.string.match_follow_boundaries), null, alerts.boundaries) {
                setAlerts(alerts.copy(boundaries = it))
            }
            FollowToggle(
                stringResource(R.string.match_follow_milestones),
                stringResource(R.string.match_follow_milestones_subtitle),
                alerts.milestones,
            ) { setAlerts(alerts.copy(milestones = it)) }
            FollowToggle(stringResource(R.string.match_follow_start_result), null, alerts.startAndResult) {
                setAlerts(alerts.copy(startAndResult = it))
            }
            Spacer(Modifier.height(spacing.sm))
        }
    }
}

@Composable
private fun FollowToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = typography.bodyLarge, color = colors.onSurface)
            if (subtitle != null) Text(subtitle, style = typography.bodySmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceVariant,
            ),
        )
    }
}
