package com.tanush.bouncer.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.billing.PlayBillingGateway
import com.tanush.bouncer.data.billing.TipJar

/** Holds the [TipJar] for the Support Bouncer sheet; the sheet opens and closes the Play connection. */
class TipJarViewModel(application: Application) : AndroidViewModel(application) {
    val tipJar = TipJar(PlayBillingGateway(application), viewModelScope)

    override fun onCleared() {
        tipJar.close()
    }
}
