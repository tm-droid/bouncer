package com.tanush.bouncer.ui.screens

import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.BuildConfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * MoreScreenV2 — Settings list and app version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreenV2(
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDevToolsClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Scaffold(
        topBar = { BouncerTopBarV2(title = "Bouncer") },
        containerColor = colors.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = spacing.xxl),
        ) {
            item(key = "settings_header") {
                SectionHeaderV2(
                    title = stringResource(R.string.common_settings),
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
            item(key = "settings_list") {
                SettingsList(
                    onSettingsClick = onSettingsClick,
                    onAppearanceClick = onAppearanceClick,
                    onAboutClick = onAboutClick,
                    onDevToolsClick = onDevToolsClick,
                )
            }

            item(key = "version") {
                Spacer(Modifier.height(spacing.xl))
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SettingsList(
    onSettingsClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDevToolsClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.lg)) {
        SettingsItemV2(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.common_settings),
            subtitle = stringResource(R.string.more_preferences_and_notifications),
            trailing = SettingsTrailing.Navigation,
            onClick = onSettingsClick,
        )
        SettingsItemV2(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.common_appearance),
            subtitle = stringResource(R.string.more_theme_and_display),
            trailing = SettingsTrailing.Navigation,
            onClick = onAppearanceClick,
        )
        SettingsItemV2(
            icon = Icons.Default.Info,
            title = stringResource(R.string.common_about),
            subtitle = stringResource(R.string.more_version_licenses_and_credits),
            trailing = SettingsTrailing.Navigation,
            onClick = onAboutClick,
        )
        // Debug builds only. This row shipped in release, and the screen behind it
        // led with an "Echo" button wired to a placeholder MCP client.
        if (BuildConfig.DEBUG) {
            SettingsItemV2(
                icon = Icons.Default.Build,
                title = stringResource(R.string.more_dev_tools),
                subtitle = stringResource(R.string.more_diagnostics_and_debug),
                trailing = SettingsTrailing.Navigation,
                onClick = onDevToolsClick,
            )
        }
    }
}
