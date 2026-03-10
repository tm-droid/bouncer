# Live Activities System - Bouncer Android

## Overview

The Live Activities system provides iOS-like persistent live match notifications and in-app live score cards for the Bouncer cricket app.

## Components

### 1. LiveActivityStyles.kt
Location: `app/src/main/java/com/tanush/bouncer/ui/components/LiveActivityStyles.kt`

Premium live activity card styles that can be displayed as floating overlays or in-app live scorecards.

#### Available Styles

1. **COMPACT** - Minimal single-line card
   - Team abbreviations + scores in one line
   - Live red dot indicator
   - Height: ~48dp

2. **EXPANDED** - Standard two-row card
   - Team logos, names, scores, overs
   - Status bar with gold text
   - Glass card with subtle border
   - Height: ~120dp

3. **DETAILED** - Full scorecard layout
   - Complete score breakdown
   - Current batsmen with individual scores
   - Current bowler with figures
   - Required run rate (RRR) + Current run rate (CRR)
   - Last 6 balls as colored circles
   - Height: ~200dp

4. **MINIMAL_PILL** - Floating pill overlay
   - Compact pill shape (100dp rounded corners)
   - "IND 245/3 vs AUS 198/7"
   - Tiny red live dot
   - Can overlay other screens
   - Height: ~36dp

5. **BROADCAST** - TV-broadcast-style scoreboard
   - Gold gradient header bar
   - Team rows with colored borders
   - Large score typography
   - Animated gold separator line
   - "LIVE" badge with pulse animation
   - Bottom ticker with status text
   - Height: ~180dp

#### Usage Example

```kotlin
import com.tanush.bouncer.ui.components.*

// Create sample data
val liveData = LiveActivityData(
    team1Name = "India",
    team2Name = "Australia",
    team1Abbreviation = "IND",
    team2Abbreviation = "AUS",
    team1Score = "245/3",
    team2Score = "198/7",
    team1Overs = "(38.2)",
    team2Overs = "(40.0)",
    status = "IND need 48 runs from 70 balls",
    currentBatsman1 = "V Kohli 87* (52)",
    currentBatsman2 = "S Iyer 34 (28)",
    currentBowler = "P Cummins 2/45 (7.2)",
    requiredRunRate = "6.17",
    currentRunRate = "6.42",
    lastSixBalls = listOf("1", "4", "0", "W", "6", "2"),
    isLive = true
)

// Display a card
@Composable
fun MyScreen() {
    Column {
        // Compact style
        CompactLiveCard(data = liveData)
        
        // Expanded style
        ExpandedLiveCard(data = liveData)
        
        // Detailed style
        DetailedLiveCard(data = liveData)
        
        // Minimal pill style
        MinimalPillCard(data = liveData)
        
        // Broadcast style
        BroadcastLiveCard(data = liveData)
    }
}

// Style picker
@Composable
fun SettingsScreen() {
    var selectedStyle by remember { mutableStateOf(LiveActivityStyle.EXPANDED) }
    
    LiveActivityStylePicker(
        selectedStyle = selectedStyle,
        onStyleSelected = { selectedStyle = it }
    )
}
```

### 2. LiveActivityService.kt
Location: `app/src/main/kotlin/com/tanush/bouncer/services/LiveActivityService.kt`

A foreground service that shows persistent live notifications.

#### Features

- Persistent foreground notification for live matches
- Custom RemoteViews for notification layout
- Updates notification content when match data changes
- Supports multiple notification styles
- Auto-stops when match ends
- Sentry breadcrumb tracking

#### Usage Example

```kotlin
import com.tanush.bouncer.services.LiveActivityService
import com.tanush.bouncer.ui.components.LiveActivityData
import com.tanush.bouncer.ui.components.LiveActivityStyle

// Start live activity
val matchData = LiveActivityData(
    team1Name = "India",
    team2Name = "Australia",
    team1Abbreviation = "IND",
    team2Abbreviation = "AUS",
    team1Score = "245/3",
    team2Score = "198/7",
    team1Overs = "(38.2)",
    team2Overs = "(40.0)",
    status = "IND need 48 runs from 70 balls",
    currentBatsman1 = "V Kohli 87* (52)",
    currentBatsman2 = "S Iyer 34 (28)",
    currentBowler = "P Cummins 2/45 (7.2)",
    requiredRunRate = "6.17",
    currentRunRate = "6.42",
    lastSixBalls = listOf("1", "4", "0", "W", "6", "2"),
    isLive = true
)

// Start the service
LiveActivityService.startLiveActivity(
    context = context,
    matchData = matchData,
    style = LiveActivityStyle.BROADCAST
)

// Update the activity (when match data changes)
LiveActivityService.updateLiveActivity(
    context = context,
    matchData = updatedMatchData
)

// Stop the activity (when match ends)
LiveActivityService.stopLiveActivity(context)
```

## AndroidManifest Configuration

The service is already registered in AndroidManifest.xml with required permissions:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".services.LiveActivityService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

## Design Tokens

### Colors
- **BlackBackground**: `#141720`
- **CardSurface**: `#1A1D23`
- **ShinyGoldDeep**: `#D4AF37`
- **LiveRed**: `#E53935`
- **TextPrimary**: `#F5F5F5`
- **TextSecondary**: `#AAAAAA`

### Ball Indicator Colors
- **Dot**: Gray `#555555`
- **Runs**: White `#F5F5F5`
- **Four**: Blue `#2196F3`
- **Six**: Gold `#D4AF37`
- **Wicket**: Red `#E53935`

## Integration with Existing Systems

### With LiveNotificationManager
The LiveActivityService complements the existing `LiveNotificationManager` by providing:
- Persistent foreground notifications (vs. dismissible alerts)
- In-app live score cards (vs. system notifications only)
- User-selectable styles (vs. fixed layout)

### With CricketMatch Model
The `LiveActivityData` can be easily created from a `CricketMatch` object:

```kotlin
fun CricketMatch.toLiveActivityData(): LiveActivityData {
    val team1 = teams.getOrNull(0)
    val team2 = teams.getOrNull(1)
    
    return LiveActivityData(
        team1Name = team1?.name ?: "",
        team2Name = team2?.name ?: "",
        team1Abbreviation = team1?.acronym ?: "",
        team2Abbreviation = team2?.acronym ?: "",
        team1Score = team1?.score ?: "",
        team2Score = team2?.score ?: "",
        team1Overs = team1?.overs ?: "",
        team2Overs = team2?.overs ?: "",
        status = status,
        currentBatsman1 = scrapedBatsmen?.getOrNull(0)?.let { 
            "${it.name} ${it.runs}* (${it.balls})" 
        } ?: "",
        currentBatsman2 = scrapedBatsmen?.getOrNull(1)?.let { 
            "${it.name} ${it.runs} (${it.balls})" 
        } ?: "",
        currentBowler = currentBowler?.let { 
            "${it.name} ${it.wickets}/${it.runs} (${it.overs})" 
        } ?: "",
        requiredRunRate = score?.requiredRunRate ?: "",
        currentRunRate = score?.currentRunRate ?: "",
        lastSixBalls = recentBalls ?: emptyList(),
        isLive = isLive
    )
}
```

## Testing

All composables include `@Preview` annotations for visual testing in Android Studio.

Preview the styles:
1. Open `LiveActivityStyles.kt` in Android Studio
2. Switch to Split or Design view
3. View all 5 style previews + style picker

## Notes

- Uses 8pt spacing system throughout
- All cards use glass morphism effect (semi-transparent surfaces)
- Animations are optimized for 60fps performance
- Notification layouts use RemoteViews compatible with all Android versions
- Service properly handles lifecycle (onCreate → onStartCommand → onDestroy)
- Sentry breadcrumbs track all service lifecycle events

## Future Enhancements

Potential additions:
- Widget support for home screen live scores
- Lock screen live activity (Android 14+)
- Customizable color schemes per team
- Landscape optimized layouts
- Picture-in-Picture (PIP) mode support
