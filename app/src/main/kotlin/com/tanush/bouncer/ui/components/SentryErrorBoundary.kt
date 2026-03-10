package com.tanush.bouncer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Composable error boundary that catches exceptions during composition,
 * reports them to Sentry with screen context, and shows a retry-able fallback.
 */
@Composable
fun SentryErrorBoundary(
    screenName: String,
    modifier: Modifier = Modifier,
    fallback: (@Composable (retry: () -> Unit, error: Throwable) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var error by remember { mutableStateOf<Throwable?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Track screen navigation
    LaunchedEffect(screenName) {
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "navigation"
                message = "Viewing $screenName"
                level = SentryLevel.INFO
            }
        )
    }

    if (error != null) {
        if (fallback != null) {
            fallback(
                {
                    error = null
                    retryTrigger++
                },
                error ?: return
            )
        } else {
            DefaultErrorFallback(
                screenName = screenName,
                error = error ?: return,
                onRetry = {
                    error = null
                    retryTrigger++
                },
                modifier = modifier
            )
        }
    } else {
        // key(retryTrigger) forces recomposition on retry
        key(retryTrigger) {
            content()
        }
    }
}

@Composable
private fun DefaultErrorFallback(
    screenName: String,
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "An error occurred in $screenName. This has been reported automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}
