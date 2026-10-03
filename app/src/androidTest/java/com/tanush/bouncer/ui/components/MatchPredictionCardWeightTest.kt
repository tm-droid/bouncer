package com.tanush.bouncer.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tanush.bouncer.ui.theme.BouncerPreviewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the impact bar in `PredictionFactorRow` against the three impacts that used to crash it.
 *
 * The bar was two `Modifier.weight` children splitting `impact` against `1f - impact`, and Compose
 * requires every weight to be strictly positive. The producer at the time (a run-rate factor, since
 * removed) computed `((crr / rrr) - 1f).coerceIn(-1f, 1f)`, so `impact` reached 0f whenever CRR equals RRR, +1f
 * whenever CRR is at least twice RRR, and -1f when CRR is zero — each leaving one side of the split
 * at zero and throwing `IllegalArgumentException: invalid weight` while laying out the Overview tab
 * (ANDROID-4K).
 *
 * This cannot be a unit test: the rule it violates is enforced by Compose's layout pass, so the
 * card has to actually compose. Same reasoning as LiveUpdatePromotabilityTest.
 */
@RunWith(AndroidJUnit4::class)
class MatchPredictionCardWeightTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun infoWith(vararg impacts: Float) = MatchPredictionInfo(
        team1Name = "India",
        team2Name = "Australia",
        team1WinProbability = 0.5f,
        team2WinProbability = 0.5f,
        predictionFactors = impacts.mapIndexed { i, impact ->
            PredictionFactor("Factor $i", impact, "impact $impact")
        },
    )

    private fun assertComposes(vararg impacts: Float) {
        composeRule.setContent {
            BouncerPreviewTheme { MatchPredictionCard(predictionInfo = infoWith(*impacts)) }
        }
        composeRule.onNodeWithText("Factor 0").assertIsDisplayed()
    }

    @Test
    fun a_flat_factor_lays_out() = assertComposes(0f)

    @Test
    fun a_fully_positive_factor_lays_out() = assertComposes(1f)

    @Test
    fun a_fully_negative_factor_lays_out() = assertComposes(-1f)

    @Test
    fun the_whole_produced_range_lays_out_together() =
        assertComposes(-1f, -0.5f, 0f, 0.5f, 1f)
}
