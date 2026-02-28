package com.tanush.bouncer.utils

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ColorUtils {
    /**
     * Lightens a color by a given factor
     */
    fun Color.lighten(factor: Float = 0.1f): Color {
        return copy(
            red = (red + (1 - red) * factor).coerceIn(0f, 1f),
            green = (green + (1 - green) * factor).coerceIn(0f, 1f),
            blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f)
        )
    }
    
    /**
     * Darkens a color by a given factor
     */
    fun Color.darken(factor: Float = 0.1f): Color {
        return copy(
            red = (red * (1 - factor)).coerceIn(0f, 1f),
            green = (green * (1 - factor)).coerceIn(0f, 1f),
            blue = (blue * (1 - factor)).coerceIn(0f, 1f)
        )
    }
    
    /**
     * Adjusts the alpha of a color
     */
    fun Color.withAlpha(alpha: Float): Color {
        return copy(alpha = alpha.coerceIn(0f, 1f))
    }
    
    /**
     * Checks if a color is considered "dark"
     */
    fun Color.isDark(): Boolean {
        val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
        return luminance < 0.5
    }
    
    /**
     * Gets a contrasting color (black or white) for text on this background
     */
    fun Color.getContrastingColor(): Color {
        return if (isDark()) Color.White else Color.Black
    }
}

object StringUtils {
    /**
     * Capitalizes the first letter of each word
     */
    fun String.toTitleCase(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
    
    /**
     * Truncates a string to a given length with ellipsis
     */
    fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
        return if (length <= maxLength) this
        else take(maxLength - ellipsis.length) + ellipsis
    }
    
    /**
     * Formats a number with commas
     */
    fun Int.formatWithCommas(): String {
        return toString().reversed().chunked(3).joinToString(",").reversed()
    }
    
    /**
     * Formats a large number to K, M, B format
     */
    fun Int.formatCompact(): String {
        return when {
            this >= 1_000_000_000 -> "${this / 1_000_000_000}B"
            this >= 1_000_000 -> "${this / 1_000_000}M"
            this >= 1_000 -> "${this / 1_000}K"
            else -> toString()
        }
    }
}

object TimeUtils {
    /**
     * Formats seconds into MM:SS format
     */
    fun Int.toTimeFormat(): String {
        val minutes = this / 60
        val seconds = this % 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    /**
     * Formats milliseconds into readable time
     */
    fun Long.toReadableTime(): String {
        val seconds = (this / 1000).toInt()
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}

object ValidationUtils {
    /**
     * Validates if a string is a valid team name
     */
    fun String.isValidTeamName(): Boolean {
        return isNotBlank() && length >= 2 && length <= 50
    }
    
    /**
     * Validates if a score string is valid
     */
    fun String.isValidScore(): Boolean {
        return matches(Regex("\\d+(/\\d+)?"))
    }
    
    /**
     * Validates if an over string is valid
     */
    fun String.isValidOvers(): Boolean {
        return matches(Regex("\\d+(\\.\\d)?"))
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
            delay(delayMillis)
            action()
        }
    }
}
