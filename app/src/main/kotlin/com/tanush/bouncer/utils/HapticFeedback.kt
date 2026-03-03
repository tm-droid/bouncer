package com.tanush.bouncer.utils

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import io.sentry.Sentry

class HapticFeedback(private val view: View) {
    fun click() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun longPress() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun success() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun error() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

@Composable
fun rememberHapticFeedback(): HapticFeedback {
    val view = LocalView.current
    return remember(view) { HapticFeedback(view) }
}
