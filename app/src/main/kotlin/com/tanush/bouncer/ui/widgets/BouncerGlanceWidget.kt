package com.tanush.bouncer.ui.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class BouncerGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(80.dp, 40.dp),    // 2x1
            DpSize(180.dp, 110.dp),  // 3x2
            DpSize(250.dp, 110.dp)   // 4x2
        )
    )

    companion object {
        internal const val PREF_NAME = "bouncer_glance_widget"
        private const val SELECTED_MATCH_KEY_PREFIX = "selected_match_id_"

        fun updateWidgetData(
            context: Context,
            team1Name: String,
            team1Score: String,
            team2Name: String,
            team2Score: String,
            matchStatus: String,
            isLive: Boolean,
            winProbability: Float? = null,
            recentBalls: String? = null,
            battingPair: String? = null,
            currentBowler: String? = null
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
                if (recentBalls != null) putString("recent_balls", recentBalls)
                if (battingPair != null) putString("batting_pair", battingPair)
                if (currentBowler != null) putString("current_bowler", currentBowler)
                apply()
            }

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Glance widget data updated: $team1Name vs $team2Name"
                level = SentryLevel.INFO
            })
        }

        fun setSelectedMatchId(context: Context, appWidgetId: Int, matchId: String) {
            context
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("$SELECTED_MATCH_KEY_PREFIX$appWidgetId", matchId)
                .apply()
        }

        fun getSelectedMatchId(context: Context, appWidgetId: Int): String? {
            return context
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("$SELECTED_MATCH_KEY_PREFIX$appWidgetId", null)
        }

        fun createMatchDeepLinkAction(context: Context, matchId: String): Action {
            return actionStartActivity<BouncerActivity>(
                actionParametersOf(
                    ActionParameters.Key<String>(LiveMatchWidgetProvider.EXTRA_MATCH_ID) to matchId
                )
            )
        }

        /** Parse "Name|runs|balls;Name|runs|balls" back into a pair. */
        internal fun parseBattingPair(raw: String): Pair<BatsmanInfo, BatsmanInfo>? {
            val parts = raw.split(";")
            if (parts.size < 2) return null
            val striker = parseBatsman(parts[0]) ?: return null
            val nonStriker = parseBatsman(parts[1]) ?: return null
            return striker to nonStriker
        }

        private fun parseBatsman(raw: String): BatsmanInfo? {
            val p = raw.split("|")
            if (p.size < 3) return null
            return BatsmanInfo(
                name  = p[0],
                runs  = p[1].toIntOrNull() ?: 0,
                balls = p[2].toIntOrNull() ?: 0
            )
        }

        /** Parse "name|wickets|runs|overs" back into a BowlerInfo. */
        internal fun parseBowler(raw: String): BowlerInfo? {
            val p = raw.split("|")
            if (p.size < 4) return null
            return BowlerInfo(
                name    = p[0],
                wickets = p[1].toIntOrNull() ?: 0,
                runs    = p[2].toIntOrNull() ?: 0,
                overs   = p[3]
            )
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
        val recentBallsRaw = prefs.getString("recent_balls", null)
        val battingPairRaw = prefs.getString("batting_pair", null)
        val currentBowlerRaw = prefs.getString("current_bowler", null)

        val size = LocalSize.current
        val isMediumOrLarger = size.height >= 100.dp
        val isLargeWidget = size.width >= 240.dp && size.height >= 100.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColorScheme.background)
                .cornerRadius(WidgetTheme.radiusLarge)
                .clickable(actionStartActivity<BouncerActivity>())
        ) {
            if (team1Name != null && team2Name != null) {
                LiveMatchLayout(
                    team1Name        = team1Name,
                    team1Score       = team1Score ?: "-",
                    team2Name        = team2Name,
                    team2Score       = team2Score ?: "-",
                    matchStatus      = matchStatus ?: "Unknown",
                    isLive           = isLive,
                    winProbability   = winProb,
                    recentBallsRaw   = recentBallsRaw,
                    battingPairRaw   = battingPairRaw,
                    currentBowlerRaw = currentBowlerRaw,
                    showRichData     = isMediumOrLarger,
                    showBattingData  = isLargeWidget
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
        winProbability: Float,
        recentBallsRaw: String?,
        battingPairRaw: String?,
        currentBowlerRaw: String?,
        showRichData: Boolean,
        showBattingData: Boolean
    ) {
        val recentBalls = recentBallsRaw?.split(",")?.filter { it.isNotBlank() }
        val battingPair = battingPairRaw?.let { parseBattingPair(it) }
        val bowler = currentBowlerRaw?.let { parseBowler(it) }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Header with live badge + status
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    Box(
                        modifier = GlanceModifier
                            .background(WidgetColorScheme.live)
                            .cornerRadius(WidgetTheme.radiusSmall)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            style = TextStyle(
                                color = WidgetColorScheme.textPrimary,
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
                        color = if (isLive) WidgetColorScheme.primary else WidgetColorScheme.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isLive) FontWeight.Medium else FontWeight.Normal
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Team 1
            TeamRow(teamName = team1Name, score = team1Score, isLeading = winProbability > 0.5f)

            Spacer(modifier = GlanceModifier.height(8.dp))

            // VS
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VS",
                    style = TextStyle(
                        color = WidgetColorScheme.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Team 2
            TeamRow(teamName = team2Name, score = team2Score, isLeading = winProbability < 0.5f)

            if (isLive) {
                Spacer(modifier = GlanceModifier.height(12.dp))
                WinProbabilityBar(winProbability)
            }

            // 3x2+: recent balls strip
            if (showRichData && isLive && !recentBalls.isNullOrEmpty()) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                RecentBallsStrip(
                    modifier = GlanceModifier.fillMaxWidth(),
                    balls = recentBalls
                )
            }

            // 4x2: batting pair + current bowler
            if (showBattingData && isLive) {
                if (battingPair != null) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    BattingPairWidget(
                        modifier = GlanceModifier.fillMaxWidth(),
                        striker    = battingPair.first,
                        nonStriker = battingPair.second
                    )
                }
                if (bowler != null) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    CurrentBowlerWidget(
                        modifier = GlanceModifier.fillMaxWidth(),
                        bowler = bowler
                    )
                }
            }
        }
    }

    @Composable
    private fun TeamRow(teamName: String, score: String, isLeading: Boolean) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(if (isLeading) WidgetColorScheme.primaryContainer else WidgetColorScheme.surfaceElevated)
                .cornerRadius(WidgetTheme.radiusMedium)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = teamName,
                style = TextStyle(
                    color = if (isLeading) WidgetColorScheme.primary else WidgetColorScheme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isLeading) FontWeight.Bold else FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = score,
                style = TextStyle(
                    color = if (isLeading) WidgetColorScheme.primary else WidgetColorScheme.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun WinProbabilityBar(probability: Float) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(WidgetColorScheme.primaryContainer)
                .cornerRadius(WidgetTheme.radiusSmall)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${(probability * 100).toInt()}% projected win edge",
                style = TextStyle(
                    color = WidgetColorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
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
                style = TextStyle(fontSize = 32.sp)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = LocalContext.current.getString(R.string.common_no_live_matches),
                style = TextStyle(
                    color = WidgetColorScheme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = LocalContext.current.getString(R.string.widget_check_back_soon),
                style = TextStyle(
                    color = WidgetColorScheme.textSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}

class BouncerGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BouncerGlanceWidget()

    // The refresh chain stops when no widget is placed, so the first one placed restarts it.
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.triggerImmediateUpdate(context)
    }
}
