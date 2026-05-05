package com.tanush.bouncer.ui.components.v2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchContextMenuSheet(
    match: CricketMatch,
    onDismiss: () -> Unit,
    onSetReminder: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    val sheetState = rememberModalBottomSheetState()

    val favorites by FavouriteTeamsManager.favourites.collectAsState()
    val isFavorite = match.teams.any { team -> favorites.contains(team.name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacing.xl)
        ) {
            Text(
                text = match.matchTitle,
                style = BouncerThemeV2.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)
            )
            HorizontalDivider(color = colors.divider)

            // Share score
            ListItem(
                headlineContent = { Text("Share Score") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable {
                    val scoreText = match.teams.joinToString(" vs ") { "${it.name} ${it.score?.toString() ?: "-"}" }
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$scoreText\n#cricket #bouncer")
                        }, "Share Score"
                    ))
                    onDismiss()
                }
            )

            // Toggle favorite
            ListItem(
                headlineContent = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                leadingContent = { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null) },
                modifier = Modifier.clickable {
                    val teamName = match.teams.firstOrNull()?.name ?: return@clickable
                    if (isFavorite) FavouriteTeamsManager.remove(teamName)
                    else FavouriteTeamsManager.add(teamName)
                    onDismiss()
                }
            )

            // Set reminder
            ListItem(
                headlineContent = { Text("Set Reminder") },
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                modifier = Modifier.clickable { onSetReminder(); onDismiss() }
            )

            // Copy match ID
            ListItem(
                headlineContent = { Text("Copy Match ID") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier.clickable {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Match ID", match.id))
                    onDismiss()
                }
            )
        }
    }
}
