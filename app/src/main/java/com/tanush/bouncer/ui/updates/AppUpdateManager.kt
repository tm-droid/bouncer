package com.tanush.bouncer.ui.updates

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import com.tanush.bouncer.BuildConfig
import com.tanush.bouncer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object CheckingForUpdates : AppUpdateUiState
    data class UpdateAvailable(val info: UpdateInfo) : AppUpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Int) : AppUpdateUiState
    data class ReadyToInstall(val info: UpdateInfo) : AppUpdateUiState
    data class Error(val info: UpdateInfo, val message: String) : AppUpdateUiState
}

enum class UpdateFeatureType {
    FEATURE,
    FIX,
    SECURITY
}

data class UpdateFeature(
    val text: String,
    val type: UpdateFeatureType = UpdateFeatureType.FEATURE
)

data class UpdateInfo(
    val currentVersion: String,
    val newVersion: String,
    val features: List<UpdateFeature>,
    val sizeMb: Double,
    val estimatedMinutes: Int,
    val downloadUrl: String,
    val forceUpdate: Boolean = false
)

class AppUpdateManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private val _showUpdateBadge = MutableStateFlow(false)
    val showUpdateBadge: StateFlow<Boolean> = _showUpdateBadge.asStateFlow()

    private var latestKnownUpdate: UpdateInfo? = null

    init {
        restorePersistedState()
    }

    suspend fun checkForUpdates(forcePrompt: Boolean = false, silent: Boolean = false): UpdateInfo? {
        if (!silent) {
            _uiState.value = AppUpdateUiState.CheckingForUpdates
            delay(450)
        }

        val simulatedUpdate = buildSimulatedUpdate() ?: run {
            _uiState.value = AppUpdateUiState.Idle
            _showUpdateBadge.value = false
            saveState(StateValue.IDLE)
            return null
        }

        latestKnownUpdate = simulatedUpdate

        if (!shouldPromptFor(simulatedUpdate.newVersion, forcePrompt)) {
            if (!silent) {
                _uiState.value = AppUpdateUiState.Idle
            }
            return null
        }

        _uiState.value = AppUpdateUiState.UpdateAvailable(simulatedUpdate)
        _showUpdateBadge.value = true

        saveUpdateMeta(simulatedUpdate)
        saveLastCheckTime(System.currentTimeMillis())
        saveState(StateValue.AVAILABLE)

        return simulatedUpdate
    }

    suspend fun downloadUpdate() {
        val info = latestKnownUpdate ?: ((uiState.value as? AppUpdateUiState.UpdateAvailable)?.info)
        if (info == null) {
            _uiState.value = AppUpdateUiState.Idle
            return
        }

        saveState(StateValue.DOWNLOADING)

        var progress = max(0, prefs.getInt(KEY_PROGRESS, 0))
        while (progress < 100) {
            delay(75)
            progress = (progress + 3).coerceAtMost(100)
            prefs.edit { putInt(KEY_PROGRESS, progress) }
            _uiState.value = AppUpdateUiState.Downloading(info = info, progress = progress)
        }

        _uiState.value = AppUpdateUiState.ReadyToInstall(info)
        _showUpdateBadge.value = true

        saveState(StateValue.READY)
        prefs.edit {
            putInt(KEY_PROGRESS, 100)
            putString(KEY_READY_VERSION, info.newVersion)
        }
    }

    fun installUpdate() {
        val info = latestKnownUpdate ?: ((uiState.value as? AppUpdateUiState.ReadyToInstall)?.info)
        if (info == null) {
            _uiState.value = AppUpdateUiState.Idle
            return
        }

        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(marketIntent)
        }.onFailure {
            context.startActivity(webIntent)
        }

        prefs.edit {
            putString(KEY_INSTALLED_VERSION, info.newVersion)
            putInt(KEY_PROGRESS, 0)
            remove(KEY_READY_VERSION)
            putString(KEY_STATE, StateValue.IDLE.name)
        }

        _showUpdateBadge.value = false
        _uiState.value = AppUpdateUiState.Idle
    }

    fun dismissUpdate() {
        val version = latestKnownUpdate?.newVersion
            ?: (uiState.value as? AppUpdateUiState.UpdateAvailable)?.info?.newVersion
            ?: (uiState.value as? AppUpdateUiState.ReadyToInstall)?.info?.newVersion
            ?: prefs.getString(KEY_AVAILABLE_VERSION, null)
            ?: return

        val now = System.currentTimeMillis()
        prefs.edit {
            putLong(KEY_LAST_DISMISS_TIME, now)
            putString(KEY_LAST_DISMISSED_VERSION, version)
            putString(KEY_STATE, StateValue.IDLE.name)
        }

        _showUpdateBadge.value = false
        _uiState.value = AppUpdateUiState.Idle
    }

    fun updateLastCheckNow() {
        saveLastCheckTime(System.currentTimeMillis())
    }

    fun getLastCheckTime(): Long = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)

    fun shouldAllowMeteredChecks(): Boolean = prefs.getBoolean(KEY_ALLOW_METERED, false)

    fun setAllowMeteredChecks(allow: Boolean) {
        prefs.edit { putBoolean(KEY_ALLOW_METERED, allow) }
    }

    fun getCurrentVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    private fun buildSimulatedUpdate(): UpdateInfo? {
        val currentVersion = getCurrentVersion()
        val simulatedNewVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "1.1.0"
        } else {
            "1.0.9"
        }

        if (compareVersions(simulatedNewVersion, currentVersion) <= 0) {
            return null
        }

        val features = listOf(
            UpdateFeature(context.getString(R.string.update_mock_feature_one), UpdateFeatureType.FEATURE),
            UpdateFeature(context.getString(R.string.update_mock_feature_two), UpdateFeatureType.FIX),
            UpdateFeature(context.getString(R.string.update_mock_feature_three), UpdateFeatureType.SECURITY),
            UpdateFeature(context.getString(R.string.update_mock_feature_four), UpdateFeatureType.FEATURE)
        )

        return UpdateInfo(
            currentVersion = currentVersion,
            newVersion = simulatedNewVersion,
            features = features,
            sizeMb = 12.5,
            estimatedMinutes = 2,
            downloadUrl = "https://play.google.com/store/apps/details?id=${context.packageName}",
            forceUpdate = false
        )
    }

    private fun restorePersistedState() {
        val persistedState = prefs.getString(KEY_STATE, StateValue.IDLE.name)
        val currentVersion = getCurrentVersion()
        val availableVersion = prefs.getString(KEY_AVAILABLE_VERSION, null)
        val readyVersion = prefs.getString(KEY_READY_VERSION, null)

        val features = listOf(
            UpdateFeature(context.getString(R.string.update_mock_feature_one), UpdateFeatureType.FEATURE),
            UpdateFeature(context.getString(R.string.update_mock_feature_two), UpdateFeatureType.FIX),
            UpdateFeature(context.getString(R.string.update_mock_feature_three), UpdateFeatureType.SECURITY),
            UpdateFeature(context.getString(R.string.update_mock_feature_four), UpdateFeatureType.FEATURE)
        )

        val restoredInfo = when {
            !readyVersion.isNullOrBlank() -> UpdateInfo(
                currentVersion = currentVersion,
                newVersion = readyVersion,
                features = features,
                sizeMb = 12.5,
                estimatedMinutes = 2,
                downloadUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
            )

            !availableVersion.isNullOrBlank() -> UpdateInfo(
                currentVersion = currentVersion,
                newVersion = availableVersion,
                features = features,
                sizeMb = 12.5,
                estimatedMinutes = 2,
                downloadUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
            )

            else -> null
        }

        latestKnownUpdate = restoredInfo

        when (persistedState) {
            StateValue.AVAILABLE.name -> {
                if (restoredInfo != null && shouldPromptFor(restoredInfo.newVersion, forcePrompt = false)) {
                    _uiState.value = AppUpdateUiState.UpdateAvailable(restoredInfo)
                    _showUpdateBadge.value = true
                } else {
                    _uiState.value = AppUpdateUiState.Idle
                }
            }

            StateValue.DOWNLOADING.name -> {
                if (restoredInfo != null) {
                    _uiState.value = AppUpdateUiState.Downloading(
                        info = restoredInfo,
                        progress = prefs.getInt(KEY_PROGRESS, 0)
                    )
                    _showUpdateBadge.value = true
                }
            }

            StateValue.READY.name -> {
                if (restoredInfo != null) {
                    _uiState.value = AppUpdateUiState.ReadyToInstall(restoredInfo)
                    _showUpdateBadge.value = true
                }
            }

            else -> {
                _uiState.value = AppUpdateUiState.Idle
                _showUpdateBadge.value = false
            }
        }
    }

    private fun shouldPromptFor(version: String, forcePrompt: Boolean): Boolean {
        if (forcePrompt) return true

        val dismissedVersion = prefs.getString(KEY_LAST_DISMISSED_VERSION, "")
        val dismissedAt = prefs.getLong(KEY_LAST_DISMISS_TIME, 0L)
        if (dismissedVersion != version) {
            return true
        }

        val repromptAfterMs = REPROMPT_DAYS * DAY_IN_MILLIS
        return System.currentTimeMillis() - dismissedAt >= repromptAfterMs
    }

    private fun compareVersions(versionA: String, versionB: String): Int {
        val a = versionA.split(".").map { it.toIntOrNull() ?: 0 }
        val b = versionB.split(".").map { it.toIntOrNull() ?: 0 }
        val size = max(a.size, b.size)

        repeat(size) { index ->
            val aPart = a.getOrNull(index) ?: 0
            val bPart = b.getOrNull(index) ?: 0
            if (aPart != bPart) return aPart - bPart
        }
        return 0
    }

    private fun saveUpdateMeta(info: UpdateInfo) {
        prefs.edit {
            putString(KEY_AVAILABLE_VERSION, info.newVersion)
        }
    }

    private fun saveLastCheckTime(timeMillis: Long) {
        prefs.edit { putLong(KEY_LAST_CHECK_TIME, timeMillis) }
    }

    private fun saveState(stateValue: StateValue) {
        prefs.edit { putString(KEY_STATE, stateValue.name) }
    }

    private enum class StateValue {
        IDLE,
        AVAILABLE,
        DOWNLOADING,
        READY
    }

    companion object {
        private const val PREF_FILE = "app_update_prefs"
        private const val KEY_STATE = "state"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_AVAILABLE_VERSION = "available_version"
        private const val KEY_READY_VERSION = "ready_version"
        private const val KEY_LAST_DISMISSED_VERSION = "last_dismissed_version"
        private const val KEY_LAST_DISMISS_TIME = "last_dismiss_time"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_ALLOW_METERED = "allow_metered"
        private const val KEY_INSTALLED_VERSION = "installed_version"

        private const val REPROMPT_DAYS = 2L
        private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L
    }
}
