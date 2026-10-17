package com.tanush.bouncer.ui.screens

import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import com.tanush.bouncer.ui.components.v2.AppMark
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
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
        AppMark(size = 88.dp)
        Spacer(Modifier.height(spacing.xl))
        Text(
            text = stringResource(R.string.update_required_title),
            style = typography.headlineMedium,
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
        // The only thing to do on this screen, so a full-width filled button rather than a link.
        Button(
            onClick = onUpdate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        ) {
            Text(
                text = stringResource(R.string.update_required_action),
                style = typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
