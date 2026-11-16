package com.tanush.bouncer.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

/** The app's Play listing, where "What's new" is published. Play Store first, a browser if there is none. */
fun Context.openStoreListing() {
    if (!open("market://details?id=$packageName")) open(storeListingUrl())
}

/** The listing's web address, for sharing. */
fun Context.storeListingUrl(): String = "https://play.google.com/store/apps/details?id=$packageName"

/** Opens [uri] in whatever handles it. False when nothing on the device does. */
fun Context.open(uri: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    Log.d("StoreListing", "Nothing opens $uri", e)
    false
}
