package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BackgroundBase
import com.tanush.bouncer.ui.theme.GlassSurface
import com.tanush.bouncer.ui.theme.GoldHairline
import com.tanush.bouncer.ui.theme.LiveRed
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import com.tanush.bouncer.ui.theme.TextMuted
import com.tanush.bouncer.ui.theme.TextPrimary
import com.tanush.bouncer.ui.theme.TextSecondary
import com.tanush.bouncer.ui.theme.liftForContrast
import com.tanush.bouncer.ui.theme.lighten
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import com.tanush.bouncer.ui.theme.rememberTeamGround

/** What the number is a statement about, which changes both the label and whether it can move. */
enum class WinProbabilityState { LIVE, PRE_MATCH, FINAL }

/**
 * What the number is a statement about.
 *
 * The server's own pre-match flag outranks the match status: a fixture can still read Upcoming from
 * a stale list snapshot while the server is already scoring it live, and the label has to describe
 * where the number came from rather than what the list believed.
 */
fun winProbabilityStateFor(
    isCompleted: Boolean,
    isPrematch: Boolean,
    isUpcoming: Boolean,
): WinProbabilityState = when {
    isCompleted -> WinProbabilityState.FINAL
    isPrematch || isUpcoming -> WinProbabilityState.PRE_MATCH
    else -> WinProbabilityState.LIVE
}

private const val MIN_VISIBLE_SHARE = 0.06f
private const val DELTA_WORTH_SHOWING = 0.02f

/**
 * Contrast floor for a team's colour against the card it is drawn on.
 *
 * `TeamColorRegistry` already guarantees 3:1 against the app background, which is the right target
 * for text on the page but not for a filled segment inside a lifted glass card: Bangladesh's
 * #006A4E clears that bar at 3.17:1 and still rendered as an unfilled-looking void next to a
 * mid-tone card fill. This is measured against the card's own composited fill instead, and the
 * floor is lower because a 14dp block of solid colour needs far less separation than a glyph.
 */
private const val SEGMENT_MIN_CONTRAST = 2.2f
private const val SEGMENT_SHADE = 0.18f

/** The card's fill as it actually reaches the eye — [GlassSurface] is translucent. */
private val CardFill: Color
    @Composable @ReadOnlyComposable
    get() = GlassSurface.compositeOver(BackgroundBase)

@Composable
@ReadOnlyComposable
private fun onCard(color: Color): Color = color.liftForContrast(CardFill, SEGMENT_MIN_CONTRAST)

/**
 * Win probability as a divided bar rather than a single gauge.
 *
 * The gauge this replaces showed one arc, one percentage and one team's acronym, so reading it
 * required knowing which side the number belonged to and doing the subtraction for the other.
 * Worse, it could not express a Test at all: a semicircle has two ends and a Test has three
 * outcomes, and the draw — often the likeliest result — had nowhere to go.
 *
 * Both sides are named and both numbers are stated. [drawProbability] adds a third, neutral segment
 * and is null for every format that cannot be drawn, which is not the same as a draw of zero.
 *
 * @param team1Probability the first team's share of the whole, 0..1. With a draw present the three
 *   shares sum to one, so the second team's share is the remainder rather than the complement.
 */
@Composable
@Suppress("LongParameterList") // Each is a distinct thing the bar states; bundling them into a
// holder would move the argument list rather than shorten it.
fun PremiumWinProbabilityHero(
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    modifier: Modifier = Modifier,
    drawProbability: Float? = null,
    state: WinProbabilityState = WinProbabilityState.LIVE,
    detail: String? = null,
    delta: Float? = null,
) {
    val draw = (drawProbability ?: 0f).coerceIn(0f, 1f)
    val team1 = team1Probability.coerceIn(0f, 1f - draw)
    val team2 = (1f - draw - team1).coerceAtLeast(0f)

    val animatedTeam1 by animateFloatAsState(team1, tween(900, easing = FastOutSlowInEasing), label = "team1")
    val animatedDraw by animateFloatAsState(draw, tween(900, easing = FastOutSlowInEasing), label = "draw")

    val ground = rememberTeamGround()
    val team1Color = onCard(TeamColorRegistry.forTeam(team1Name, ground).primary)
    val team2Color = onCard(TeamColorRegistry.forTeam(team2Name, ground).primary)
    val percents = wholePercents(team1, draw, team2)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassSurface)
            .border(0.5.dp, GoldHairline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(20.dp)
            .semantics {
                contentDescription = describe(team1Name, team2Name, percents, drawProbability != null, state)
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WIN PROBABILITY",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            StateChip(state)
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            SideLabel(team1Name, percents[0], team1Color, TextAlign.Start, Modifier.weight(1f))
            if (drawProbability != null) {
                SideLabel("DRAW", percents[1], TextSecondary, TextAlign.Center, Modifier.weight(1f))
            }
            SideLabel(team2Name, percents[2], team2Color, TextAlign.End, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        SegmentedBar(
            team1Share = animatedTeam1,
            drawShare = animatedDraw,
            team1Color = team1Color,
            team2Color = team2Color,
            hasDraw = drawProbability != null,
        )

        val footer = footerText(detail, delta, team1Name, team2Name)
        if (footer != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = footer, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SideLabel(
    name: String,
    percent: Int,
    color: Color,
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = name.uppercase(),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "$percent%",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The bar itself.
 *
 * Every non-zero share is drawn at least [MIN_VISIBLE_SHARE] wide. A 3% chance that renders as a
 * hairline reads as zero, and "nearly impossible" and "impossible" are the two states this component
 * most needs to keep apart.
 */
@Composable
private fun SegmentedBar(
    team1Share: Float,
    drawShare: Float,
    team1Color: Color,
    team2Color: Color,
    hasDraw: Boolean,
) {
    val team1 = visible(team1Share)
    val draw = if (hasDraw) visible(drawShare) else 0f
    val team2 = visible(1f - team1Share - drawShare)
    val total = (team1 + draw + team2).coerceAtLeast(0.0001f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
    ) {
        // Every segment is guarded, not just the draw. `Modifier.weight(0f)` throws, and [visible]
        // returns exactly zero for an impossible outcome — which is the normal state of a finished
        // match, where the losing side is 0. That crashed the Overview tab on any completed match
        // (ANDROID-3N); the draw was guarded and the two teams were not.
        if (team1 > 0f) {
            Box(
                Modifier
                    .weight(team1 / total)
                    .fillMaxWidth()
                    .height(14.dp)
                    // Shaded by lightening, not by alpha: alpha over a dark card drags the segment
                    // back below the contrast floor onCard() just lifted it above.
                    .background(
                        Brush.horizontalGradient(listOf(team1Color.lighten(SEGMENT_SHADE), team1Color)),
                    ),
            )
        }
        if (draw > 0f) {
            Box(
                Modifier
                    .weight(draw / total)
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(TextMuted.copy(alpha = 0.55f)),
            )
        }
        if (team2 > 0f) {
            Box(
                Modifier
                    .weight(team2 / total)
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(
                        Brush.horizontalGradient(listOf(team2Color, team2Color.lighten(SEGMENT_SHADE))),
                    ),
            )
        }
    }
}

private fun visible(share: Float): Float =
    if (share <= 0.0005f) 0f else share.coerceAtLeast(MIN_VISIBLE_SHARE)

@Composable
private fun StateChip(state: WinProbabilityState) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val (label, color) = when (state) {
        WinProbabilityState.LIVE -> "LIVE" to LiveRed
        // Labelled distinctly on purpose: a pre-match number is a prediction from form alone, and
        // showing it in the same clothes as a number computed from a live position would overstate
        // it.
        WinProbabilityState.PRE_MATCH -> "PRE-MATCH" to TextSecondary
        WinProbabilityState.FINAL -> "FINAL" to TextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state == WinProbabilityState.LIVE) {
            Box(Modifier.size(6.dp).background(color.copy(alpha = pulse), CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
    }
}

/**
 * The line under the bar: the supporting numbers, and the swing since the last reading.
 *
 * A swing below [DELTA_WORTH_SHOWING] is not reported. The model is refreshed once a minute and
 * moves a point or two on nothing in particular; an arrow on that noise would read as a momentum
 * call the model is not making.
 */
private fun footerText(detail: String?, delta: Float?, team1Name: String, team2Name: String): String? {
    val swing = delta?.takeIf { abs(it) >= DELTA_WORTH_SHOWING }?.let {
        val gained = if (it > 0) team1Name else team2Name
        "${gained.uppercase()} +${(abs(it) * 100).roundToInt()}%"
    }
    return listOfNotNull(detail?.takeIf { it.isNotBlank() }, swing).joinToString("  ·  ").ifBlank { null }
}

private fun describe(
    team1Name: String,
    team2Name: String,
    percents: List<Int>,
    hasDraw: Boolean,
    state: WinProbabilityState,
): String {
    val prefix = when (state) {
        WinProbabilityState.LIVE -> "Live win probability"
        WinProbabilityState.PRE_MATCH -> "Pre-match win probability"
        WinProbabilityState.FINAL -> "Final result"
    }
    val drawPart = if (hasDraw) ", draw ${percents[1]} percent" else ""
    return "$prefix: $team1Name ${percents[0]} percent, $team2Name ${percents[2]} percent$drawPart"
}

/**
 * Whole percentages that sum to exactly 100.
 *
 * Rounding each share independently lands on 99 or 101 often enough to matter — 32 + 17 + 50 was on
 * screen — and three numbers that visibly fail to add up read as a broken model rather than as a
 * rounding artefact. Largest remainder: floor every share, then give the leftover points to the
 * shares that lost the most in the flooring.
 */
internal fun wholePercents(vararg shares: Float): List<Int> {
    val scaled = shares.map { (it * 100f).coerceAtLeast(0f) }
    val whole = scaled.map { floor(it).toInt() }.toMutableList()
    var leftover = 100 - whole.sum()
    val byRemainder = scaled.indices.sortedByDescending { scaled[it] - whole[it] }
    var next = 0
    while (leftover > 0) {
        whole[byRemainder[next % byRemainder.size]]++
        leftover--
        next++
    }
    return whole
}
