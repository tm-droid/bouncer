package com.tanush.bouncer.utils

import android.app.ActivityManager
import android.content.Context

/**
 * Whether this phone is one where optional background work costs the user something they notice.
 *
 * Play's pre-launch devices are the measured case: 2 cores, about 4 GB, Android 11, and a time to
 * first frame of 5.4 s against 0.34 s on a current flagship (Sentry, October 2026). Anything the
 * app does for its own benefit (replay capture, extra tracing) is cut back there first.
 */
object DeviceClass {

    fun isLowEnd(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        return isLowEnd(am.isLowRamDevice, Runtime.getRuntime().availableProcessors(), am.memoryClass)
    }

    internal fun isLowEnd(lowRamFlag: Boolean, cores: Int, memoryClassMb: Int): Boolean =
        lowRamFlag || cores <= LOW_END_MAX_CORES || memoryClassMb <= LOW_END_MAX_MEMORY_CLASS_MB

    private const val LOW_END_MAX_CORES = 2
    private const val LOW_END_MAX_MEMORY_CLASS_MB = 128
}
