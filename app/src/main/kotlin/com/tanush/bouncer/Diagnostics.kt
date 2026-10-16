package com.tanush.bouncer

import android.content.Context
import com.tanush.bouncer.utils.DeviceClass
import android.util.Log
import com.tanush.bouncer.data.prefs.PreferenceStore
import com.tanush.bouncer.utils.PreferencesHelper
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
    internal const val LEGACY_CHECKED_FILE = "diagnostics_choice_checked"
    private const val LEGACY_PREFERENCE_KEY = "share_diagnostics"

    fun isEnabled(context: Context): Boolean =
        isEnabledIn(context.filesDir) && earlierChoiceAllows(context.filesDir) {
            val wasOn = PreferencesHelper.getBoolean(context, LEGACY_PREFERENCE_KEY, true)
            // An unreadable store answers every read with its default, which here is "on". That
            // is not an answer about what was chosen.
            check(!PreferenceStore.readFailed) { "the preference store could not be read" }
            wasOn
        }

    /**
     * Carries over an opt-out made in the one build that kept the choice as a preference.
     *
     * That build shipped to testers before the choice became a file, and without this the file's
     * absence would read as "on" for anyone who had switched off in it. The preference is read
     * once: "off" becomes the opt-out file, "on" (or never set) is recorded as checked. Until a
     * read has succeeded nothing is sent, the same rule as everywhere else here.
     */
    internal fun earlierChoiceAllows(filesDir: File?, readEarlierChoice: () -> Boolean): Boolean {
        if (filesDir == null) return false
        return try {
            val checked = File(filesDir, LEGACY_CHECKED_FILE)
            if (checked.exists()) return true
            val wasOn = readEarlierChoice()
            if (!wasOn) {
                recordChoice(filesDir, enabled = false)
                // Not marked as checked unless the opt-out is on disk: marked without it, the
                // next launch would find neither the file nor a reason to look again.
                if (!File(filesDir, OPT_OUT_FILE).exists()) return false
            }
            checked.createNewFile()
            wasOn
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the earlier diagnostics choice; treating it as off", e)
            false
        }
    }

    /** Starts Sentry if diagnostics are on. Every `Sentry.*` call is a no-op when it has not started. */
    fun start(context: Context) {
        if (isEnabled(context)) {
            SentryAndroid.init(
                context.applicationContext,
                SentryOptionsConfig(lowEndDevice = DeviceClass.isLowEnd(context)),
            )
        }
    }

    /**
     * Saves the choice and applies it now, not at the next launch. Returns what is in force
     * afterwards, which is what the switch must show: a choice that could not be saved as "on"
     * stays off.
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        recordChoice(context.filesDir, enabled)
        val inForce = isEnabled(context)
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
