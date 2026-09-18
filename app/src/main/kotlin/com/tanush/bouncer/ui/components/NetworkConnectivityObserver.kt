package com.tanush.bouncer.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * True while the device's *default* network cannot reach the internet.
 *
 * This must follow the default network, not every network. It used to
 * `registerNetworkCallback` with an INTERNET request, which reports each matching network
 * independently: on a device holding Wi-Fi and cellular at once (every emulator, and any phone
 * at the moment it hands over from one to the other) `onLost` for the network that went away
 * flipped the flag to offline even though the other network was up and being used, and the
 * "No internet connection" banner sat over a list that had just loaded. `registerDefaultNetworkCallback`
 * only ever describes the one network traffic actually goes through.
 */
@Composable
fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    var isOffline by remember {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        mutableStateOf(!cm.getNetworkCapabilities(cm.activeNetwork).hasInternet())
    }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOffline = false }
            override fun onLost(network: Network) { isOffline = true }
            override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
                isOffline = !caps.hasInternet()
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return isOffline
}

private fun NetworkCapabilities?.hasInternet(): Boolean =
    this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
