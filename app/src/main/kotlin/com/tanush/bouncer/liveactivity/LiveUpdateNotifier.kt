package com.tanush.bouncer.liveactivity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tanush.bouncer.R
import com.tanush.bouncer.services.LiveUpdateDismissReceiver
import com.tanush.bouncer.ui.screens.NotificationPrefsManager
import com.tanush.bouncer.ui.theme.getTeamNotificationColor
import com.tanush.bouncer.ui.widgets.WidgetRemoteViewsColors
import com.tanush.bouncer.utils.NotificationPermission

/**
 * Posts a match's score as an Android **Live Update** — a promoted ongoing notification that the
 * system pins to the top of the shade, shows on the lock screen, and summarises as a status-bar
 * chip.
 *
 * The promotion contract is narrow and every clause of it is load-bearing here:
 *
 *  - the style must be one of Standard / BigText / Call / Progress / Metric, so this builds
 *    [NotificationCompat.ProgressStyle] and **never** touches `setCustomContentView`. The stack
 *    this replaces used `DecoratedCustomViewStyle` with two `RemoteViews`, which disqualified it
 *    outright — the promotion request it made by reflection could never have been honoured.
 *  - `POST_PROMOTED_NOTIFICATIONS` must be declared in the manifest.
 *  - the notification must be ongoing and must request promotion explicitly.
 *  - it must not be a group summary, so nothing here groups.
 *
 * `NotificationCompat` carries all of these from core 1.17 onwards, so there is no reflection and
 * no `@RequiresApi`: below API 36 the compat builder drops the promotion fields and the same call
 * produces an ordinary ongoing notification, which is exactly the intended fallback.
 */
class LiveUpdateNotifier(private val context: Context) {

    private val colors by lazy { WidgetRemoteViewsColors.of(context) }

    /**
     * Creates the Live Update channel and retires the old one.
     *
     * A new id is required rather than a settings change: `live_activity_updates` already exists
     * at `IMPORTANCE_LOW` on every device that has run a previous build, channel importance is
     * immutable once created, and a LOW channel has no status-bar presence for the chip to attach
     * to.
     */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel(RETIRED_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.live_update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.live_update_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // A score that ticks every minute must never make a sound; the point of a Live
                // Update is that it is glanceable, not that it interrupts.
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    /** Whether the system will honour a promotion request right now. */
    fun canPromote(): Boolean =
        Build.VERSION.SDK_INT >= PROMOTED_MIN_SDK &&
            NotificationManagerCompat.from(context).canPostPromotedNotifications()

    fun build(content: LiveUpdateContent, largeIcon: Bitmap?): Notification {
        val accent = getTeamNotificationColor(content.accentTeamName)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setSubText(content.subText)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setShortCriticalText(content.shortCriticalText)
            .setLargeIcon(largeIcon)
            .setColor(accent)
            .setColorized(true)
            .setStyle(progressStyle(context, colors, content.progress, accent))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Promotion is what puts this on the lock screen and in the status bar. It is dropped
            // during quiet hours rather than the notification being withheld: the foreground
            // service needs a notification to exist, so the honest lever is prominence, not
            // presence.
            .setRequestPromotedOngoing(!isQuietHoursActive())
            .setContentIntent(openMatchIntent(content.matchId))
            .setDeleteIntent(dismissIntent(content.matchId))
            .addAction(0, context.getString(R.string.live_update_action_scorecard), openMatchIntent(content.matchId))
            .addAction(0, context.getString(R.string.live_update_action_stop), stopIntent(content.matchId))
            .build()
    }

    /**
     * Posts [content], or returns false when it must not be shown — notifications denied, or the
     * user swiped this match away.
     */
    fun post(content: LiveUpdateContent, largeIcon: Bitmap?): Boolean {
        if (LiveUpdateDismissals.isDismissed(content.matchId)) return false
        return NotificationPermission.notify(context, liveUpdateNotificationId(content.matchId), build(content, largeIcon))
    }

    fun cancel(matchId: String) {
        NotificationManagerCompat.from(context).cancel(liveUpdateNotificationId(matchId))
    }

    private fun isQuietHoursActive(): Boolean {
        val settings = NotificationPrefsManager.getInstance(context).settings.value
        if (settings.allowLiveMatchAlertsDuringQuietHours) return false
        return NotificationPrefsManager.isQuietHoursActive()
    }

    private fun openMatchIntent(matchId: String): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(matchId, REQUEST_OPEN),
        Intent(Intent.ACTION_VIEW, Uri.parse("app://bouncer/match/$matchId")).setPackage(context.packageName),
        PENDING_INTENT_FLAGS,
    )

    private fun stopIntent(matchId: String): PendingIntent = broadcast(matchId, LiveUpdateDismissReceiver.ACTION_STOP, REQUEST_STOP)

    private fun dismissIntent(matchId: String): PendingIntent =
        broadcast(matchId, LiveUpdateDismissReceiver.ACTION_DISMISSED, REQUEST_DISMISS)

    private fun broadcast(matchId: String, action: String, salt: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(matchId, salt),
        Intent(context, LiveUpdateDismissReceiver::class.java)
            .setAction(action)
            .putExtra(LiveUpdateDismissReceiver.EXTRA_MATCH_ID, matchId),
        PENDING_INTENT_FLAGS,
    )

    companion object {
        const val CHANNEL_ID = "live_update_scores"

        /** The `DecoratedCustomViewStyle` stack's channel. Deleted, never reused. */
        private const val RETIRED_CHANNEL_ID = "live_activity_updates"

        /** Live Updates are Android 16. Below it the same builder posts an ordinary notification. */
        private const val PROMOTED_MIN_SDK = 36

        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        private const val REQUEST_OPEN = 0
        private const val REQUEST_STOP = 1
        private const val REQUEST_DISMISS = 2

        private fun requestCode(matchId: String, salt: Int): Int = matchId.hashCode() * REQUEST_CODE_STRIDE + salt

        /** Keeps the three request codes of one match from colliding with another match's. */
        private const val REQUEST_CODE_STRIDE = 8
    }
}

/** Stable notification id for a match, so an update replaces its card rather than stacking a new one. */
internal fun liveUpdateNotificationId(matchId: String): Int = ("live_update_$matchId").hashCode()

/**
 * The bar itself. `ProgressStyle` takes its maximum from the sum of the segment lengths, so the
 * segments are what define the scale — there is no separate max to set.
 */
private fun progressStyle(
    context: Context,
    colors: WidgetRemoteViewsColors,
    progress: LiveUpdateProgress,
    accent: Int,
): NotificationCompat.ProgressStyle {
    val style = NotificationCompat.ProgressStyle()
        .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_ball))
    if (progress !is LiveUpdateProgress.Determinate) {
        // No readable over figure. An indeterminate bar says "working" honestly; a zeroed
        // determinate one would claim the innings had not started.
        return style.setProgressIndeterminate(true)
            .addProgressSegment(NotificationCompat.ProgressStyle.Segment(1).setColor(accent))
    }
    progress.segments.forEach { segment ->
        style.addProgressSegment(
            NotificationCompat.ProgressStyle.Segment(segment.length).setColor(tintFor(colors, segment.tint, accent)),
        )
    }
    progress.points.forEach { position ->
        style.addProgressPoint(NotificationCompat.ProgressStyle.Point(position).setColor(colors.error))
    }
    // Segments carry phase meaning of their own, so the bar must not be recoloured by how far
    // along the tracker happens to be.
    return style.setStyledByProgress(false).setProgress(progress.progress)
}

private fun tintFor(colors: WidgetRemoteViewsColors, tint: SegmentTint, accent: Int): Int = when (tint) {
    SegmentTint.POWERPLAY -> colors.success
    SegmentTint.MIDDLE -> colors.textTertiary
    SegmentTint.DEATH -> colors.warning
    SegmentTint.NEUTRAL -> accent
}

/**
 * Matches the user has swiped away this session.
 *
 * The platform's guidance is explicit that a dismissed Live Update must not be reposted — doing so
 * is what makes people revoke the permission for the whole app. In memory rather than on disk
 * because "this session" is the intended scope: a new subscription, or a restart, is a fresh
 * decision.
 */
object LiveUpdateDismissals {
    private val dismissed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun record(matchId: String) {
        dismissed.add(matchId)
    }

    fun clear(matchId: String) {
        dismissed.remove(matchId)
    }

    fun isDismissed(matchId: String): Boolean = dismissed.contains(matchId)
}
