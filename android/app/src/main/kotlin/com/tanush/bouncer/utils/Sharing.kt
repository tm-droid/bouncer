package com.tanush.bouncer.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.tanush.bouncer.R
import io.sentry.Sentry

/** Opens the system share sheet with [text]. [subject] is for apps that take one (email). */
fun Context.shareText(text: String, chooserTitle: String? = null, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, chooserTitle)
    if (this !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooser)
}

/** Opens [url] in whatever handles it; a toast when nothing on the device does. */
fun Context.openLink(url: String) {
    try {
        val view = Intent(Intent.ACTION_VIEW, url.toUri())
        if (this !is Activity) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Sentry.captureException(e)
        Toast.makeText(this, R.string.common_no_app_for_link, Toast.LENGTH_SHORT).show()
    }
}
