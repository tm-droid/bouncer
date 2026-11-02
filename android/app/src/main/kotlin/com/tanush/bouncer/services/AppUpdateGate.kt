package com.tanush.bouncer.services

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.tanush.bouncer.BuildConfig
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.utils.openStoreListing
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The activity half of [AppUpdatePolicy]: asks Play and the `app-config` file, starts Play's
 * immediate update when Play has a forced one, and exposes [required] for the blocking screen.
 *
 * Construct it as an activity property: [ComponentActivity.registerForActivityResult] must run
 * before the activity is started.
 *
 * Every failure leaves the app usable. A non-Play install makes `requestAppUpdateInfo` throw, and a
 * missing or unreadable `app-config` is no minimum; neither may lock anyone out.
 */
class AppUpdateGate(private val activity: ComponentActivity) {
    // Lazy: the activity has no context yet when its properties are initialised.
    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(activity) }

    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    // Declining Play's update screen leaves [required] set, so the blocking screen takes over.
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            sentryBreadcrumb("app.update", "Forced update not completed: result ${result.resultCode}")
        }
    }

    /** Call from `onCreate`. */
    fun check() {
        activity.lifecycleScope.launch {
            val info = playInfo() ?: return@launch
            if (AppUpdatePolicy.playForces(installed, info.updateAvailable(), info.updatePriority())) {
                markRequired("Play has a priority ${info.updatePriority()} update")
                startImmediate(info)
            }
        }
        activity.lifecycleScope.launch {
            val bytes = AppwriteClient.downloadFile(BUCKET_ID, FILE_ID, MAX_AGE_SECONDS) ?: return@launch
            val minimum = AppUpdatePolicy.parseMinimum(bytes)
            if (AppUpdatePolicy.belowMinimum(installed, minimum)) markRequired("versionCode $installed is below $minimum")
        }
    }

    /**
     * Call from `onResume`. Play's guide requires it: an immediate update left mid-flow (the user
     * switched away during the download) is only resumed by asking again.
     */
    fun resumeIfInProgress() {
        if (!_required.value) return
        activity.lifecycleScope.launch {
            val info = playInfo() ?: return@launch
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startImmediate(info)
            }
        }
    }

    /** The blocking screen's button. Play's own update screen when Play has the build, else the listing. */
    fun update() {
        activity.lifecycleScope.launch {
            // An AppUpdateInfo starts one flow only, so a retry needs a fresh one.
            val info = playInfo()
            if (info == null || !info.updateAvailable() || !startImmediate(info)) activity.openStoreListing()
        }
    }

    private val installed: Int get() = BuildConfig.VERSION_CODE

    private fun markRequired(reason: String) {
        if (_required.value) return
        _required.value = true
        sentryBreadcrumb("app.update", "Update required: $reason", SentryLevel.WARNING)
    }

    private suspend fun playInfo(): AppUpdateInfo? = try {
        manager.requestAppUpdateInfo()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // Expected on any install Play did not make (a debug build, a sideloaded APK).
        Log.d(TAG, "No Play update info: ${e.message}")
        null
    }

    private fun startImmediate(info: AppUpdateInfo): Boolean {
        if (!info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) return false
        return manager.startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
        )
    }

    private fun AppUpdateInfo.updateAvailable(): Boolean =
        updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
            updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

    private companion object {
        const val TAG = "AppUpdateGate"
        const val BUCKET_ID = "cricket-stats"
        const val FILE_ID = "app-config"
        // A retirement does not need to land within minutes, and every launch inside the hour is
        // then served from OkHttp's cache instead of a billed download.
        const val MAX_AGE_SECONDS = 60 * 60
    }
}
