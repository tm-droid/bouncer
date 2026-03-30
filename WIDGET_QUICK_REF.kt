// BOUNCER WIDGETS - QUICK INTEGRATION GUIDE
// ==========================================

// 1️⃣ UPDATE ALL WIDGETS FROM ANYWHERE
// ========================================
import com.tanush.bouncer.ui.widgets.WidgetManager

WidgetManager.updateAllWidgets(
    context = context,
    match = currentLiveMatch,
    winProbability = 0.72f,  // Optional
    recentBalls = listOf("4", "1", "W", "6"),  // Optional
    teamColor = 0xFFD4AF37.toInt()  // Optional
)

// 2️⃣ CLEAR WIDGETS (NO MATCHES)
// ========================================
WidgetManager.clearAllWidgets(context)

// 3️⃣ FORCE IMMEDIATE REFRESH
// ========================================
WidgetManager.forceRefresh(context)

// 4️⃣ INTEGRATION POINTS
// ========================================

// In ViewModel when match updates:
fun onMatchUpdate(match: CricketMatch) {
    _matchState.value = match
    WidgetManager.updateAllWidgets(context, match)
}

// In LiveActivityService when tracking live match:
override fun onMatchScoreUpdate(match: CricketMatch) {
    updateNotification(match)
    WidgetManager.updateAllWidgets(this, match)
}

// In BouncerActivity on app launch:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WidgetManager.forceRefresh(this)
}

// 5️⃣ BACKGROUND UPDATES
// ========================================

// Schedule periodic updates (every 15 min):
WidgetUpdateWorker.schedulePeriodicUpdates(context)

// Cancel scheduled updates:
WidgetUpdateWorker.cancelUpdates(context)

// Trigger immediate one-time update:
WidgetUpdateWorker.triggerImmediateUpdate(context)

// 6️⃣ INDIVIDUAL WIDGET UPDATES
// ========================================

// Update Glance Home Screen Widget:
BouncerGlanceWidget.updateWidgetData(
    context, "IND", "245/3", "AUS", "198/7",
    "IND need 47 from 42", true, 0.72f
)

// Update Lock Screen Widget:
BouncerLockScreenWidget.updateLockWidgetData(
    context, "IND", "245/3", "AUS", "198/7", true
)

// 7️⃣ PREMIUM COLORS (USE IN UI)
// ========================================
val BlackBackground = Color(0xFF0F1115)
val ShinyGoldDeep = Color(0xFFD4AF37)
val CardSurface = Color(0xFF1C2028)
val LiveRed = Color(0xFFFF3B30)

// 8️⃣ WIDGETS AVAILABLE
// ========================================
// ✅ Home Screen Widget (Glance) - Modern, 3 sizes
// ✅ Lock Screen Widget (Glance) - Compact, 1 row
// ✅ Legacy Widget (RemoteViews) - Feature-rich

// 9️⃣ TESTING
// ========================================
// Add widgets: Long press home screen → Widgets → Bouncer
// Lock screen: Lock screen → Customize → Bouncer
// Test updates: Call WidgetManager methods and observe

// 🔟 TROUBLESHOOTING
// ========================================
// Check cache:
val prefs = context.getSharedPreferences("widget_cache", MODE_PRIVATE)
Log.d("Widget", "Data: ${prefs.all}")

// Force refresh if stuck:
WidgetManager.forceRefresh(context)

// ==========================================
// 📚 Full docs: WIDGETS.md
// 📋 Summary: WIDGET_IMPLEMENTATION_SUMMARY.md
// ==========================================
