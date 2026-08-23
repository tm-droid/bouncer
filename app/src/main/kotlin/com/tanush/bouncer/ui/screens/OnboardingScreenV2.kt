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
import com.tanush.bouncer.utils.rememberHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.tanush.bouncer.R

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
    val haptics = rememberHapticFeedback()
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }

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
                    Text(stringResource(R.string.action_skip), color = colors.textSecondary)
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
                            stringResource(R.string.onboarding_title),
                            style = typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            stringResource(R.string.onboarding_welcome_subtitle),
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.lg))
                        listOf(
                            R.string.onboarding_feature_ball_by_ball,
                            R.string.onboarding_feature_win_probability,
                            R.string.onboarding_feature_follow_teams,
                        ).forEach { highlight ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = colors.primary)
                                Spacer(Modifier.padding(start = spacing.sm))
                                Text(
                                    stringResource(highlight),
                                    style = typography.bodyMedium,
                                    color = colors.onSurface,
                                )
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
                            stringResource(R.string.onboarding_teams_title),
                            style = typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            stringResource(R.string.onboarding_teams_subtitle),
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
                                            if (hapticEnabled) haptics.toggle(turningOn = !selected)
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
                            stringResource(R.string.onboarding_preferred_matches),
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
                                        .clickable {
                                            if (hapticEnabled) haptics.click()
                                            selectedCategory = category
                                        }
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
                            stringResource(R.string.onboarding_notifications_title),
                            style = typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            stringResource(R.string.onboarding_notifications_subtitle),
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            // Reciprocity priming: the value (followed teams) came first,
                            // so the permission ask is scoped to it, not open-ended.
                            if (selectedTeams.isNotEmpty()) {
                                "You follow ${selectedTeams.size} team${if (selectedTeams.size > 1) "s" else ""} — we'll only ping you about them."
                            } else {
                                "Follow your teams first — we'll only ping you about them."
                            },
                            style = typography.labelMedium,
                            color = colors.primary,
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
                            Text(stringResource(R.string.onboarding_allow_notifications), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(spacing.sm))
                        TextButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(PAGE_COMPLETE) } },
                        ) {
                            Text(stringResource(R.string.onboarding_not_now), color = colors.textSecondary)
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
                            stringResource(R.string.onboarding_complete_title),
                            style = typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            completionSummary(selectedTeams.size, selectedCategory),
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
                                Text(stringResource(R.string.onboarding_add_widget), color = colors.primary)
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
                Text(
                    stringResource(
                        if (isLastPage) R.string.onboarding_get_started else R.string.action_next
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * The completion line on the last onboarding page.
 *
 * The count is grammatical, so it goes through a plural rather than the `"team" + "s"` concatenation
 * this replaced — that construction has no correct translation in a language whose plural rules are
 * not English's.
 */
@Composable
private fun completionSummary(teamCount: Int, category: String): String = when {
    teamCount > 0 && category != "All" ->
        pluralStringResource(R.plurals.onboarding_following_teams, teamCount, teamCount) + " " +
            stringResource(R.string.onboarding_showing_category, category)
    teamCount > 0 ->
        pluralStringResource(R.plurals.onboarding_following_teams, teamCount, teamCount) + " " +
            stringResource(R.string.onboarding_start_exploring)
    else -> stringResource(R.string.onboarding_complete_generic)
}
