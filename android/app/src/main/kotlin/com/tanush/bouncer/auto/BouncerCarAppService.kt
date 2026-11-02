package com.tanush.bouncer.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.tanush.bouncer.BuildConfig

class BouncerCarAppService : CarAppService() {

    /**
     * Release builds accept only hosts on the androidx allowlist (Android Auto, Automotive OS).
     *
     * This returned `ALLOW_ALL_HOSTS_VALIDATOR` unconditionally — the value androidx documents as
     * debug-only, because it lets any app on the device bind this service and drive the car
     * screens. It is the default in the template the class was started from, and templates are
     * where it usually survives to production.
     */
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = BouncerCarSession()
}

class BouncerCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen =
        TabbedScoreScreen(carContext)
}
