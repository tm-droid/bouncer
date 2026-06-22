package com.tanush.bouncer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tanush.bouncer.liveactivity.LiveActivityNotificationBuilder
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.utils.PreferencesHelper

/**
 * Main entry point for the Bouncer cricket app.
 *
 * Uses Jetpack Compose with Material 3, edge-to-edge display, and a
 * bottom-navigation–driven single-Activity architecture.
 */
class BouncerActivity : ComponentActivity() {
    private val externalDeepLinkUri = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val splashStart = SystemClock.elapsedRealtime()
        splashScreen.setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - splashStart < 300L
        }

        io.sentry.Sentry.addBreadcrumb(
            io.sentry.Breadcrumb().apply {
                category = "app.lifecycle"
                message = "BouncerActivity.onCreate"
                level = io.sentry.SentryLevel.INFO
            }
        )

        enableEdgeToEdge()

        LiveActivityNotificationBuilder(applicationContext).createNotificationChannel()
        // Onboarding handles the permission prompt for new users.
        // Only request here for users who already completed onboarding (pre-onboarding installs).
        val onboardingDone = PreferencesHelper.getBoolean(this, PreferencesHelper.KEY_ONBOARDING_COMPLETE, false)
            || FavouriteTeamsManager.favourites.value.isNotEmpty()
        if (onboardingDone) requestNotificationPermissionIfNeeded()
        externalDeepLinkUri.value = intent?.dataString

        setContent {
            com.tanush.bouncer.ui.BouncerAppContent(
                externalDeepLinkUri = externalDeepLinkUri.value
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalDeepLinkUri.value = intent.dataString
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
        }
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 4112
    }
}
