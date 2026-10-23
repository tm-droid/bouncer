package com.tanush.bouncer.ui.screens.matchdetail.v2

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/** The over-and-ball label beside a delivery. A minimum size, so a large font scale is not clipped. */
@Composable
internal fun BallLabel(label: String) {
    val colors = BouncerThemeV2.colors
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 36.dp, minHeight = 24.dp)
            .clip(BouncerThemeV2.shapes.extraSmall)
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            // Tabular, so "19.2" and "19.10" line up down the feed.
            style = BouncerThemeV2.typography.numericSmall,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun CommentaryLineMenu(expanded: Boolean, shareText: String, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.commentary_action_copy)) },
            onClick = {
                clipboardManager.setText(AnnotatedString(shareText))
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.commentary_action_share)) },
            onClick = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        },
                        null,
                    )
                )
                onDismiss()
            },
        )
    }
}
