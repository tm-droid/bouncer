package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.data.AppException
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

@Composable
fun ErrorScreenV2(
    message: String,
    exception: AppException? = null,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val icon = when (exception) {
        is AppException.NetworkException -> Icons.Outlined.CloudOff
        else -> Icons.Outlined.Warning
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.error,
            modifier = Modifier.size(64.dp),
        )

        Spacer(Modifier.height(spacing.xl))

        Text(
            text = when (exception) {
                is AppException.NetworkException -> "No Connection"
                is AppException.ScrapingException -> "Data Unavailable"
                is AppException.ApiException -> "Server Error"
                else -> "Oops!"
            },
            style = typography.titleLarge,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(spacing.sm))

        Text(
            text = message,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(spacing.xxl))

        if (onRetry != null) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.sm))
                Text("Try Again", style = typography.labelLarge)
            }
        }

        if (onBack != null) {
            Spacer(Modifier.height(spacing.md))
            OutlinedButton(onClick = onBack) {
                Text("Go Back", style = typography.labelLarge, color = colors.textSecondary)
            }
        }
    }
}
