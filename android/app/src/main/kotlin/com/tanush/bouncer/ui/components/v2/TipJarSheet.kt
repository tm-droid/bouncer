package com.tanush.bouncer.ui.components.v2

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.R
import com.tanush.bouncer.data.billing.TipJar
import com.tanush.bouncer.data.billing.TipJarState
import com.tanush.bouncer.data.billing.TipTier
import com.tanush.bouncer.data.billing.TipUnavailableReason
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.TipJarViewModel

/**
 * "Support Bouncer": an optional tip, opened from the More tab. The Play connection lives only
 * while the sheet is shown. Prices are Play's own strings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipJarSheet(onDismiss: () -> Unit, viewModel: TipJarViewModel = viewModel()) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    val tipJar = viewModel.tipJar
    val state by tipJar.state.collectAsStateWithLifecycle()
    DisposableEffect(tipJar) {
        tipJar.open()
        onDispose { tipJar.close() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.tip_jar_row_title),
                style = BouncerThemeV2.typography.titleMedium,
                color = colors.onSurface,
            )
            TipJarBody(state, tipJar)
        }
    }
}

@Composable
private fun TipJarBody(state: TipJarState, tipJar: TipJar) {
    when (state) {
        TipJarState.Loading -> CircularProgressIndicator(Modifier.padding(BouncerThemeV2.spacing.lg))
        is TipJarState.Unavailable -> {
            Note(unavailableText(state.reason))
            if (state.reason == TipUnavailableReason.NOT_CONNECTED) RetryButton(tipJar::reload)
        }
        is TipJarState.Ready -> {
            Note(stringResource(R.string.tip_jar_intro))
            if (state.pending) Note(stringResource(R.string.tip_jar_pending))
            TierButtons(state.tiers, enabled = true, tipJar)
        }
        is TipJarState.Purchasing -> {
            Note(stringResource(R.string.tip_jar_intro))
            TierButtons(state.tiers, enabled = false, tipJar)
        }
        is TipJarState.Thanks -> {
            Note(stringResource(R.string.tip_jar_thanks))
            OutlinedButton(onClick = tipJar::tipAgain, modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT)) {
                Text(stringResource(R.string.tip_jar_again))
            }
        }
        is TipJarState.Error -> {
            Note(stringResource(R.string.tip_jar_error))
            RetryButton(tipJar::reload)
        }
    }
}

@Composable
private fun unavailableText(reason: TipUnavailableReason) = stringResource(
    when (reason) {
        TipUnavailableReason.NOT_SUPPORTED -> R.string.tip_jar_not_supported
        TipUnavailableReason.NO_PRODUCTS -> R.string.tip_jar_no_products
        TipUnavailableReason.NOT_CONNECTED -> R.string.tip_jar_not_connected
    },
)

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = BouncerThemeV2.typography.bodyMedium,
        color = BouncerThemeV2.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT)) {
        Text(stringResource(R.string.tip_jar_retry))
    }
}

@Composable
private fun TierButtons(tiers: List<TipTier>, enabled: Boolean, tipJar: TipJar) {
    val colors = BouncerThemeV2.colors
    val activity = LocalContext.current.findActivity()
    tiers.forEach { tier ->
        Button(
            onClick = { activity?.let { tipJar.tip(it, tier.productId) } },
            enabled = enabled && activity != null,
            modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        ) {
            Text(
                text = "${stringResource(tierLabel(tier.productId))} · ${tier.price}",
                style = BouncerThemeV2.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun tierLabel(productId: String) = when (productId) {
    "tip_small" -> R.string.tip_jar_small
    "tip_medium" -> R.string.tip_jar_medium
    else -> R.string.tip_jar_large
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val BUTTON_HEIGHT = 52.dp
