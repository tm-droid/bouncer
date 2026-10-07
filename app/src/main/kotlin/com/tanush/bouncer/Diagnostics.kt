package com.tanush.bouncer

import android.content.Context
import android.util.Log
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import java.io.File

/**
 * Whether crash reports and usage data leave the device, and the one place that turns Sentry on
 * or off.
 *
 * On by default. There was no way to turn it off: the SDK was initialised unconditionally, with
 * `sendDefaultPii` on, and nothing in the app said so.
 *
 * **The choice to switch it off is a file, and every doubt about it resolves to off.** It is read
 * before Sentry starts, so it cannot depend on anything that might itself fail and want reporting:
 * `PreferenceStore` can throw on a bad store, and treating that as "on" would have re-enabled
 * reporting for someone who had switched it off. A file either exists or does not. If the check
 * cannot be made, or the file cannot be removed when switching back on, nothing is sent.
 * `backup_rules.xml` and `data_extraction_rules.xml` carry the file to a new phone, so the choice
 * is not undone by a restore.
 */
object Diagnostics {
    private const val TAG = "Diagnostics"
    internal const val OPT_OUT_FILE = "diagnostics_off"

    fun isEnabled(context: Context): Boolean = isEnabledIn(context.filesDir)

    /** Starts Sentry if diagnostics are on. Every `Sentry.*` call is a no-op when it has not started. */
    fun start(context: Context) {
        if (isEnabled(context)) SentryAndroid.init(context.applicationContext, SentryOptionsConfig())
    }

    /**
     * Saves the choice and applies it now, not at the next launch. Returns what is in force
     * afterwards, which is what the switch must show: a choice that could not be saved as "on"
     * stays off.
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val inForce = recordChoice(context.filesDir, enabled)
        if (inForce) start(context) else Sentry.close()
        return inForce
    }

    /** False when the opt-out file is there, and when that cannot be determined. */
    internal fun isEnabledIn(filesDir: File?): Boolean = try {
        filesDir != null && !File(filesDir, OPT_OUT_FILE).exists()
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not read the diagnostics choice; treating it as off", e)
        false
    }

    /** Writes or removes the opt-out file and returns whether diagnostics are on as a result. */
    internal fun recordChoice(filesDir: File?, enabled: Boolean): Boolean {
        if (filesDir == null) return false
        val optOut = File(filesDir, OPT_OUT_FILE)
        try {
            if (enabled) optOut.delete() else optOut.createNewFile()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the diagnostics choice", e)
            // Asked to switch off and the file could not be written: off for this session at
            // least. Asked to switch on and it could not be removed: isEnabledIn still says off.
            if (!enabled) return false
        }
        return isEnabledIn(filesDir)
    }
}
