package com.tanush.bouncer.demo

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.HomeScreenV2
import com.tanush.bouncer.ui.MatchDetailViewV2
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.ThemeModeV2
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.MatchDetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug-only thin host for the showcase video. Renders the real production
 * screens (HomeScreenV2, MatchDetailViewV2) driven by the real ViewModels,
 * with all data coming from [ShowcaseScript] via [FakeMatchRepository] and
 * [FakeLiveDetailSource] — no UI is reimplemented here.
 *
 * Sequence: home (live cards, score ticking) → auto-opens match detail after
 * [HOME_DWELL_MS] with the production overlay transition. The AI tab switch is
 * driven externally (adb tap) because the detail pager is screen-internal.
 *
 * Launch: adb shell am start -n com.tanush.bouncer/.demo.ShowcaseActivity
 * Add --ez pinWidget true to request pinning the Glance widget (seeded data).
 */
internal class ShowcaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startMs = SystemClock.elapsedRealtime()
        val elapsed = { SystemClock.elapsedRealtime() - startMs }
        val fakeRepo = FakeMatchRepository(elapsed)
        val fakeLive = FakeLiveDetailSource(elapsed)

        val liveVm = ViewModelProvider(this, factoryFor { LiveActivityViewModel(fakeRepo) })[LiveActivityViewModel::class.java]

        // Pre-seed the detail ViewModel under the same key MatchDetailViewV2 uses
        // (match.id), so its internal viewModel() lookup returns this fake-backed
        // instance and the production factory never runs.
        ViewModelProvider(
            this,
            factoryFor { MatchDetailViewModel(ShowcaseScript.matchAt(0L), fakeRepo, fakeLive) },
        ).get(ShowcaseScript.MATCH_ID, MatchDetailViewModel::class.java)

        lifecycleScope.launch { WidgetDemoSeeder.seed(applicationContext) }
        if (intent.getBooleanExtra("pinWidget", false)) WidgetDemoSeeder.requestPin(this)

        setContent {
            BouncerThemeV2(themeMode = ThemeModeV2.Dark) {
                ShowcaseSequence(liveVm)
            }
        }
    }

    private inline fun <reified T : ViewModel> factoryFor(crossinline create: () -> T) =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
        }

    private companion object {
        const val HOME_DWELL_MS = 6_500L
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @androidx.compose.runtime.Composable
    private fun ShowcaseSequence(liveVm: LiveActivityViewModel) {
        var currentMatch by remember { mutableStateOf<CricketMatch?>(null) }

        LaunchedEffect(Unit) {
            delay(HOME_DWELL_MS)
            currentMatch = liveVm.currentMatches.value.firstOrNull { it.isLive }
                ?: ShowcaseScript.matchAt(0L)
        }

        // Mirrors BouncerAppContent's root overlay pattern so the detail screen
        // opens with the same production transition.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BouncerThemeV2.colors.background)
        ) {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = currentMatch,
                    transitionSpec = {
                        if (targetState != null) {
                            BouncerMotion.Transitions.verticalEnter(offsetDivisor = 10)
                                .togetherWith(fadeOut(BouncerMotion.Specs.FadeOut))
                        } else {
                            fadeIn(BouncerMotion.Specs.FadeIn) togetherWith
                                BouncerMotion.Transitions.verticalExit(offsetDivisor = 10)
                        }
                    },
                    label = "showcaseNavigation",
                ) { match ->
                    if (match != null) {
                        Box(
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "match_container_${match.id}"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                )
                                .graphicsLayer { clip = true }
                        ) {
                            MatchDetailViewV2(
                                match = match,
                                onBackClick = { currentMatch = null },
                            )
                        }
                    } else {
                        HomeScreenV2(
                            viewModel = liveVm,
                            onMatchClick = { currentMatch = it },
                        )
                    }
                }
            }
        }
    }
}
