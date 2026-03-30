package com.tanush.bouncer.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.launch

private data class WidgetMatchData(
    val matchId: String,
    val team1Name: String,
    val team1Score: String,
    val team2Name: String,
    val team2Score: String,
    val status: String,
    val isLive: Boolean,
    val winProbability: Float
)

/**
 * Widget Configuration Activity
 * 
 * Allows users to select which match to display on the widget.
 * Launched when the widget is first added or when the user taps "Configure" option.
 * 
 * Features:
 * - List of available matches from SharedPreferences cache
 * - Selection persisted per widget instance
 * - Premium dark theme consistent with Bouncer branding
 */
class WidgetConfigurationActivity : ComponentActivity() {
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    // Premium Bouncer Colors
    private val BlackBackground = Color(0xFF0F1115)
    private val CardSurface = Color(0xFF1C2028)
    private val ShinyGoldDeep = Color(0xFFD4AF37)
    private val TextPrimary = Color(0xFFF9FAFB)
    private val TextSecondary = Color(0xFF94A3B8)
    private val LiveRed = Color(0xFFFF3B30)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set the result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)
        
        // Get the widget ID from the intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        // If there's no valid ID, finish immediately
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget configuration: Invalid widget ID"
                level = SentryLevel.WARNING
            })
            finish()
            return
        }
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "widget"
            message = "Widget configuration started for widget $appWidgetId"
            level = SentryLevel.INFO
        })
        
        setContent {
            WidgetConfigurationContent()
        }
    }
    
    @Composable
    private fun WidgetConfigurationContent() {
        val scope = rememberCoroutineScope()
        val availableMatches = remember { loadAvailableMatches() }
        var selectedMatchId by remember { 
            mutableStateOf(BouncerGlanceWidget.getSelectedMatchId(this@WidgetConfigurationActivity, appWidgetId))
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BlackBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    text = "Select Match",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Choose which match to display on this widget",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                if (availableMatches.isEmpty()) {
                    // No matches available
                    NoMatchesAvailable()
                } else {
                    // Match list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(availableMatches) { match ->
                            MatchSelectionCard(
                                match = match,
                                isSelected = match.matchId == selectedMatchId,
                                onSelect = { selectedMatchId = match.matchId }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Confirm button
                    Button(
                        onClick = {
                            scope.launch {
                                confirmSelection(selectedMatchId)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ShinyGoldDeep
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = selectedMatchId != null
                    ) {
                        Text(
                            text = "Confirm Selection",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    private fun NoMatchesAvailable() {
        val scope = rememberCoroutineScope()
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🏏",
                fontSize = 48.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Matches Available",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Check back when matches are scheduled",
                color = TextSecondary,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedButton(
                onClick = { 
                    // Use default (any available match)
                    scope.launch { confirmSelection(null) }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ShinyGoldDeep
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ShinyGoldDeep)
                )
            ) {
                Text("Use Default")
            }
        }
    }
    
    @Composable
    private fun MatchSelectionCard(
        match: WidgetMatchData,
        isSelected: Boolean,
        onSelect: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) ShinyGoldDeep.copy(alpha = 0.2f) else CardSurface)
                .clickable { onSelect() }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live indicator
                if (match.isLive) {
                    Box(
                        modifier = Modifier
                            .background(LiveRed, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    // Teams
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = match.team1Name,
                            color = if (isSelected) ShinyGoldDeep else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = match.team1Score,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = match.team2Name,
                            color = if (isSelected) ShinyGoldDeep else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = match.team2Score,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Status
                    Text(
                        text = match.status,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                
                // Selection indicator
                if (isSelected) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(ShinyGoldDeep, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Load available matches from SharedPreferences cache
     */
    private fun loadAvailableMatches(): List<WidgetMatchData> {
        val matches = mutableListOf<WidgetMatchData>()
        
        // Load from widget cache
        val prefs = getSharedPreferences(BouncerGlanceWidget.PREF_NAME, Context.MODE_PRIVATE)
        val matchCount = prefs.getInt("match_count", 0)
        
        if (matchCount > 0) {
            // Multi-match mode: load from prefixed keys
            for (i in 1..matchCount) {
                val prefix = "match${i}_"
                val team1Name = prefs.getString("${prefix}team1_name", null) ?: continue
                val team2Name = prefs.getString("${prefix}team2_name", null) ?: continue
                
                matches.add(WidgetMatchData(
                    matchId = prefs.getString("${prefix}match_id", "match_$i") ?: "match_$i",
                    team1Name = team1Name,
                    team1Score = prefs.getString("${prefix}team1_score", "-") ?: "-",
                    team2Name = team2Name,
                    team2Score = prefs.getString("${prefix}team2_score", "-") ?: "-",
                    status = prefs.getString("${prefix}status", "Unknown") ?: "Unknown",
                    isLive = prefs.getBoolean("${prefix}is_live", false),
                    winProbability = prefs.getFloat("${prefix}win_prob", 0.5f)
                ))
            }
        } else {
            // Single match mode: load from regular keys
            val team1Name = prefs.getString("team1_name", null)
            val team2Name = prefs.getString("team2_name", null)
            
            if (team1Name != null && team2Name != null) {
                matches.add(WidgetMatchData(
                    matchId = prefs.getString("match_id", "default_match") ?: "default_match",
                    team1Name = team1Name,
                    team1Score = prefs.getString("team1_score", "-") ?: "-",
                    team2Name = team2Name,
                    team2Score = prefs.getString("team2_score", "-") ?: "-",
                    status = prefs.getString("match_status", "Unknown") ?: "Unknown",
                    isLive = prefs.getBoolean("is_live", false),
                    winProbability = prefs.getFloat("win_prob", 0.5f)
                ))
            }
        }
        
        // Also check the general widget cache for more matches
        val widgetCache = getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
        val cacheTeam1 = widgetCache.getString("team1_name", null)
        val cacheTeam2 = widgetCache.getString("team2_name", null)
        
        if (cacheTeam1 != null && cacheTeam2 != null) {
            val cacheMatchId = "cache_match"
            if (matches.none { it.matchId == cacheMatchId }) {
                matches.add(WidgetMatchData(
                    matchId = cacheMatchId,
                    team1Name = cacheTeam1,
                    team1Score = widgetCache.getString("team1_score", "-") ?: "-",
                    team2Name = cacheTeam2,
                    team2Score = widgetCache.getString("team2_score", "-") ?: "-",
                    status = widgetCache.getString("match_status", "Unknown") ?: "Unknown",
                    isLive = widgetCache.getBoolean("is_live", false),
                    winProbability = widgetCache.getFloat("win_probability", 0.5f)
                ))
            }
        }
        
        return matches
    }
    
    /**
     * Confirm selection and update widget
     */
    private suspend fun confirmSelection(matchId: String?) {
        try {
            // Save the selected match ID for this widget
            if (matchId != null) {
                BouncerGlanceWidget.setSelectedMatchId(
                    this@WidgetConfigurationActivity, 
                    appWidgetId, 
                    matchId
                )
            }
            
            // Update the widget
            val glanceManager = GlanceAppWidgetManager(this@WidgetConfigurationActivity)
            val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
            BouncerGlanceWidget().update(this@WidgetConfigurationActivity, glanceId)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "widget"
                message = "Widget $appWidgetId configured with match: $matchId"
                level = SentryLevel.INFO
            })
            
            // Return success
            val resultValue = Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId
            )
            setResult(RESULT_OK, resultValue)
            finish()
            
        } catch (e: Exception) {
            Sentry.captureException(e)
            
            // Still finish with success to avoid widget being stuck
            val resultValue = Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId
            )
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}
