package com.tanush.bouncer.ui.components.v2

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Date

/**
 * Inline, flat banner for content plus a failure: "You're offline · Showing scores saved at 4:12 pm"
 * and Retry. A fill and a hairline, no colour: the failure is stated in words, and red/gold keep
 * their jobs (live / interactive).
 */
@Composable
fun StaleDataBanner(
    kind: FailureKind,
    savedAtMs: Long?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current
    val offline = kind == FailureKind.Offline
    val title = stringResource(if (offline) R.string.state_offline_saved_title else R.string.state_server_title)
    val cause = when {
        offline && savedAtMs != null ->
            stringResource(R.string.state_offline_saved_cause, DateFormat.getTimeFormat(context).format(Date(savedAtMs)))
        offline -> stringResource(R.string.state_offline_cause)
        else -> stringResource(R.string.state_server_cause)
    }
    Column(modifier.fillMaxWidth().background(colors.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.xxl)
                .padding(start = spacing.lg, end = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "$title · $cause",
                modifier = Modifier.weight(1f),
                style = typography.bodySmall,
                color = colors.onBackground,
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.action_retry),
                    style = typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText,
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}
