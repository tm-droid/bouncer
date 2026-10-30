package com.tanush.bouncer.models

import kotlinx.serialization.Serializable

/**
 * How far one group of context terms moved the win probability, as `bouncer-match-sync` publishes
 * it: [k] is `teams`, `ground` or `elevens`, and [d] is the probability as scored minus the
 * probability with that group neutral, on 0..1. Positive favours the side the probability is for.
 *
 * It describes the model, not the match: "elevens +0.06" means the number would be six points
 * lower had the team sheets been unknown. A key this build does not know is skipped, not shown.
 */
@Serializable
data class WinProbFactor(
    val k: String = "",
    val d: Double = 0.0,
)
