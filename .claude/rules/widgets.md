---
paths:
  - "app/src/main/kotlin/**/ui/widgets/**"
  - "app/src/main/AndroidManifest.xml"
---

# Widgets

**The widgets have one snapshot and one writer.** `ui/widgets/WidgetSnapshot.kt` + `WidgetSnapshotSource` build it from the scraped `CricketMatch` (both sides, acronyms, team ids) and merge `match-live-detail` on top **by team id via `alignedTo`**, skipping a finished match and a row that has gone quiet. `WidgetSnapshotStore` is the only thing that writes `widget_cache`, `bouncer_glance_widget` or `bouncer_lock_widget`, projecting the snapshot onto the legacy keys each renderer already reads. `WidgetManager` is deleted. Pinned by `WidgetSnapshotTest`.

What this replaced, so it is not rebuilt:

- `ScorePollingWorker` wrote the payload's `score` (the innings *being batted*) into `team1_score` whichever side was batting, **never wrote `team2_score`**, named the sides by splitting a free-text title, and hardcoded `is_live = true`.
- `LiveMatchWidgetProvider.updateCache` wrote both sides properly, but its only caller was `WidgetManager.updateAllWidgets`, whose only call site passed `match = null` — the clear-everything branch. So every `score_update` FCM push **blanked all three stores**.

That push now goes through `applyPushTo`, which updates the side it names and returns null (change nothing) when it names neither: a number against the wrong team is worse than one a minute old.

**The countdown widget is fed by `CountdownFeed`** (`ui/widgets/CountdownFixture.kt`), from the Upcoming lists the app already keeps: a followed team's next fixture, else the soonest. Until October 2026 nothing wrote the preferences it reads (`updateCountdownData` had no caller), so it showed "no upcoming match" on every device. Inside the last 24 hours the figure is a `Chronometer` in an `AndroidRemoteViews`, which ticks by itself; a Glance `Text` is only as fresh as the 15-minute worker.

**Glance's `InvisibleActionTrampolineActivity` is removed from the manifest.** Only a Glance `LazyColumn`/`LazyVerticalGrid` builds an intent for it and no widget here has one, so it could only be started bare, where it throws in `onCreate` (ANDROID-58, nine of ten from Play's pre-launch devices). A widget that adopts a Glance lazy list must delete that `tools:node="remove"` element.
