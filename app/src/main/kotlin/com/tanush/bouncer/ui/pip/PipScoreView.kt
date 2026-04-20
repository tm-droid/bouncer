package com.tanush.bouncer.ui.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import com.tanush.bouncer.R
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Helper for Picture-in-Picture support, providing a compact live-score overlay.
 */
object PipScoreView {

    private const val ACTION_REFRESH = "com.tanush.bouncer.PIP_REFRESH"
    private const val PIP_ASPECT_W = 16
    private const val PIP_ASPECT_H = 9

    /** Builds [PictureInPictureParams] with a 16:9 aspect ratio and a refresh action. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun buildPipParams(context: Context, matchId: String): PictureInPictureParams {
        val refreshIntent = Intent(ACTION_REFRESH).apply {
            setPackage(context.packageName)
            putExtra("matchId", matchId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            matchId.hashCode(),
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val refreshAction = RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
            "Refresh",
            "Refresh score",
            pendingIntent
        )

        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(PIP_ASPECT_W, PIP_ASPECT_H))
            .setActions(listOf(refreshAction))
            .build()
    }

    /** Enters PiP mode for the given activity, displaying the live score for [matchId]. */
    fun enterPipMode(activity: Activity, matchId: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = buildPipParams(activity, matchId)
                activity.enterPictureInPictureMode(params)

                Sentry.addBreadcrumb(
                    Breadcrumb().apply {
                        category = "pip"
                        message = "Entered PiP mode"
                        level = SentryLevel.INFO
                        setData("matchId", matchId)
                    }
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
}
