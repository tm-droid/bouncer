package com.tanush.bouncer.ui.animations

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.*

/**
 * Centralized haptic feedback system for Bouncer cricket app.
 * Provides premium, context-aware haptic patterns for cricket events and UI interactions.
 */
object BouncerHaptics {

    /**
     * Light tap - for button presses, tab switches
     */
    fun lightTap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Medium impact - for card selections, toggles
     */
    fun mediumImpact(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /**
     * Heavy impact - for important actions, milestones
     */
    fun heavyImpact(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Success - for completed actions (double tap pattern)
     */
    fun success(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        CoroutineScope(Dispatchers.Main).launch {
            delay(80)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    /**
     * Error - for errors (triple quick pattern)
     */
    fun error(view: View) {
        val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 50, 50, 50, 50, 50)
            val amplitudes = intArrayOf(0, 100, 0, 100, 0, 100)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Score update - subtle tick for score changes
     */
    fun scoreTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Boundary - medium burst for 4s
     */
    fun boundary(view: View) {
        val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 30, 40, 50)
            val amplitudes = intArrayOf(0, 150, 0, 200)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            CoroutineScope(Dispatchers.Main).launch {
                delay(70)
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
    }

    /**
     * Six - strong celebratory pattern for 6s
     */
    fun six(view: View) {
        val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 50, 30, 70, 30, 100)
            val amplitudes = intArrayOf(0, 180, 0, 200, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            CoroutineScope(Dispatchers.Main).launch {
                delay(80)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    /**
     * Wicket - dramatic pattern for wickets
     */
    fun wicket(view: View) {
        val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 80, 50, 120)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            CoroutineScope(Dispatchers.Main).launch {
                delay(130)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    /**
     * Swipe - light feedback during swipe gesture
     */
    fun swipe(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Selection change - very light tick
     */
    fun selectionTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Generic method to perform any haptic type
     */
    fun perform(view: View, type: HapticType) {
        when (type) {
            HapticType.LightTap -> lightTap(view)
            HapticType.MediumImpact -> mediumImpact(view)
            HapticType.HeavyImpact -> heavyImpact(view)
            HapticType.Success -> success(view)
            HapticType.Error -> error(view)
            HapticType.ScoreTick -> scoreTick(view)
            HapticType.Boundary -> boundary(view)
            HapticType.Six -> six(view)
            HapticType.Wicket -> wicket(view)
            HapticType.Swipe -> swipe(view)
            HapticType.SelectionTick -> selectionTick(view)
        }
    }
}

/**
 * Enum defining all available haptic feedback types
 */
enum class HapticType {
    LightTap,
    MediumImpact,
    HeavyImpact,
    Success,
    Error,
    ScoreTick,
    Boundary,
    Six,
    Wicket,
    Swipe,
    SelectionTick
}

/**
 * Controller wrapping BouncerHaptics with a specific View reference
 */
class BouncerHapticController(private val view: View) {
    
    fun perform(type: HapticType) {
        BouncerHaptics.perform(view, type)
    }

    fun lightTap() = BouncerHaptics.lightTap(view)
    
    fun mediumImpact() = BouncerHaptics.mediumImpact(view)
    
    fun heavyImpact() = BouncerHaptics.heavyImpact(view)
    
    fun success() = BouncerHaptics.success(view)
    
    fun error() = BouncerHaptics.error(view)
    
    fun scoreTick() = BouncerHaptics.scoreTick(view)
    
    fun boundary() = BouncerHaptics.boundary(view)
    
    fun six() = BouncerHaptics.six(view)
    
    fun wicket() = BouncerHaptics.wicket(view)
    
    fun swipe() = BouncerHaptics.swipe(view)
    
    fun selectionTick() = BouncerHaptics.selectionTick(view)
}

/**
 * Composable helper to get a haptic controller for the current View
 */
@Composable
fun rememberHaptic(): BouncerHapticController {
    val view = LocalView.current
    return remember(view) { BouncerHapticController(view) }
}

/**
 * Modifier extension for clickable elements with haptic feedback
 * 
 * Example:
 * ```
 * Box(
 *     modifier = Modifier.hapticClickable(HapticType.MediumImpact) {
 *         // Handle click
 *     }
 * )
 * ```
 */
fun Modifier.hapticClickable(
    hapticType: HapticType = HapticType.LightTap,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    
    this.clickable(enabled = enabled) {
        if (enabled) {
            BouncerHaptics.perform(view, hapticType)
            onClick()
        }
    }
}

/**
 * Modifier extension for clickable elements with custom haptic action
 * Allows full control over when and how haptic feedback is triggered
 */
fun Modifier.clickableWithHaptic(
    enabled: Boolean = true,
    onClickWithHaptic: (BouncerHapticController) -> Unit
): Modifier = composed {
    val view = LocalView.current
    val hapticController = remember(view) { BouncerHapticController(view) }
    
    this.clickable(enabled = enabled) {
        if (enabled) {
            onClickWithHaptic(hapticController)
        }
    }
}
