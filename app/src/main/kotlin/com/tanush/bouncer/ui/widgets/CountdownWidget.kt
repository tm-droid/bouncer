package com.tanush.bouncer.ui.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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
import androidx.work.*
import com.tanush.bouncer.BouncerActivity
import com.tanush.bouncer.R
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.concurrent.TimeUnit

/**
 * Data class for scheduled match information
 */
data class ScheduledMatchData(
    val matchId: String = "",
    val team1Name: String,
    val team2Name: String,
    val matchTitle: String = "",
    val venue: String = "",
    val startTimeMillis: Long,
    val matchType: String = "T20"
)

/**
 * Next Match Countdown Widget
 * 
 * Shows time remaining until the next scheduled match starts.
 * Updates every minute via WorkManager.
 * 
 * Features:
 * - Countdown format: "2d 5h 30m" or "Starting in 45 min"
 * - Match name and team names display
 * - Premium dark theme with gold accents
 * - Deep-link to match details on tap
 * - Auto-refresh via WorkManager
 */
class CountdownWidget : GlanceAppWidget() {

    companion object {
        internal const val PREF_NAME = "bouncer_countdown_widget"

        /**
         * Update countdown widget with next scheduled match data
         */
        fun updateCountdownData(
            context: Context,
            matchId: String,
            team1Name: String,
            team2Name: String,
            matchTitle: String,
            venue: String,
            startTimeMillis: Long,
            matchType: String = "T20"
        ) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("match_id", matchId)
                putString("team1_name", team1Name)
                putString("team2_name", team2Name)
                putString("match_title", matchTitle)
                putString("venue", venue)
                putLong("start_time_millis", startTimeMillis)
                putString("match_type", matchType)
                putLong("last_update", System.currentTimeMillis())
                apply()
            }
            
            // Schedule countdown updates
            scheduleCountdownUpdates(context)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Countdown widget updated: $team1Name vs $team2Name"
                level = SentryLevel.INFO
            })
        }
        
        /**
         * Clear countdown widget data
         */
        fun clearCountdownData(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            cancelCountdownUpdates(context)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Countdown widget data cleared"
                level = SentryLevel.INFO
            })
        }
        
        /**
         * Schedule periodic countdown updates via WorkManager (every minute)
         */
        fun scheduleCountdownUpdates(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<CountdownUpdateWorker>(
                1, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            ).addTag("countdown_widget_update")
            .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "countdown_widget_update",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Countdown updates scheduled via WorkManager"
                level = SentryLevel.INFO
            })
        }
        
        /**
         * Cancel countdown updates
         */
        fun cancelCountdownUpdates(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("countdown_widget_update")
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Countdown updates cancelled"
                level = SentryLevel.INFO
            })
        }
        
        /**
         * Format countdown time into human-readable string
         * "2d 5h 30m" for long durations, "Starting in 45 min" for short
         */
        fun formatCountdown(millisRemaining: Long): String {
            if (millisRemaining <= 0) {
                return "Starting now!"
            }
            
            val totalMinutes = millisRemaining / (1000 * 60)
            val days = totalMinutes / (24 * 60)
            val hours = (totalMinutes % (24 * 60)) / 60
            val minutes = totalMinutes % 60
            
            return when {
                days > 0 -> "${days}d ${hours}h ${minutes}m"
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "Starting in ${minutes} min"
                else -> "Starting soon!"
            }
        }
        
        /**
         * Get short format for widget display
         */
        fun formatCountdownShort(millisRemaining: Long): Pair<String, String> {
            if (millisRemaining <= 0) {
                return "NOW" to "Starting"
            }
            
            val totalMinutes = millisRemaining / (1000 * 60)
            val days = totalMinutes / (24 * 60)
            val hours = (totalMinutes % (24 * 60)) / 60
            val minutes = totalMinutes % 60
            
            return when {
                days > 0 -> "${days}d ${hours}h" to "remaining"
                hours > 0 -> "${hours}h ${minutes}m" to "remaining"
                minutes > 0 -> "${minutes}m" to "to start"
                else -> "Soon" to "Starting"
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                CountdownContent(context)
            }
        }
    }

    @Composable
    private fun CountdownContent(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        val matchId = prefs.getString("match_id", null)
        val team1Name = prefs.getString("team1_name", null)
        val team2Name = prefs.getString("team2_name", null)
        val matchTitle = prefs.getString("match_title", "")
        val venue = prefs.getString("venue", "")
        val startTimeMillis = prefs.getLong("start_time_millis", 0L)
        val matchType = prefs.getString("match_type", "T20")
        
        // Calculate time remaining
        val timeRemaining = startTimeMillis - System.currentTimeMillis()
        
        // Create click action with deep-link
        val clickAction = if (!matchId.isNullOrEmpty()) {
            BouncerGlanceWidget.createMatchDeepLinkAction(context, matchId)
        } else {
            actionStartActivity<BouncerActivity>()
        }
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColorScheme.background)
                .cornerRadius(WidgetTheme.radiusLarge)
                .clickable(clickAction)
        ) {
            if (team1Name != null && team2Name != null && startTimeMillis > 0) {
                CountdownLayout(
                    team1Name = team1Name,
                    team2Name = team2Name,
                    matchTitle = matchTitle ?: "",
                    venue = venue ?: "",
                    timeRemainingMillis = timeRemaining,
                    matchType = matchType ?: "T20"
                )
            } else {
                NoUpcomingMatchLayout()
            }
        }
    }

    @Composable
    private fun CountdownLayout(
        team1Name: String,
        team2Name: String,
        matchTitle: String,
        venue: String,
        timeRemainingMillis: Long,
        matchType: String
    ) {
        val (countdownValue, countdownLabel) = formatCountdownShort(timeRemainingMillis)
        val isStartingSoon = timeRemainingMillis in 1..(60 * 60 * 1000) // Less than 1 hour
        
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Header with match type badge
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .background(if (isStartingSoon) WidgetColorScheme.primary else WidgetColorScheme.primaryContainer)
                        .cornerRadius(WidgetTheme.radiusSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isStartingSoon) "SOON" else "UPCOMING",
                        style = TextStyle(
                            color = if (isStartingSoon) WidgetColorScheme.textOnPrimary else WidgetColorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Spacer(modifier = GlanceModifier.width(6.dp))
                
                Text(
                    text = matchType,
                    style = TextStyle(
                        color = WidgetColorScheme.textSecondary,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Countdown display
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = countdownValue,
                        style = TextStyle(
                            color = WidgetColorScheme.primary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = countdownLabel,
                        style = TextStyle(
                            color = WidgetColorScheme.textSecondary,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Teams
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(WidgetColorScheme.surfaceElevated)
                    .cornerRadius(WidgetTheme.radiusMedium)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = team1Name.take(4).uppercase(),
                    style = TextStyle(
                        color = WidgetColorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = GlanceModifier.width(8.dp))
                
                Text(
                    text = "VS",
                    style = TextStyle(
                        color = WidgetColorScheme.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = GlanceModifier.width(8.dp))
                
                Text(
                    text = team2Name.take(4).uppercase(),
                    style = TextStyle(
                        color = WidgetColorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Match title and venue (if space permits)
            if (matchTitle.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                
                Text(
                    text = matchTitle.take(30),
                    style = TextStyle(
                        color = WidgetColorScheme.textSecondary,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }

            if (venue.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = venue.take(28),
                    style = TextStyle(
                        color = WidgetColorScheme.textTertiary,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun NoUpcomingMatchLayout() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📅",
                style = TextStyle(
                    fontSize = 28.sp
                )
            )
            
            Spacer(modifier = GlanceModifier.height(8.dp))
            
            Text(
                text = LocalContext.current.getString(R.string.widget_no_upcoming_matches),
                style = TextStyle(
                    color = WidgetColorScheme.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            
            Spacer(modifier = GlanceModifier.height(4.dp))
            
            Text(
                text = LocalContext.current.getString(R.string.widget_check_the_app_for_schedule),
                style = TextStyle(
                    color = WidgetColorScheme.textSecondary,
                    fontSize = 10.sp
                )
            )
        }
    }
}

/**
 * WorkManager Worker for countdown updates
 * Runs every minute to refresh countdown display
 */
class CountdownUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            // Update the countdown widget
            val glanceManager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            val glanceIds = glanceManager.getGlanceIds(CountdownWidget::class.java)
            
            glanceIds.forEach { glanceId ->
                CountdownWidget().update(context, glanceId)
            }
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Countdown widget updated via WorkManager"
                level = SentryLevel.DEBUG
            })
            
            Result.success()
        } catch (e: Exception) {
            Sentry.captureException(e)
            Result.retry()
        }
    }
}

/**
 * Countdown Widget Receiver
 */
class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CountdownWidget.scheduleCountdownUpdates(context)
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "widget"
            message = "Countdown widget enabled"
            level = SentryLevel.INFO
        })
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        CountdownWidget.cancelCountdownUpdates(context)
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "widget"
            message = "Countdown widget disabled"
            level = SentryLevel.INFO
        })
    }
}
