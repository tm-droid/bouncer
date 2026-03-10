package com.tanush.bouncer.ui.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import com.tanush.bouncer.MainActivity
import com.tanush.bouncer.R
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Premium Glance-based Home Screen Widget for Bouncer
 * 
 * Features:
 * - Modern Jetpack Glance composables
 * - Premium dark theme with gold accents
 * - Live match scores with badges
 * - Multiple widget sizes (2x1, 3x2, 4x2)
 * - Tap to open match details
 * - Auto-refresh via WorkManager
 */
class BouncerGlanceWidget : GlanceAppWidget() {

    companion object {
        private const val PREF_NAME = "bouncer_glance_widget"
        
        // Premium Bouncer Colors
        private val BlackBackground = Color(0xFF0F1115)
        private val CardSurface = Color(0xFF1C2028)
        private val ShinyGoldDeep = Color(0xFFD4AF37)
        private val GoldDark = Color(0xFFB08D26)
        private val TextPrimary = Color(0xFFF9FAFB)
        private val TextSecondary = Color(0xFF94A3B8)
        private val LiveRed = Color(0xFFFF3B30)
        private val SuccessGreen = Color(0xFF22C55E)
        
        fun updateWidgetData(
            context: Context,
            team1Name: String,
            team1Score: String,
            team2Name: String,
            team2Score: String,
            matchStatus: String,
            isLive: Boolean,
            winProbability: Float? = null
        ) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("team1_name", team1Name)
                putString("team1_score", team1Score)
                putString("team2_name", team2Name)
                putString("team2_score", team2Score)
                putString("match_status", matchStatus)
                putBoolean("is_live", isLive)
                putFloat("win_prob", winProbability ?: 0.5f)
                putLong("last_update", System.currentTimeMillis())
                apply()
            }
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Glance widget data updated: $team1Name vs $team2Name"
                level = SentryLevel.INFO
            })
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        val team1Name = prefs.getString("team1_name", null)
        val team1Score = prefs.getString("team1_score", null)
        val team2Name = prefs.getString("team2_name", null)
        val team2Score = prefs.getString("team2_score", null)
        val matchStatus = prefs.getString("match_status", "No matches")
        val isLive = prefs.getBoolean("is_live", false)
        val winProb = prefs.getFloat("win_prob", 0.5f)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BlackBackground)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            if (team1Name != null && team2Name != null) {
                LiveMatchLayout(
                    team1Name = team1Name,
                    team1Score = team1Score ?: "-",
                    team2Name = team2Name,
                    team2Score = team2Score ?: "-",
                    matchStatus = matchStatus ?: "Unknown",
                    isLive = isLive,
                    winProbability = winProb
                )
            } else {
                NoMatchLayout()
            }
        }
    }

    @Composable
    private fun LiveMatchLayout(
        team1Name: String,
        team1Score: String,
        team2Name: String,
        team2Score: String,
        matchStatus: String,
        isLive: Boolean,
        winProbability: Float
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Header with live badge
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    Box(
                        modifier = GlanceModifier
                            .background(LiveRed)
                            .cornerRadius(4.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            style = TextStyle(
                                color = ColorProvider(TextPrimary),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                
                Text(
                    text = matchStatus,
                    style = TextStyle(
                        color = ColorProvider(if (isLive) LiveRed else TextSecondary),
                        fontSize = 11.sp,
                        fontWeight = if (isLive) FontWeight.Medium else FontWeight.Normal
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Team 1
            TeamRow(
                teamName = team1Name,
                score = team1Score,
                isLeading = winProbability > 0.5f
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // VS Divider
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VS",
                    style = TextStyle(
                        color = ColorProvider(TextSecondary),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Team 2
            TeamRow(
                teamName = team2Name,
                score = team2Score,
                isLeading = winProbability < 0.5f
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Win probability bar
            if (isLive) {
                WinProbabilityBar(winProbability)
            }
        }
    }

    @Composable
    private fun TeamRow(teamName: String, score: String, isLeading: Boolean) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(CardSurface)
                .cornerRadius(8.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = teamName,
                style = TextStyle(
                    color = ColorProvider(if (isLeading) ShinyGoldDeep else TextPrimary),
                    fontSize = 14.sp,
                    fontWeight = if (isLeading) FontWeight.Bold else FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            
            Spacer(modifier = GlanceModifier.width(8.dp))
            
            Text(
                text = score,
                style = TextStyle(
                    color = ColorProvider(if (isLeading) ShinyGoldDeep else TextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun WinProbabilityBar(probability: Float) {
        Column(
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${(probability * 100).toInt()}% win prob",
                    style = TextStyle(
                        color = ColorProvider(TextSecondary),
                        fontSize = 10.sp
                    )
                )
            }
            
            Spacer(modifier = GlanceModifier.height(4.dp))
            
            // Simple progress indicator using Box
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(CardSurface)
                    .cornerRadius(2.dp)
            ) {
                // Empty progress bar for now
            }
        }
    }

    @Composable
    private fun NoMatchLayout() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🏏",
                style = TextStyle(
                    fontSize = 32.sp
                )
            )
            
            Spacer(modifier = GlanceModifier.height(8.dp))
            
            Text(
                text = "No live matches",
                style = TextStyle(
                    color = ColorProvider(TextPrimary),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            
            Spacer(modifier = GlanceModifier.height(4.dp))
            
            Text(
                text = "Check back soon",
                style = TextStyle(
                    color = ColorProvider(TextSecondary),
                    fontSize = 11.sp
                )
            )
        }
    }
}

/**
 * Glance Widget Receiver for Home Screen
 */
class BouncerGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BouncerGlanceWidget()
}
