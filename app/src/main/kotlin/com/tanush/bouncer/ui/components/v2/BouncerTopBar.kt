package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Top app bar: the page's own colour, a title on the leading edge, and a hairline beneath.
 *
 * Left-aligned, not centred. `CenterAlignedTopAppBar` centres the title in whatever width the
 * actions leave over, so with three trailing icons and no leading one the wordmark sat visibly
 * off-centre — near-centred but not, which reads as a mistake rather than a choice. Cricbuzz and
 * Cricinfo both anchor their wordmark to the leading edge; so does every Material default.
 *
 * The bar used to be its own tinted slab (mustard in light mode, a lifted charcoal in dark). It is now
 * the background colour, separated by [showDivider]'s hairline, so the only brand mark is the gold
 * title ink. A screen that draws its own rule directly under the bar — Home's tab row — turns it off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncerTopBarV2(
    title: String = "Bouncer",
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    showDivider: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = BouncerThemeV2.colors

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                )
            },
            navigationIcon = {
                if (showBack && onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.topBar,
                titleContentColor = colors.onTopBar,
                navigationIconContentColor = colors.onTopBarMuted,
                actionIconContentColor = colors.onTopBarMuted,
            ),
        )
        if (showDivider) {
            HorizontalDivider(color = colors.divider)
        }
    }
}
