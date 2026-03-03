package com.tanush.bouncer.utils

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.sentry.Sentry

object ColorUtils {
    /**
     * Lightens a color by a given factor
     */
    fun Color.lighten(factor: Float = 0.1f): Color {
        try {
            return copy(
                red = (red + (1 - red) * factor).coerceIn(0f, 1f),
                green = (green + (1 - green) * factor).coerceIn(0f, 1f),
                blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f)
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Darkens a color by a given factor
     */
    fun Color.darken(factor: Float = 0.1f): Color {
        try {
            return copy(
                red = (red * (1 - factor)).coerceIn(0f, 1f),
                green = (green * (1 - factor)).coerceIn(0f, 1f),
                blue = (blue * (1 - factor)).coerceIn(0f, 1f)
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Adjusts the alpha of a color
     */
    fun Color.withAlpha(alpha: Float): Color {
        try {
            return copy(alpha = alpha.coerceIn(0f, 1f))
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Checks if a color is considered "dark"
     */
    fun Color.isDark(): Boolean {
        try {
            val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
            return luminance < 0.5
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Gets a contrasting color (black or white) for text on this background
     */
    fun Color.getContrastingColor(): Color {
        try {
            return if (isDark()) Color.White else Color.Black
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object StringUtils {
    /**
     * Capitalizes the first letter of each word
     */
    fun String.toTitleCase(): String {
        try {
            return split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Truncates a string to a given length with ellipsis
     */
    fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
        try {
            return if (length <= maxLength) this
            else take(maxLength - ellipsis.length) + ellipsis
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Formats a number with commas
     */
    fun Int.formatWithCommas(): String {
        try {
            return toString().reversed().chunked(3).joinToString(",").reversed()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Formats a large number to K, M, B format
     */
    fun Int.formatCompact(): String {
        try {
            return when {
                this >= 1_000_000_000 -> "${this / 1_000_000_000}B"
                this >= 1_000_000 -> "${this / 1_000_000}M"
                this >= 1_000 -> "${this / 1_000}K"
                else -> toString()
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object TimeUtils {
    /**
     * Formats seconds into MM:SS format
     */
    fun Int.toTimeFormat(): String {
        try {
            val minutes = this / 60
            val seconds = this % 60
            return "%02d:%02d".format(minutes, seconds)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Formats milliseconds into readable time
     */
    fun Long.toReadableTime(): String {
        try {
            val seconds = (this / 1000).toInt()
            val minutes = seconds / 60
            val hours = minutes / 60
        
            return when {
                hours > 0 -> "${hours}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m ${seconds % 60}s"
                else -> "${seconds}s"
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object ValidationUtils {
    /**
     * Validates if a string is a valid team name
     */
    fun String.isValidTeamName(): Boolean {
        try {
            return isNotBlank() && length >= 2 && length <= 50
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Validates if a score string is valid
     */
    fun String.isValidScore(): Boolean {
        try {
            return matches(Regex("\\d+(/\\d+)?"))
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Validates if an over string is valid
     */
    fun String.isValidOvers(): Boolean {
        try {
            return matches(Regex("\\d+(\\.\\d)?"))
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

object PerformanceUtils {
    /**
     * Debounces a function call
     */
    private val debounceJobs = mutableMapOf<String, Job>()
    
    fun debounce(
        key: String = "default",
        delayMillis: Long = 300L,
        coroutineScope: CoroutineScope,
        action: suspend () -> Unit
    ) {
        debounceJobs[key]?.cancel()
        debounceJobs[key] = coroutineScope.launch(Dispatchers.Main) {
            try {
                delay(delayMillis)
                action()
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
}
