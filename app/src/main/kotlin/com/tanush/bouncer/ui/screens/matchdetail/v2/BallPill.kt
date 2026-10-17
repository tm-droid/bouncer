package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.v2.ballChipInk
import com.tanush.bouncer.ui.theme.v2.BouncerColorsV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.CricketEventColors

// ── The commentary feed's per-delivery result pill ───────────────────────────
//
// Split from CommentaryTabV2.kt, which owns the feed itself. The pill is where the outcome
// columns surface, so it lives beside nothing else and can be read against BallOutcome.kt.

/**
 * The delivery's outcome, or null when nobody knows what it was.
 *
 * Null is reached only when the `runs` column is absent — a row written before it existed, or a
 * scrape fallback — and the raw clause was not there to fall back on. It draws no pill rather than
 * a `·`, because a dot ball is a claim, and the claim was wrong: every single used to land here.
 */
private fun resultPillOf(ball: ParsedBall, colors: BouncerColorsV2): Triple<String, Color, Color>? =
    eventPillOf(ball, colors.cricket) ?: plainPillOf(ball, colors)

/** The three outcomes the server classifies outright, each in its own event colour. */
private fun eventPillOf(ball: ParsedBall, cricket: CricketEventColors): Triple<String, Color, Color>? =
    when {
        // Solid, with whichever of black and white reads on the fill — the over strip's treatment.
        // Event ink on a 20% tint of itself was 3.4–4.2:1, and the three that matter most read
        // weakest in the feed.
        ball.isWicket -> Triple("W", ballChipInk(cricket.wicket), cricket.wicket)
        ball.isSix -> Triple("6", ballChipInk(cricket.six), cricket.six)
        ball.isFour -> Triple("4", ballChipInk(cricket.four), cricket.four)
        else -> null
    }

private fun plainPillOf(ball: ParsedBall, colors: BouncerColorsV2): Triple<String, Color, Color>? {
    val cricket = colors.cricket
    return when {
        // Cricbuzz's own notation, and worth the extra character: a `Wd3` and a `Wd` are different
        // deliveries, and flattening both to `WD` hid the difference the runs column exists to keep.
        ball.extra != null && ball.runs != null -> extraPill(ball, cricket)
        ball.isWide -> Triple("Wd", cricket.wide, cricket.wide.copy(alpha = 0.15f))
        ball.isNoBall -> Triple("Nb", cricket.noBall, cricket.noBall.copy(alpha = 0.15f))
        // textSecondary keeps dot/run balls visually quieter than boundaries
        // while staying above the 4.5:1 AA contrast floor (raw alpha dips failed it).
        ball.runs == 0 -> Triple("·", colors.textSecondary, colors.surfaceVariant)
        ball.runs != null -> Triple(ball.runs.toString(), colors.onSurface, colors.surfaceVariant)
        else -> null
    }
}

/**
 * An extra's pill: Cricbuzz's glyph, and the colour of who it is charged to.
 *
 * Wides and no-balls are the bowler's and keep the two event colours they always had. Byes and leg
 * byes are not, so they take the wide family's colour but never the no-ball one — the chip should
 * not read as a delivery fault when the bowler did nothing wrong.
 */
private fun extraPill(ball: ParsedBall, cricket: CricketEventColors): Triple<String, Color, Color> {
    val colour = if (ball.extra == BallExtra.NOBALL) cricket.noBall else cricket.wide
    return Triple(ball.toBallString().orEmpty(), colour, colour.copy(alpha = 0.15f))
}

@Composable
internal fun ResultPill(ball: ParsedBall) {
    val colors = BouncerThemeV2.colors
    val outcome = resultPillOf(ball, colors)

    if (outcome == null) {
        // Width still reserved: the ball label and text columns line up down the whole feed, and a
        // feed that reflows around the unknown rows reads as a rendering fault.
        Spacer(Modifier.size(24.dp))
        return
    }
    val (text, fg, bg) = outcome

    // A minimum, not a fixed size: at a large font scale "Wd3" or "N1" was clipped inside a fixed
    // 24dp square. At the default scale every chip is still exactly 24dp.
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BouncerThemeV2.typography.numericSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The over strip's glyph, in Cricbuzz's own notation, or null when the runs are unknown and nothing
 * else names the ball.
 *
 * `Wd` / `Wd3` / `N1` / `B1` / `L1` are exactly what Cricbuzz's `overSummary` writes, so an extra
 * that cost more than one reads as what it was worth. Flattening every wide to `WD` threw that
 * away, and a `5 wides` is a very different ball from a `wide`.
 *
 * The no-ball glyph counts the runs off the BAT, because the penalty is already implied by the `N`
 * — `N1` is a no-ball worth two. See [parseBallOutcome].
 */
internal fun ParsedBall.toBallString(): String? = when {
    isWicket -> "W"
    isSix -> "6"
    isFour -> "4"
    extra != null && runs != null -> extraGlyph(extra, runs)
    else -> plainGlyph()
}

/** A delivery with no server verdict: the prose's wide/no-ball guess, then the runs, then null. */
private fun ParsedBall.plainGlyph(): String? = when {
    isWide -> "Wd"
    isNoBall -> "Nb"
    runs == null -> null
    runs == 0 -> "."
    else -> runs.toString()
}

private fun extraGlyph(extra: BallExtra, runs: Int): String = when (extra) {
    BallExtra.WIDE -> if (runs <= 1) "Wd" else "Wd$runs"
    BallExtra.NOBALL -> if (runs <= 1) "Nb" else "N${runs - 1}"
    BallExtra.BYE -> "B$runs"
    BallExtra.LEGBYE -> "L$runs"
}
