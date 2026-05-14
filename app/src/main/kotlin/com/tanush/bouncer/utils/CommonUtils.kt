package com.tanush.bouncer.utils

import androidx.compose.ui.graphics.Color
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ColorUtils {
    fun Color.lighten(factor: Float = 0.1f): Color = copy(
        red = (red + (1 - red) * factor).coerceIn(0f, 1f),
        green = (green + (1 - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f),
    )

    fun Color.darken(factor: Float = 0.1f): Color = copy(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
    )

    fun Color.withAlpha(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))

    fun Color.isDark(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

    fun Color.getContrastingColor(): Color = if (isDark()) Color.White else Color.Black
}

object StringUtils {
    fun String.toTitleCase(): String = split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun String.truncate(maxLength: Int, ellipsis: String = "..."): String =
        if (length <= maxLength) this else take(maxLength - ellipsis.length) + ellipsis

    fun Int.formatWithCommas(): String =
        toString().reversed().chunked(3).joinToString(",").reversed()

    fun Int.formatCompact(): String = when {
        this >= 1_000_000_000 -> "${this / 1_000_000_000}B"
        this >= 1_000_000 -> "${this / 1_000_000}M"
        this >= 1_000 -> "${this / 1_000}K"
        else -> toString()
    }
}

object TimeUtils {
    fun Int.toTimeFormat(): String = "%02d:%02d".format(this / 60, this % 60)

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
    private val scoreRegex = Regex("\\d+(/\\d+)?")
    private val oversRegex = Regex("\\d+(\\.\\d)?")

    fun String.isValidTeamName(): Boolean = isNotBlank() && length in 2..50

    fun String.isValidScore(): Boolean = matches(scoreRegex)

    fun String.isValidOvers(): Boolean = matches(oversRegex)
}

object PerformanceUtils {
    private val debounceJobs = mutableMapOf<String, Job>()

    fun debounce(
        key: String = "default",
        delayMillis: Long = 300L,
        coroutineScope: CoroutineScope,
        action: suspend () -> Unit,
    ) {
        debounceJobs[key]?.cancel()
        debounceJobs[key] = coroutineScope.launch(Dispatchers.Main) {
            try {
                delay(delayMillis)
                action()
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            } finally {
                debounceJobs.remove(key)
            }
        }
    }
}
