package com.tanush.bouncer.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.messaging.FirebaseMessaging
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper
import kotlinx.coroutines.launch

private val POPULAR_TEAMS = listOf(
    "India", "Australia", "England", "Pakistan", "South Africa",
    "New Zealand", "Sri Lanka", "West Indies", "Bangladesh", "Afghanistan",
    "Zimbabwe", "Ireland"
)

private val PAGE_COUNT = 6
private const val PAGE_TEAMS = 3
private const val PAGE_NOTIFICATIONS = 4

private data class InfoPage(val icon: ImageVector, val title: String, val description: String, val highlights: List<String>)

private val infoPages = listOf(
    InfoPage(Icons.Default.SportsCricket, "Welcome to Bouncer", "Your premium cricket companion with live scores and match intelligence.", listOf("Live ball-by-ball updates", "Fast, readable match views")),
    InfoPage(Icons.Default.Insights, "Live Intelligence", "Get momentum shifts and match context in real time.", listOf("Win-probability insights", "Key-moment summaries")),
    InfoPage(Icons.Default.Favorite, "Personalized Feed", "Follow teams and tournaments you care about.", listOf("Quick favorite setup", "Focused content")),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreenV2(onComplete: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val isLastPage = pagerState.currentPage == PAGE_COUNT - 1

    var selectedTeams by remember { mutableStateOf<Set<String>>(emptySet()) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — either way proceed */ }

    fun finishOnboarding() {
        PreferencesHelper.setString(context, PreferencesHelper.KEY_ONBOARDING_TEAMS, selectedTeams.joinToString(","))
        PreferencesHelper.setBoolean(context, PreferencesHelper.KEY_ONBOARDING_COMPLETE, true)
        selectedTeams.forEach { team ->
            val topic = "team_${team.lowercase().replace(" ", "_")}"
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
        }
        onComplete()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(spacing.lg),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isLastPage) {
                TextButton(onClick = { finishOnboarding() }) {
                    Text("Skip", color = colors.textSecondary)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false,
        ) { page ->
            when {
                page < 3 -> {
                    val p = infoPages[page]
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(p.icon, null, Modifier.size(72.dp), tint = colors.primary)
                        Spacer(Modifier.height(spacing.xl))
                        Text(p.title, style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(spacing.sm))
                        Text(p.description, style = typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(spacing.lg))
                        p.highlights.forEach { h ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = colors.primary)
                                Spacer(Modifier.padding(start = spacing.sm))
                                Text(h, style = typography.bodyMedium, color = colors.onSurface)
                            }
                            Spacer(Modifier.height(spacing.xs))
                        }
                    }
                }
                page == PAGE_TEAMS -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Groups, null, Modifier.size(56.dp), tint = colors.primary)
                        Spacer(Modifier.height(spacing.lg))
                        Text("Pick Your Teams", style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(spacing.sm))
                        Text("Get alerts and insights for teams you follow.", style = typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(spacing.lg))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            POPULAR_TEAMS.forEach { team ->
                                val selected = team in selectedTeams
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) colors.primary else colors.surfaceVariant)
                                        .border(1.dp, if (selected) colors.primary else colors.surfaceVariant, RoundedCornerShape(20.dp))
                                        .clickable {
                                            selectedTeams = if (selected) selectedTeams - team else selectedTeams + team
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = team,
                                        color = if (selected) colors.onPrimary else colors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
                page == PAGE_NOTIFICATIONS -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Notifications, null, Modifier.size(56.dp), tint = colors.primary)
                        Spacer(Modifier.height(spacing.lg))
                        Text("Stay in the Loop", style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(spacing.sm))
                        Text("Enable notifications for wickets, milestones, and match starts for your followed teams.", style = typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(spacing.xl))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        ) {
                            Text("Allow Notifications", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(spacing.sm))
                        TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                            Text("Not Now", color = colors.textSecondary)
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.RocketLaunch, null, Modifier.size(72.dp), tint = colors.primary)
                        Spacer(Modifier.height(spacing.xl))
                        Text("You're All Set", style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            if (selectedTeams.isEmpty()) "Start exploring matches, squads, and insights."
                            else "Following ${selectedTeams.size} team${if (selectedTeams.size > 1) "s" else ""}. Start exploring!",
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = spacing.lg),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(PAGE_COUNT) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == pagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == pagerState.currentPage) colors.primary else colors.surfaceVariant),
                )
            }
        }

        if (pagerState.currentPage != PAGE_NOTIFICATIONS) {
            Button(
                onClick = {
                    if (isLastPage) finishOnboarding()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) {
                Text(if (isLastPage) "Get Started" else "Next", fontWeight = FontWeight.Bold)
            }
        }
    }
}
