package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.*

// ==========================================
// BOUNCER – More Hub Screen
// The 5th tab: gateway to Settings, Achievements,
// Activity Feed, AR Tracking, About, Dev Tools.
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onSettingsClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onActivityClick: () -> Unit,
    onArTrackingClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDevToolsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "More",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgBase
                )
            )
        },
        containerColor = BgBase
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.space1)
        ) {
            item {
                Spacer(modifier = Modifier.height(Spacing.space2))
            }
            item {
                MoreMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Settings",
                    subtitle = "Preferences, notifications, data",
                    onClick = onSettingsClick
                )
            }
            item {
                MoreMenuItem(
                    icon = Icons.Default.EmojiEvents,
                    title = "Achievements",
                    subtitle = "Trophy room & milestones",
                    onClick = onAchievementsClick
                )
            }
            item {
                MoreMenuItem(
                    icon = Icons.Default.Insights,
                    title = "Activity Feed",
                    subtitle = "Intelligence & AI insights",
                    onClick = onActivityClick
                )
            }
            item {
                MoreMenuItem(
                    icon = Icons.Default.ViewInAr,
                    title = "AR Ball Tracking",
                    subtitle = "Augmented reality experience",
                    onClick = onArTrackingClick
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.space3),
                    color = Surface3
                )
            }
            item {
                MoreMenuItem(
                    icon = Icons.AutoMirrored.Filled.HelpCenter,
                    title = "About Bouncer",
                    subtitle = "Version, licenses, credits",
                    onClick = onAboutClick
                )
            }
            item {
                MoreMenuItem(
                    icon = Icons.Default.Code,
                    title = "Developer Tools",
                    subtitle = "API diagnostics & debug",
                    onClick = onDevToolsClick
                )
            }
            item {
                Spacer(modifier = Modifier.height(Spacing.space6))
            }
        }
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(CornerRadius.md),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space4)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gold400,
                modifier = Modifier.size(IconSize.md)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(IconSize.sm)
            )
        }
    }
}
