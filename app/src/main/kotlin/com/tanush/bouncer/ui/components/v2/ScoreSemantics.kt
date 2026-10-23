package com.tanush.bouncer.ui.components.v2

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.tanush.bouncer.R

private const val ALL_OUT_WICKETS = 10

/** A string resource and its arguments, built without a Context so it can be unit-tested. */
class ScoreSentenceSpec(@param:StringRes val res: Int, val args: List<String>) {
    override fun equals(other: Any?) = other is ScoreSentenceSpec && res == other.res && args == other.args
    override fun hashCode() = 31 * res + args.hashCode()
}

/**
 * Which sentence to speak for one innings: "India 187 for 4, 32.1 overs", "India 240 all out, 49.2
 * overs" or "India yet to bat". [runs] null means the side has not batted. Overs are optional
 * because a just-started or abandoned innings may not have a figure.
 */
fun scoreSentenceSpec(team: String, runs: Int?, wickets: Int?, overs: String?): ScoreSentenceSpec {
    if (runs == null) return ScoreSentenceSpec(R.string.a11y_yet_to_bat, listOf(team))
    val hasOvers = !overs.isNullOrBlank()
    val w = wickets ?: 0
    val args = listOfNotNull(team, runs.toString(), if (w >= ALL_OUT_WICKETS) null else w.toString(), overs?.takeIf { hasOvers })
    val res = when {
        w >= ALL_OUT_WICKETS -> if (hasOvers) R.string.a11y_score_all_out else R.string.a11y_score_all_out_no_overs
        hasOvers -> R.string.a11y_score_for
        else -> R.string.a11y_score_for_no_overs
    }
    return ScoreSentenceSpec(res, args)
}

@Composable
fun scoreSentence(team: String, runs: Int?, wickets: Int?, overs: String?): String {
    val spec = scoreSentenceSpec(team, runs, wickets, overs)
    return stringResource(spec.res, *spec.args.toTypedArray())
}

/**
 * Wrap a score in this once: TalkBack reads [sentence] a single time, politely, when it changes.
 * The digits inside should be `AnimatedScoreText(describedByParent = true)` so they add nothing.
 */
fun Modifier.scoreSemantics(sentence: String): Modifier = semantics {
    liveRegion = LiveRegionMode.Polite
    contentDescription = sentence
}
