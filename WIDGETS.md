# Bouncer Widgets Documentation

## Overview

Bouncer includes three premium cricket score widgets:
1. **Legacy AppWidget** (`LiveMatchWidgetProvider`) - RemoteViews-based, feature-rich
2. **Glance Home Screen Widget** (`BouncerGlanceWidget`) - Modern Jetpack Glance widget
3. **Lock Screen Widget** (`BouncerLockScreenWidget`) - Compact lock screen variant

All widgets use the premium Bouncer dark theme with gold accents.

---

## Usage

### Updating Widgets from Anywhere in the App

Use the centralized `WidgetManager` API:

```kotlin
import com.tanush.bouncer.ui.widgets.WidgetManager

// Update all widgets with live match data
WidgetManager.updateAllWidgets(
    context = context,
    match = currentMatch,
    winProbability = 0.67f,  // Optional
    recentBalls = listOf("4", "1", "W", "6"),  // Optional for legacy widget
    teamColor = 0xFFD4AF37.toInt()  // Optional for legacy widget
)

// Clear all widgets (no matches state)
WidgetManager.clearAllWidgets(context)

// Force refresh all widgets
WidgetManager.forceRefresh(context)
```

### Individual Widget Updates

#### 1. Glance Home Screen Widget

```kotlin
BouncerGlanceWidget.updateWidgetData(
    context = context,
    team1Name = "IND",
    team1Score = "245/3",
    team2Name = "AUS",
    team2Score = "198/7",
    matchStatus = "IND need 47 from 42 balls",
    isLive = true,
    winProbability = 0.72f
)
```

#### 2. Lock Screen Widget

```kotlin
BouncerLockScreenWidget.updateLockWidgetData(
    context = context,
    team1Abbr = "IND",
    team1Score = "245/3",
    team2Abbr = "AUS",
    team2Score = "198/7",
    isLive = true
)
```

#### 3. Legacy AppWidget

```kotlin
LiveMatchWidgetProvider().updateCache(
    context = context,
    match = match,
    winProbability = 0.67f,
    recentBalls = listOf("4", "1", "W", "6", "0", "1"),
    teamColor = 0xFFD4AF37.toInt()
)
```

---

## Automatic Updates

### Scheduling Background Updates

Widgets automatically schedule periodic updates (every 15 minutes) using AlarmManager:

```kotlin
// Schedule updates (called automatically by WidgetManager)
WidgetUpdateWorker.schedulePeriodicUpdates(context)

// Cancel scheduled updates
WidgetUpdateWorker.cancelUpdates(context)

// Trigger immediate update
WidgetUpdateWorker.triggerImmediateUpdate(context)
```

### Integration Points

Call `WidgetManager.updateAllWidgets()` from:

1. **Live match updates** - When scores change
2. **App launch** - Refresh widgets with latest data
3. **Foreground service** - During live match tracking
4. **Background sync** - When new match data arrives

---

## Widget Features

### Glance Home Screen Widget

- Dark background with gold accents
- Team names and scores
- Live badge (red) when match is live
- Win probability display
- Multiple sizes: 2x1, 3x2, 4x2
- Tap to open MainActivity

### Lock Screen Widget

- Ultra-compact design (1 row)
- Format: "IND 245/3 vs AUS 198/7 • LIVE"
- Minimal UI optimized for lock screen
- Tap to unlock and open app

### Legacy AppWidget

- Premium RemoteViews-based UI
- Team-specific color theming
- Recent balls display (6 balls)
- Win probability gradient bar
- Refresh and next match buttons
- Rich match status

---

## XML Configuration Files

### Home Screen Widget Info
`app/src/main/res/xml/bouncer_glance_widget_info.xml`

### Lock Screen Widget Info
`app/src/main/res/xml/bouncer_lock_widget_info.xml`

### Legacy Widget Info
`app/src/main/res/xml/live_match_widget_info.xml`

---

## AndroidManifest Receivers

All widget receivers are registered in `AndroidManifest.xml`:

```xml
<receiver android:name=".ui.widgets.BouncerGlanceWidgetReceiver"
          android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
        android:resource="@xml/bouncer_glance_widget_info" />
</receiver>
```

---

## Theme Colors

All widgets use the Bouncer premium color palette:

```kotlin
BlackBackground = Color(0xFF0F1115)
CardSurface = Color(0xFF1C2028)
ShinyGoldDeep = Color(0xFFD4AF37)
TextPrimary = Color(0xFFF9FAFB)
TextSecondary = Color(0xFF94A3B8)
LiveRed = Color(0xFFFF3B30)
```

---

## Sentry Integration

All widget operations are tracked with Sentry breadcrumbs:

```kotlin
Sentry.addBreadcrumb(Breadcrumb().apply {
    category = "widget"
    message = "Glance widget data updated: $team1Name vs $team2Name"
    level = SentryLevel.INFO
})
```

---

## Example: Full Integration

```kotlin
class MatchViewModel : ViewModel() {
    fun onMatchUpdate(match: CricketMatch) {
        // Update UI
        _matchState.value = match
        
        // Update all widgets
        WidgetManager.updateAllWidgets(
            context = application,
            match = match,
            winProbability = calculateWinProbability(match),
            recentBalls = match.recentBalls,
            teamColor = match.teams[0].color
        )
        
        // Schedule background updates if live
        if (match.isLive) {
            WidgetUpdateWorker.schedulePeriodicUpdates(application)
        }
    }
}
```

---

## Testing Widgets

1. **Install widgets** - Long press on home screen → Add Widget → Bouncer
2. **Verify updates** - Call `WidgetManager.updateAllWidgets()` and check widget UI
3. **Test empty state** - Call `WidgetManager.clearAllWidgets()` to see "no matches" state
4. **Lock screen** - Add lock screen widget from lock screen customization

---

## Troubleshooting

### Widgets not updating?

```kotlin
// Force immediate refresh
WidgetManager.forceRefresh(context)

// Check SharedPreferences cache
val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
Log.d("Widget", "Team1: ${prefs.getString("team1_name", "N/A")}")
```

### Build errors?

Ensure Glance dependencies are in `app/build.gradle.kts`:

```kotlin
implementation(libs.glance)
implementation(libs.glance.appwidget)
implementation(libs.glance.material3)
```

---

**Built with ❤️ for Bouncer • Premium Cricket Experience**
