package com.tanush.bouncer.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.messaging.FirebaseMessaging
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.widgets.BouncerGlanceWidgetReceiver
import com.tanush.bouncer.utils.PreferencesHelper
import kotlinx.coroutines.launch

private val POPULAR_TEAMS = listOf(
    "India", "Australia", "England", "Pakistan", "South Africa",
    "New Zealand", "Sri Lanka", "West Indies", "Bangladesh", "Afghanistan",
    "Zimbabwe", "Ireland"
)

private val CATEGORY_OPTIONS = listOf("All", "International", "League", "Domestic")

private const val PAGE_COUNT = 4
private const val PAGE_WELCOME = 0
private const val PAGE_YOUR_CRICKET = 1
private const val PAGE_NOTIFICATIONS = 2
private const val PAGE_COMPLETE = 3

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

    var selectedTeams by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — proceed either way */ }

    fun finishOnboarding() {
        selectedTeams.forEach { team ->
            FavouriteTeamsManager.add(team)
            FirebaseMessaging.getInstance().subscribeToTopic("team_${team.lowercase().replace(" ", "_")}")
        }
        PreferencesHelper.setString(context, PreferencesHelper.KEY_PREFERRED_CATEGORY, selectedCategory)
        PreferencesHelper.setBoolean(context, PreferencesHelper.KEY_ONBOARDING_COMPLETE, true)
        onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(spacing.lg),
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
            when (page) {
                PAGE_WELCOME -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.SportsCricket, null, Modifier.size(72.dp), tint = colors.primary)
                        Spacer(Modifier.height(spacing.xl))
                        Text(
                            "Welcome to Bouncer",
                            style = typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            "Your cricket companion for live scores and match intelligence.",
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.lg))
                        listOf(
                            "Live ball-by-ball updates",
                            "Win-probability insights",
                            "Follow your teams",
                        ).forEach { highlight ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = colors.primary)
                                Spacer(Modifier.padding(start = spacing.sm))
                                Text(highlight, style = typography.bodyMedium, color = colors.onSurface)
                            }
                        }
                    }
                }

                PAGE_YOUR_CRICKET -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Groups, null, Modifier.size(56.dp), tint = colors.primary)
                        Spacer(Modifier.height(spacing.lg))
                        Text(
                            "Your Cricket",
                            style = typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            "Pick your teams and preferred cricket.",
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
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
                                        style = typography.labelLarge,
                                        color = if (selected) colors.onPrimary else colors.textSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(spacing.xl))
                        Text(
                            "Preferred matches",
                            style = typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CATEGORY_OPTIONS.forEach { category ->
                                val selected = category == selectedCategory
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) colors.primary else colors.surfaceVariant)
                                        .border(1.dp, if (selected) colors.primary else colors.surfaceVariant, RoundedCornerShape(16.dp))
                                        .clickable { selectedCategory = category }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = category,
                                        style = typography.labelMedium,
                                        color = if (selected) colors.onPrimary else colors.textSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }

                PAGE_NOTIFICATIONS -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Notifications, null, Modifier.size(56.dp), tint = colors.primary)
                        Spacer(Modifier.height(spacing.lg))
                        Text(
                            "Stay in the Loop",
                            style = typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            "Enable notifications for wickets, milestones, and match starts for your followed teams.",
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.xl))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                scope.launch { pagerState.animateScrollToPage(PAGE_COMPLETE) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                            ),
                        ) {
                            Text("Allow Notifications", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(spacing.sm))
                        TextButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(PAGE_COMPLETE) } },
                        ) {
                            Text("Not Now", color = colors.textSecondary)
                        }
                    }
                }

                PAGE_COMPLETE -> {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val widgetProvider = ComponentName(context, BouncerGlanceWidgetReceiver::class.java)
                    val canPinWidget = appWidgetManager.isRequestPinAppWidgetSupported

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.RocketLaunch, null, Modifier.size(72.dp), tint = colors.primary)
                        Spacer(Modifier.height(spacing.xl))
                        Text(
                            "You're All Set",
                            style = typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            when {
                                selectedTeams.isNotEmpty() && selectedCategory != "All" ->
                                    "Following ${selectedTeams.size} team${if (selectedTeams.size > 1) "s" else ""}. Showing $selectedCategory matches."
                                selectedTeams.isNotEmpty() ->
                                    "Following ${selectedTeams.size} team${if (selectedTeams.size > 1) "s" else ""}. Start exploring!"
                                else -> "Start exploring matches, squads, and insights."
                            },
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        if (canPinWidget) {
                            Spacer(Modifier.height(spacing.xl))
                            TextButton(
                                onClick = {
                                    appWidgetManager.requestPinAppWidget(widgetProvider, null, null)
                                },
                            ) {
                                Text("Add Widget to Home Screen", color = colors.primary)
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.lg),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(if (isLastPage) "Get Started" else "Next", fontWeight = FontWeight.Bold)
            }
        }
    }
}
