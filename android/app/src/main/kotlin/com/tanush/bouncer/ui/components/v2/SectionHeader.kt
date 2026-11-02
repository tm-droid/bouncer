package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * Section title with optional "See All" button.
 *
 * Sentence case, in the reader's own ink. It was an 11sp wide-tracked uppercase overline in grey, and
 * the tiny all-caps label above every block is one of the most reliable signs of a generated layout —
 * it also made each section read like a form caption. Weight separates the title from the rows under
 * it; the action keeps gold, because gold's one job in this app is "you can tap this".
 */
@Composable
fun SectionHeaderV2(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (action != null && onAction != null) {
            // The text alone is about 20dp tall; the box gives it a 48dp target.
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = action, style = typography.labelLarge, color = colors.primaryText)
            }
        }
    }
}
