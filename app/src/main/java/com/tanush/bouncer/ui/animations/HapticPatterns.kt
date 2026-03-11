package com.tanush.bouncer.ui.animations

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intent-driven haptic patterns for Bouncer.
 *
 * Why this object exists:
 * - Keeps haptic semantics tied to user intent, not random constants.
 * - Makes timing easy to coordinate with animation keyframes.
 */
object HapticPatterns {

    /**
     * Single light tap for standard button presses.
     */
    fun singleTap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Success: two pulses with increased intensity to reinforce completion.
     */
    fun success(view: View) {
        performPattern(
            view = view,
            timings = longArrayOf(0, 24, 40, 38),
            amplitudes = intArrayOf(0, 90, 0, 170),
            fallback = {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                CoroutineScope(Dispatchers.Main).launch {
                    delay(85)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
            }
        )
    }

    /**
     * Warning: short, rapid pulses to signal caution without feeling punitive.
     */
    fun warning(view: View) {
        performPattern(
            view = view,
            timings = longArrayOf(0, 15, 28, 15, 28, 15),
            amplitudes = intArrayOf(0, 70, 0, 80, 0, 90),
            fallback = {
                CoroutineScope(Dispatchers.Main).launch {
                    repeat(3) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        delay(45)
                    }
                }
            }
        )
    }

    /**
     * Error: one strong pulse for immediate rejection clarity.
     */
    fun error(view: View) {
        performPattern(
            view = view,
            timings = longArrayOf(0, 60),
            amplitudes = intArrayOf(0, 220),
            fallback = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        )
    }

    /**
     * Delight: cascading taps for celebration moments (e.g., six, milestone).
     */
    fun delight(view: View) {
        performPattern(
            view = view,
            timings = longArrayOf(0, 20, 28, 26, 34, 34, 40, 42),
            amplitudes = intArrayOf(0, 80, 0, 120, 0, 160, 0, 210),
            fallback = {
                CoroutineScope(Dispatchers.Main).launch {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    delay(70)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    delay(70)
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            }
        )
    }

    /**
     * Helper to run haptic at animation key moments.
     * Example: call this exactly at score flip midpoint.
     */
    fun triggerAt(view: View, delayMs: Long, pattern: (View) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            delay(delayMs)
            pattern(view)
        }
    }

    private fun performPattern(
        view: View,
        timings: LongArray,
        amplitudes: IntArray,
        fallback: () -> Unit
    ) {
        val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator == null) {
            fallback()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings.sum())
            fallback()
        }
    }
}

class HapticPatternController(private val view: View) {
    fun singleTap() = HapticPatterns.singleTap(view)
    fun success() = HapticPatterns.success(view)
    fun warning() = HapticPatterns.warning(view)
    fun error() = HapticPatterns.error(view)
    fun delight() = HapticPatterns.delight(view)
    fun triggerAt(delayMs: Long, pattern: (View) -> Unit) = HapticPatterns.triggerAt(view, delayMs, pattern)
}

@Composable
fun rememberHapticPatterns(): HapticPatternController {
    val view = LocalView.current
    return remember(view) { HapticPatternController(view) }
}

/**
 * Example:
 * ```kotlin
 * val haptic = rememberHapticPatterns()
 * Button(onClick = {
 *     haptic.singleTap()
 *     // Trigger UI update animation
 * }) { Text("Refresh") }
 * ```
 */
