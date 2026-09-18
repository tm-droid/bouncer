package com.tanush.bouncer.liveactivity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.tanush.bouncer.R
import com.tanush.bouncer.services.LiveUpdateDismissReceiver
import com.tanush.bouncer.ui.screens.NotificationPrefsManager
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import com.tanush.bouncer.ui.theme.TeamColors
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
 *    [NotificationCompat.BigTextStyle] and **never** touches `setCustomContentView`. The stack
 *    this replaces used `DecoratedCustomViewStyle` with two `RemoteViews`, which disqualified it
 *    outright — the promotion request it made by reflection could never have been honoured.
 *  - `POST_PROMOTED_NOTIFICATIONS` must be declared in the manifest.
 *  - the notification must be ongoing and must request promotion explicitly.
 *  - it must not be a group summary, so nothing here groups.
 *
 * `NotificationCompat` carries all of these from core 1.17 onwards, so there is no reflection and
 * no `@RequiresApi`: below API 36 the compat builder drops the promotion fields and the same call
 * produces an ordinary ongoing notification, which is exactly the intended fallback.
 *
 * `BigTextStyle` rather than `ProgressStyle` because a cricket card is a scoreboard, not a
 * position. `ProgressStyle` allows exactly one line of text below the title, which is why the card
 * it replaced could show a batting pair and nothing else — no opposition total, no bowler, no
 * target. The bar it bought in exchange carried a ball count nobody reads, and none of the iOS
 * Live Activities this is modelled on has one.
 *
 * **`setColorized(true)` is a promotion requirement, not a styling choice.** It is the one flag
 * `hasPromotableCharacteristics()` actually turns on, measured on a stock API 36 emulator across
 * every combination of style, `shortCriticalText`, large icon and actions: colorized present is
 * true, colorized absent is false, and nothing else moves the verdict — a `ProgressStyle` card
 * with a chip and no colorized is refused just as a `BigTextStyle` one is. So the lever on
 * appearance is the **colour**, not whether there is one: the flat mustard this replaced was
 * `getTeamNotificationColor` missing on every match, because it matched full country names against
 * the payload's Cricbuzz short codes.
 *
 * **There are two cards, chosen by what the device can actually do.** A promoted notification may
 * not carry `RemoteViews` — measured the same way, and it is absolute: `setCustomContentView`,
 * `setCustomBigContentView`, either of them alongside a style, and `DecoratedCustomViewStyle` all
 * make `hasPromotableCharacteristics()` false. So a designed layout and promotion cannot coexist,
 * and a template card is one TextView of left-aligned text with no column for the figures and no
 * second crest.
 *
 * The resolution is that promotion is **not available on most of the install base**: it is Android
 * 16, `minSdk` is 26, and even an API 36 device can report `canPostPromotedNotifications() == false`
 * (the stock emulator does). Where it is available the template card is worth its constraints,
 * because the chip, the lock-screen pin and the top-of-shade slot are the feature. Where it is not,
 * the template buys *nothing* and costs the design, so [LiveUpdateScoreCard] renders the real
 * layout instead. Nobody is worse off than a single-path build, on either side of the line.
 */
class LiveUpdateNotifier(private val context: Context) {

    /**
     * Creates the Live Update channel and retires the old one.
     *
     * A new id is required rather than a settings change: `live_activity_updates` already exists
     * at `IMPORTANCE_LOW` on every device that has run a previous build, channel importance is
     * immutable once created, and a LOW channel has no status-bar presence for the chip to attach
     * to.
     */
    fun ensureChannel() {
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

    /**
     * [largeIcon] is the batting side's crest and [opponentIcon] the other side's. Only the custom
     * layout draws the second one; the template has a single icon slot.
     */
    /**
     * [largeIcon] is the batting side's crest and [opponentIcon] the other side's. Only the custom
     * layout draws the second one; the promoted template has a single icon slot.
     *
     * Everything the two cards share is set first, and the fork below is exactly what they disagree
     * on — which is small, and worth keeping visible in one place.
     */
    fun build(
        content: LiveUpdateContent,
        largeIcon: Bitmap?,
        opponentIcon: Bitmap? = null,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setSubText(content.subText)
            // Set on both paths: the custom-view card falls back to these if the system ever
            // declines its RemoteViews, and they are what a screen reader reads either way.
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setColor(accentColor(context, content.accentTeamName))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openMatchIntent(content.matchId))
            .setDeleteIntent(dismissIntent(content.matchId))
            .addAction(0, context.getString(R.string.live_update_action_scorecard), openMatchIntent(content.matchId))
            .addAction(0, context.getString(R.string.live_update_action_stop), stopIntent(content.matchId))

        if (canPromote()) {
            builder
                .setShortCriticalText(content.shortCriticalText)
                .setLargeIcon(largeIcon)
                // Load-bearing for promotion; see the class comment. Not a style preference.
                .setColorized(true)
                // Promotion is what puts this on the lock screen and in the status bar. It is
                // dropped during quiet hours rather than the notification being withheld: the
                // foreground service needs a notification to exist, so the honest lever is
                // prominence, not presence.
                .setRequestPromotedOngoing(!isQuietHoursActive())
            // An empty bigText would collapse the expanded card to the title alone, which is worse
            // than the standard template's own expansion of the same two lines.
            content.bigText.takeIf { it.isNotBlank() }?.let {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
            }
        } else {
            // `DecoratedCustomViewStyle` rather than a bare custom view: the system keeps drawing
            // the header and the action buttons, so only the score region is ours and the card
            // still matches the shade it sits in. Not colorized — colorized exists to buy
            // promotion, which this path has already lost, and the references are neutral cards.
            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(LiveUpdateScoreCard.collapsed(context, content, largeIcon))
                .setCustomBigContentView(
                    LiveUpdateScoreCard.expanded(context, content, largeIcon, opponentIcon),
                )
        }
        return builder.build()
    }

    /**
     * Posts [content], or returns false when it must not be shown — notifications denied, or the
     * user swiped this match away.
     */
    fun post(content: LiveUpdateContent, largeIcon: Bitmap?, opponentIcon: Bitmap? = null): Boolean {
        if (LiveUpdateDismissals.isDismissed(content.matchId)) return false
        return NotificationPermission.notify(
            context,
            liveUpdateNotificationId(content.matchId),
            build(content, largeIcon, opponentIcon),
        )
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
        Intent(Intent.ACTION_VIEW, "app://bouncer/match/$matchId".toUri()).setPackage(context.packageName),
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

/**
 * The batting side's colour, as the **fill** of a colorized card.
 *
 * [TeamColors.surface], not `primary`. `primary` is contrast-lifted to be legible *on* the ground,
 * which is the opposite of what is wanted from a colour that *is* the ground — it came out as a
 * highlighter-green card. `surface` is the registry's own team-tinted-card recipe (the brand colour
 * washed over the ground, in the direction the ground goes), which is what the design references
 * look like and what the platform can pick a readable text colour against.
 *
 * [TeamColorRegistry] rather than the retired `getTeamNotificationColor`: it is a plain object (so
 * it is safe from a Service), it knows short codes through its acronym table, and unknown teams get
 * a deterministic hash colour instead of everything collapsing onto one default.
 *
 * The ground is read from [context] on every call, never cached: `RemoteViews`-style colours are
 * plain ARGB ints resolved per `Context`, and the notifier outlives a theme change.
 */
private fun accentColor(context: Context, teamName: String): Int {
    val colors = WidgetRemoteViewsColors.of(context)
    val ground = TeamColorRegistry.Ground(
        background = Color(colors.background),
        surface = Color(colors.surface),
        ink = Color(colors.textPrimary),
    )
    return TeamColorRegistry.forTeam(teamName, ground).surface.toArgb()
}

/** Stable notification id for a match, so an update replaces its card rather than stacking a new one. */
internal fun liveUpdateNotificationId(matchId: String): Int = ("live_update_$matchId").hashCode()

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
