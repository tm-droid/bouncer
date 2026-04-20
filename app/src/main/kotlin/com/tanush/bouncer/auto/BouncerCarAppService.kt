package com.tanush.bouncer.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class BouncerCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = BouncerCarSession()
}

class BouncerCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen =
        ScoreListScreen(carContext)
}
