package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Shown instead of the whole app when this build has been retired (`AppUpdateGate`). It has one
 * action, because there is nothing else this build can still do correctly.
 */
@Composable
fun UpdateRequiredScreen(onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.update_required_title),
            style = typography.titleLarge,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.md))
        Text(
            text = stringResource(R.string.update_required_body),
            style = typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.lg))
        TextButton(onClick = onUpdate) {
            Text(
                text = stringResource(R.string.update_required_action),
                style = typography.labelLarge,
                color = colors.primaryText,
            )
        }
    }
}
