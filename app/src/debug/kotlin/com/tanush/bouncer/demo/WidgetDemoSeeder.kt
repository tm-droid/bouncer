package com.tanush.bouncer.demo

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.tanush.bouncer.ui.widgets.BouncerGlanceWidget
import com.tanush.bouncer.ui.widgets.BouncerGlanceWidgetReceiver

/**
 * Seeds the Glance widget's SharedPreferences cache with camera-ready data and
 * refreshes any pinned widgets. Formats mirror BouncerGlanceWidget's parsers:
 * recent balls "1,4,6", batting pair "Name|runs|balls;Name|runs|balls",
 * bowler "name|wickets|runs|overs".
 */
internal object WidgetDemoSeeder {

    suspend fun seed(context: Context) {
        BouncerGlanceWidget.updateWidgetData(
            context = context,
            team1Name = "IND",
            team1Score = "178/5",
            team2Name = "AUS",
            team2Score = "186/5",
            matchStatus = "India need 9 runs in 7 balls",
            isLive = true,
            winProbability = 0.72f,
            recentBalls = "1,4,6,W,2,4",
            battingPair = "Suryakumar Yadav|78|45;Ravindra Jadeja|3|3",
            currentBowler = "Mitchell Starc|1|42|3.5",
        )
        runCatching { BouncerGlanceWidget().updateAll(context) }
    }

    fun requestPin(context: Context) {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(
                ComponentName(context, BouncerGlanceWidgetReceiver::class.java),
                null,
                null,
            )
        }
    }
}
