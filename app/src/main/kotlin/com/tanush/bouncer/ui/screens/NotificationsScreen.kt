package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.managers.NotificationItem
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val items by NotificationInboxManager.items.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        NotificationInboxManager.markAllRead()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Notifications",
                        style = typography.titleMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                ),
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        if (items.isEmpty()) {
            EmptyStateV2(
                title = "No notifications yet",
                subtitle = "Subscribe to matches with the bell icon to get alerts",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                items(items, key = { it.id }) { item ->
                    NotificationRow(item = item)
                }
                item {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val accentColor = when (item.type) {
        "wicket" -> Color(0xFFFF3B30)
        "six" -> Color(0xFF8B5CF6)
        "four" -> Color(0xFF22C55E)
        "milestone" -> Color(0xFFFFD700)
        "match_start" -> Color(0xFF3B82F6)
        "match_end" -> Color(0xFF94A3B8)
        else -> colors.primary
    }

    val typeLabel = when (item.type) {
        "wicket" -> "Wicket"
        "six" -> "Six"
        "four" -> "Four"
        "milestone" -> "Milestone"
        "match_start" -> "Match Start"
        "match_end" -> "Match End"
        else -> "Update"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (!item.isRead) colors.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(spacing.sm),
            )
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accentColor)
                .align(Alignment.CenterVertically),
        )
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    style = typography.bodyMedium,
                    fontWeight = if (!item.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    color = colors.onSurface,
                )
                Text(
                    text = formatTimestamp(item.timestamp),
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.body,
                style = typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = typeLabel,
                style = typography.bodySmall,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

private fun formatTimestamp(ts: Long): String = timeFormat.format(Date(ts))
