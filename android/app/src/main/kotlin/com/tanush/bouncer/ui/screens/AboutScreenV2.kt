package com.tanush.bouncer.ui.screens

import com.tanush.bouncer.ui.components.v2.AppMark
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.open
import com.tanush.bouncer.utils.openStoreListing
import com.tanush.bouncer.utils.shareText
import com.tanush.bouncer.utils.storeListingUrl
import androidx.compose.ui.platform.LocalContext

/**
 * AboutScreenV2 — App info, version, links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenV2(onBackClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.common_about), style = typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )
        },
        containerColor = colors.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon + name + version
            item(key = "header") {
                Spacer(Modifier.height(spacing.xxl))
                AppMark(size = 80.dp)
                Spacer(Modifier.height(spacing.md))
                // The wordmark is the one place gold is not an affordance.
                Text(
                    text = "Bouncer",
                    style = typography.headlineLarge,
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = typography.numericSmall,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(spacing.xxl))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(spacing.lg))
            }

            // Every row here goes somewhere. There were four — Changelog, Open Source Licenses,
            // Privacy Policy, Terms of Service — each with a chevron and an empty onClick. The two
            // without anything to open stay out until they have it.
            item(key = "changelog") {
                SettingsItemV2(
                    icon = Icons.Default.NewReleases,
                    title = stringResource(R.string.about_changelog),
                    subtitle = stringResource(R.string.about_changelog_on_play),
                    trailing = SettingsTrailing.Navigation,
                    onClick = { context.openStoreListing() },
                )
            }
            // No "Send feedback" row: the app carries no support address to mail, and one is not invented.
            item(key = "rate") {
                SettingsItemV2(
                    icon = Icons.Default.Star,
                    title = stringResource(R.string.about_rate),
                    subtitle = stringResource(R.string.about_changelog_on_play),
                    trailing = SettingsTrailing.Navigation,
                    onClick = { context.openStoreListing() },
                )
            }
            item(key = "share") {
                val shareMessage = stringResource(R.string.about_share_message, context.storeListingUrl())
                val shareTitle = stringResource(R.string.common_share)
                SettingsItemV2(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.about_share),
                    trailing = SettingsTrailing.Navigation,
                    onClick = { context.shareText(text = shareMessage, chooserTitle = shareTitle) },
                )
            }
            item(key = "privacy") {
                SettingsItemV2(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.about_privacy_policy),
                    trailing = SettingsTrailing.Navigation,
                    showDivider = false,
                    onClick = { context.open(PRIVACY_POLICY_URL) },
                )
            }

            // Footer
            item(key = "footer") {
                Spacer(Modifier.height(spacing.xxxl))
                Text(
                    text = stringResource(R.string.about_made_with_by_tanush),
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// The address declared in Play Console (Policy › App content). Change both together.
private const val PRIVACY_POLICY_URL = "https://gist.github.com/tm-droid/e2fc757ea447c4d6970e69112a6f7b56"
