package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Branded top app bar: brand-coloured bar, "BOUNCER" wordmark or a screen title, leading edge.
 *
 * Left-aligned, not centred. `CenterAlignedTopAppBar` centres the title in whatever width the
 * actions leave over, so with three trailing icons and no leading one the wordmark sat visibly
 * off-centre — near-centred but not, which reads as a mistake rather than a choice. Cricbuzz and
 * Cricinfo both anchor their wordmark to the leading edge; so does every Material default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncerTopBarV2(
    title: String = "BOUNCER",
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = BouncerThemeV2.colors

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
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
}
