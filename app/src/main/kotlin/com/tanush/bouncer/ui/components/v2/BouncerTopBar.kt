package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Branded top app bar.
 * - Primary tabs: orange bg (light), dark bg (dark), "BOUNCER" wordmark.
 * - Detail screens: back arrow, centered title.
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

    CenterAlignedTopAppBar(
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
                        contentDescription = "Back",
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = colors.topBar,
            titleContentColor = colors.onTopBar,
            navigationIconContentColor = colors.onTopBar,
            actionIconContentColor = colors.onTopBar,
        ),
    )
}
