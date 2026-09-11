package com.tanush.bouncer.ui.components.v2

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.utils.calendarDaysUntil
import com.tanush.bouncer.utils.parseMatchStart
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A match's status line as a reader wants it. Cricbuzz's `Match starts at Sep 12, 23:00 GMT` becomes
 * "Starts tomorrow, 7:00 AM" in the device's zone and clock format; any other status is unchanged.
 */
@Composable
internal fun matchStatusText(status: String): String {
    val start = remember(status) { parseMatchStart(status, ZonedDateTime.now()) } ?: return status
    val locale = LocalConfiguration.current.locales[0]
    val clockPattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    val time = DateTimeFormatter.ofPattern(clockPattern, locale).format(start)
    return when (calendarDaysUntil(start, LocalDate.now())) {
        0L -> stringResource(R.string.match_starts_today, time)
        1L -> stringResource(R.string.match_starts_tomorrow, time)
        else -> stringResource(
            R.string.match_starts_on,
            DateTimeFormatter.ofPattern("EEE d MMM", locale).format(start),
            time,
        )
    }
}
