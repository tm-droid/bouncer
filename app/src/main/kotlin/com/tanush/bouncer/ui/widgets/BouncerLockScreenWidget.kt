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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tanush.bouncer.BouncerActivity
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Premium Lock Screen Widget for Bouncer
 * 
 * Minimal, glanceable cricket scores optimized for lock screen display.
 * Ultra-compact design showing only essential match info.
 * 
 * Features:
 * - Minimal height (1-2 rows)
 * - Dark background with gold accents
 * - Format: "IND 245/3 vs AUS 198/7 • LIVE"
 * - Tap to unlock and open app
 */
class BouncerLockScreenWidget : GlanceAppWidget() {

    companion object {
        private const val PREF_NAME = "bouncer_lock_widget"
        
        // Premium Colors - optimized for lock screen visibility
        private val BlackBackground = Color(0xFF0A0C10)
        private val ShinyGoldDeep = Color(0xFFD4AF37)
        private val TextPrimary = Color(0xFFFFFFFF)
        private val TextSecondary = Color(0xFF94A3B8)
        private val LiveRed = Color(0xFFFF3B30)
        
        fun updateLockWidgetData(
            context: Context,
            team1Abbr: String,
            team1Score: String,
            team2Abbr: String,
            team2Score: String,
            isLive: Boolean
        ) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("team1_abbr", team1Abbr)
                putString("team1_score", team1Score)
                putString("team2_abbr", team2Abbr)
                putString("team2_score", team2Score)
                putBoolean("is_live", isLive)
                putLong("last_update", System.currentTimeMillis())
                apply()
            }
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Lock screen widget updated: $team1Abbr vs $team2Abbr"
                level = SentryLevel.INFO
            })
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                LockScreenContent(context)
            }
        }
    }

    @Composable
    private fun LockScreenContent(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        val team1Abbr = prefs.getString("team1_abbr", null)
        val team1Score = prefs.getString("team1_score", null)
        val team2Abbr = prefs.getString("team2_abbr", null)
        val team2Score = prefs.getString("team2_score", null)
        val isLive = prefs.getBoolean("is_live", false)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BlackBackground)
                .cornerRadius(12.dp)
                .clickable(actionStartActivity<BouncerActivity>())
        ) {
            if (team1Abbr != null && team2Abbr != null) {
                CompactMatchView(
                    team1Abbr = team1Abbr,
                    team1Score = team1Score ?: "-",
                    team2Abbr = team2Abbr,
                    team2Score = team2Score ?: "-",
                    isLive = isLive
                )
            } else {
                NoMatchView()
            }
        }
    }

    @Composable
    private fun CompactMatchView(
        team1Abbr: String,
        team1Score: String,
        team2Abbr: String,
        team2Score: String,
        isLive: Boolean
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Team 1
            Text(
                text = team1Abbr,
                style = TextStyle(
                    color = ColorProvider(ShinyGoldDeep),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            
            Spacer(modifier = GlanceModifier.width(4.dp))
            
            Text(
                text = team1Score,
                style = TextStyle(
                    color = ColorProvider(TextPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            
            Spacer(modifier = GlanceModifier.width(8.dp))
            
            // VS
            Text(
                text = "vs",
                style = TextStyle(
                    color = ColorProvider(TextSecondary),
                    fontSize = 11.sp
                )
            )
            
            Spacer(modifier = GlanceModifier.width(8.dp))
            
            // Team 2
            Text(
                text = team2Abbr,
                style = TextStyle(
                    color = ColorProvider(ShinyGoldDeep),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            
            Spacer(modifier = GlanceModifier.width(4.dp))
            
            Text(
                text = team2Score,
                style = TextStyle(
                    color = ColorProvider(TextPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            
            // Live indicator
            if (isLive) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                
                Text(
                    text = "•",
                    style = TextStyle(
                        color = ColorProvider(LiveRed),
                        fontSize = 14.sp
                    )
                )
                
                Spacer(modifier = GlanceModifier.width(4.dp))
                
                Text(
                    text = "LIVE",
                    style = TextStyle(
                        color = ColorProvider(LiveRed),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    @Composable
    private fun NoMatchView() {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🏏",
                style = TextStyle(fontSize = 16.sp)
            )
            
            Spacer(modifier = GlanceModifier.width(8.dp))
            
            Text(
                text = "No live matches",
                style = TextStyle(
                    color = ColorProvider(TextSecondary),
                    fontSize = 12.sp
                )
            )
        }
    }
}

/**
 * Lock Screen Widget Receiver
 */
class BouncerLockScreenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BouncerLockScreenWidget()
}
