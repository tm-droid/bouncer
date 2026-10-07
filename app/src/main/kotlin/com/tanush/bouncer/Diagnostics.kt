package com.tanush.bouncer

import android.content.Context
import com.tanush.bouncer.utils.PreferencesHelper
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

/**
 * Whether crash reports and usage data leave the device, and the one place that turns Sentry on
 * or off.
 *
 * On by default. There was no way to turn it off: the SDK was initialised unconditionally, with
 * `sendDefaultPii` on, and nothing in the app said so.
 */
object Diagnostics {

    /**
     * True unless the user has switched diagnostics off. A preference that cannot be read counts
     * as on: this is read before Sentry starts, and failing closed there would mean a broken
     * preference store also silenced the report of its own failure.
     */
    fun isEnabled(context: Context): Boolean =
        runCatching { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_SHARE_DIAGNOSTICS, true) }
            .getOrDefault(true)

    /** Starts Sentry if diagnostics are on. Every `Sentry.*` call is a no-op when it has not started. */
    fun start(context: Context) {
        if (isEnabled(context)) SentryAndroid.init(context.applicationContext, SentryOptionsConfig())
    }

    /** Saves the choice and applies it now, not at the next launch. */
    fun setEnabled(context: Context, enabled: Boolean) {
        PreferencesHelper.setBoolean(context, PreferencesHelper.KEY_SHARE_DIAGNOSTICS, enabled)
        if (enabled) start(context) else Sentry.close()
    }
}
