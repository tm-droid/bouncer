package com.tanush.bouncer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tanush.bouncer.ui.theme.BackgroundSkinType
import com.tanush.bouncer.ui.theme.ThemeVariant
import com.tanush.bouncer.utils.PreferencesHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LandingTab {
    HOME,
    ALL_MATCHES,
    FAVOURITES
}

class PersonalizationViewModel : ViewModel() {
    private val _themeVariant = MutableStateFlow(ThemeVariant.Dark)
    val themeVariant: StateFlow<ThemeVariant> = _themeVariant

    private val _backgroundSkin = MutableStateFlow(BackgroundSkinType.MIDNIGHT_GRADIENT)
    val backgroundSkin: StateFlow<BackgroundSkinType> = _backgroundSkin

    private val _defaultLanding = MutableStateFlow(LandingTab.HOME)
    val defaultLanding: StateFlow<LandingTab> = _defaultLanding

    fun load(context: Context) {
        // Read persisted preferences and populate state flows
        val tv = runCatching {
            ThemeVariant.valueOf(
                PreferencesHelper.getString(context, PreferencesHelper.KEY_THEME_VARIANT, ThemeVariant.Dark.name)
            )
        }.getOrDefault(ThemeVariant.Dark)
        _themeVariant.value = tv

        val bs = runCatching {
            BackgroundSkinType.valueOf(
                PreferencesHelper.getString(context, PreferencesHelper.KEY_BACKGROUND_SKIN, BackgroundSkinType.MIDNIGHT_GRADIENT.name)
            )
        }.getOrDefault(BackgroundSkinType.MIDNIGHT_GRADIENT)
        _backgroundSkin.value = bs

        val dl = runCatching {
            when (PreferencesHelper.getString(context, PreferencesHelper.KEY_DEFAULT_LANDING, LandingTab.HOME.name)) {
                LandingTab.ALL_MATCHES.name -> LandingTab.ALL_MATCHES
                LandingTab.FAVOURITES.name -> LandingTab.FAVOURITES
                else -> LandingTab.HOME
            }
        }.getOrDefault(LandingTab.HOME)
        _defaultLanding.value = dl
    }

    fun setThemeVariant(context: Context, variant: ThemeVariant) {
        _themeVariant.value = variant
        PreferencesHelper.setString(context, PreferencesHelper.KEY_THEME_VARIANT, variant.name)
    }

    fun setBackgroundSkin(context: Context, skin: BackgroundSkinType) {
        _backgroundSkin.value = skin
        PreferencesHelper.setString(context, PreferencesHelper.KEY_BACKGROUND_SKIN, skin.name)
    }

    fun setDefaultLanding(context: Context, landingTab: LandingTab) {
        _defaultLanding.value = landingTab
        PreferencesHelper.setString(context, PreferencesHelper.KEY_DEFAULT_LANDING, landingTab.name)
    }
}
