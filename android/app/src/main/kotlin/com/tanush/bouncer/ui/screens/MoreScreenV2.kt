package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.BuildConfig
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.components.v2.BouncerRowGroup
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.TipJarSheet
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.ui.screens.settings.DataSettingsGroup
import com.tanush.bouncer.ui.screens.settings.NotificationSettingsGroup
import com.tanush.bouncer.ui.screens.settings.SettingsColumn
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.ThemeModeV2

/**
 * The More tab is the settings, not a menu of three rows leading to them. It used to list
 * Settings, Appearance and About, while Settings listed Appearance and About again and hid the
 * only way into Favourites at the bottom of "Data & Behavior". Now: your teams (each opening its
 * page), notifications, the theme, how the app behaves, and about.
 */
@Composable
fun MoreScreenV2(
    themeMode: ThemeModeV2,
    onThemeModeChanged: (ThemeModeV2) -> Unit,
    onTeamClick: (String) -> Unit,
    onEditFavourites: () -> Unit,
    onAboutClick: () -> Unit,
    onRedoSetup: () -> Unit,
    onDevToolsClick: () -> Unit,
) {
    val favourites by FavouriteTeamsManager.favourites.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BouncerTopBarV2(title = stringResource(R.string.app_name)) },
        containerColor = BouncerThemeV2.colors.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = BouncerThemeV2.spacing.xxl),
        ) {
            yourTeamsItems(favourites, onTeamClick, onEditFavourites)
            moreSection("notifications", R.string.more_notifications) { NotificationSettingsGroup() }
            moreSection("appearance", R.string.common_appearance) { ThemePicker(themeMode, onThemeModeChanged) }
            moreSection("data", R.string.more_data_and_behaviour) { DataSettingsGroup() }
            moreSection("about", R.string.common_about) { AboutRows(onAboutClick, onRedoSetup, onDevToolsClick) }
            item(key = "version") {
                Text(
                    text = stringResource(R.string.more_version, BuildConfig.VERSION_NAME),
                    style = BouncerThemeV2.typography.bodySmall,
                    color = BouncerThemeV2.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = BouncerThemeV2.spacing.xl),
                )
            }
        }
    }
}

private fun LazyListScope.moreSection(key: String, title: Int, content: @Composable () -> Unit) {
    item(key = "${key}_header") {
        SectionHeaderV2(
            title = stringResource(title),
            modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.lg, vertical = BouncerThemeV2.spacing.sm),
        )
    }
    item(key = key) { SettingsColumn(content) }
}

/** The favourite sides, each opening its team page; Edit reorders, adds and removes. */
private fun LazyListScope.yourTeamsItems(favourites: List<String>, onTeamClick: (String) -> Unit, onEdit: () -> Unit) {
    item(key = "teams_header") {
        SectionHeaderV2(
            title = stringResource(R.string.more_your_teams),
            modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.lg, vertical = BouncerThemeV2.spacing.sm),
            action = stringResource(R.string.more_edit).takeIf { favourites.isNotEmpty() },
            onAction = onEdit.takeIf { favourites.isNotEmpty() },
        )
    }
    item(key = "teams") {
        SettingsColumn {
            if (favourites.isEmpty()) {
                SettingsItemV2(
                    icon = Icons.Default.Favorite,
                    title = stringResource(R.string.more_add_favourites),
                    trailing = SettingsTrailing.Navigation,
                    onClick = onEdit,
                )
            } else {
                BouncerRowGroup(favourites) { team -> FavouriteTeamRow(team) { onTeamClick(team) } }
            }
        }
    }
}

@Composable
private fun FavouriteTeamRow(team: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.cd_open_team, team), onClick = onClick)
            .padding(horizontal = BouncerThemeV2.spacing.md, vertical = BouncerThemeV2.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(teamName = team, logoUrl = TeamLogoManager.getLogoUrl(team), size = 28.dp)
        Spacer(Modifier.width(BouncerThemeV2.spacing.md))
        Text(team, style = BouncerThemeV2.typography.bodyLarge, color = rememberTeamInk(team), maxLines = 1)
    }
}

/** Light, dark or the system's, picked here. It was a screen of its own with three cards on it. */
@Composable
private fun ThemePicker(mode: ThemeModeV2, onChange: (ThemeModeV2) -> Unit) {
    val modes = ThemeModeV2.entries
    TextTabBarV2(
        tabs = modes.map { stringResource(it.label()) },
        selectedIndex = modes.indexOf(mode),
        onSelect = { onChange(modes[it]) },
    )
    Spacer(Modifier.height(BouncerThemeV2.spacing.sm))
}

private fun ThemeModeV2.label(): Int = when (this) {
    ThemeModeV2.Light -> R.string.appearance_light
    ThemeModeV2.Dark -> R.string.appearance_dark
    ThemeModeV2.System -> R.string.appearance_system
}

@Composable
private fun AboutRows(onAboutClick: () -> Unit, onRedoSetup: () -> Unit, onDevToolsClick: () -> Unit) {
    var confirmingRedo by remember { mutableStateOf(false) }
    if (confirmingRedo) {
        AlertDialog(
            onDismissRequest = { confirmingRedo = false },
            title = { Text(stringResource(R.string.settings_redo_setup)) },
            text = { Text(stringResource(R.string.settings_redo_setup_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmingRedo = false; onRedoSetup() }) {
                    Text(stringResource(R.string.settings_redo_setup_action))
                }
            },
            dismissButton = { TextButton(onClick = { confirmingRedo = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    var showTips by remember { mutableStateOf(false) }
    if (showTips) TipJarSheet(onDismiss = { showTips = false })
    SettingsItemV2(
        icon = Icons.Default.Favorite,
        title = stringResource(R.string.tip_jar_row_title),
        subtitle = stringResource(R.string.tip_jar_row_subtitle),
        trailing = SettingsTrailing.Navigation,
        onClick = { showTips = true },
    )
    SettingsItemV2(
        icon = Icons.Default.Info,
        title = stringResource(R.string.settings_about_bouncer),
        subtitle = stringResource(R.string.more_version_licenses_and_credits),
        trailing = SettingsTrailing.Navigation,
        onClick = onAboutClick,
    )
    SettingsItemV2(
        icon = Icons.Default.Refresh,
        title = stringResource(R.string.settings_redo_setup),
        subtitle = stringResource(R.string.settings_re_run_the_onboarding_flow_to),
        trailing = SettingsTrailing.Navigation,
        onClick = { confirmingRedo = true },
    )
    // Debug builds only. This row shipped in release, and the screen behind it led with an "Echo"
    // button wired to a placeholder MCP client.
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
