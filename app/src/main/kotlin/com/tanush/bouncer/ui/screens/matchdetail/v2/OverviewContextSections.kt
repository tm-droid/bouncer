package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.models.MatchContext
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The words the server wrote about this match as a whole: the summary once it is over, the
 * preview before that. One or the other, never both — a finished match's preview is yesterday's
 * news, and the header above this tab already carries the result.
 */
internal fun matchSurfaceFor(context: MatchContext?, isCompleted: Boolean): Pair<Int, String>? {
    val summary = context?.summary?.text?.takeIf { it.isNotBlank() }
    val preview = context?.preview?.text?.takeIf { it.isNotBlank() }
    return when {
        isCompleted -> summary?.let { R.string.match_summary_title to it }
        else -> preview?.let { R.string.match_preview_title to it }
    }
}

internal fun LazyListScope.overviewMatchSurface(surface: Pair<Int, String>?) {
    if (surface == null) return
    item(key = "surface_header") { SectionHeaderV2(title = stringResource(surface.first)) }
    item(key = "surface_card") {
        BouncerCard {
            Text(
                text = surface.second,
                style = BouncerThemeV2.typography.bodyMedium,
                color = BouncerThemeV2.colors.onSurface,
                modifier = Modifier.padding(BouncerThemeV2.spacing.md),
            )
        }
    }
}
