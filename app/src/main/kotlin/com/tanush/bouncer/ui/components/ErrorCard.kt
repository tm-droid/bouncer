package com.tanush.bouncer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// ErrorCard — Premium error state composable
// ==========================================

private val CardBackground = Color(0xFF1A1A1A)
private val RedAccent = Color(0xFFE53935)
private val Gold = Color(0xFFD4AF37)

@Composable
fun ErrorCard(
    errorCause: ErrorCause,
    message: String,
    onRetry: (() -> Unit)? = null,
    onViewCached: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (icon, iconDesc) = errorIcon(errorCause)
    val retryLabel = retryLabel(errorCause)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CardBackground
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Red accent left border
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(RedAccent, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconDesc,
                        tint = RedAccent,
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = iconDesc }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = errorTitle(errorCause),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onRetry != null) {
                        OutlinedButton(
                            onClick = onRetry,
                            border = BorderStroke(1.dp, Gold),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = retryLabel,
                                color = Gold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (onViewCached != null) {
                        TextButton(onClick = onViewCached) {
                            Text(
                                text = "View Last Cached",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Helpers
// ==========================================

private fun errorIcon(cause: ErrorCause): Pair<ImageVector, String> = when (cause) {
    ErrorCause.NETWORK -> Icons.Outlined.WifiOff to "Network error"
    ErrorCause.SERVER -> Icons.Outlined.CloudOff to "Server error"
    ErrorCause.PARSING -> Icons.Outlined.SyncProblem to "Parsing error"
    ErrorCause.TIMEOUT -> Icons.Outlined.SyncProblem to "Timeout error"
    ErrorCause.UNKNOWN -> Icons.Outlined.ErrorOutline to "Unknown error"
}

private fun errorTitle(cause: ErrorCause): String = when (cause) {
    ErrorCause.NETWORK -> "Connection Lost"
    ErrorCause.SERVER -> "Server Unavailable"
    ErrorCause.PARSING -> "Data Error"
    ErrorCause.TIMEOUT -> "Request Timed Out"
    ErrorCause.UNKNOWN -> "Something Went Wrong"
}

private fun retryLabel(cause: ErrorCause): String = when (cause) {
    ErrorCause.NETWORK -> "Retry Connection"
    ErrorCause.SERVER, ErrorCause.TIMEOUT -> "Retry"
    ErrorCause.PARSING, ErrorCause.UNKNOWN -> "Try Again"
}

// ==========================================
// Previews
// ==========================================

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ErrorCardNetworkPreview() {
    ErrorCard(
        errorCause = ErrorCause.NETWORK,
        message = "Unable to reach the server. Check your internet connection and try again.",
        onRetry = {},
        onViewCached = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ErrorCardServerPreview() {
    ErrorCard(
        errorCause = ErrorCause.SERVER,
        message = "CricBuzz returned an unexpected response.",
        onRetry = {}
    )
}
