package com.tanush.bouncer.data.history

import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.OverPoint
import com.tanush.bouncer.models.Scorecard
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.Locale

/**
 * A Cricsheet match as the app's own [Scorecard] and result line, so a match Bouncer never
 * followed reads through the same tabs as one it did. Super overs are left out of the card, as
 * they are of every record (`.claude/rules/history-data.md`).
 */
fun scorecardOf(m: HistoryMatch): Scorecard {
    val played = m.inn.filter { it.so == 0 }
    val innings = played.map { inningsOf(m, it) }
    val overs = played.flatMapIndexed { index, inn -> overPointsOf(m, inn, index + 1) }
    return Scorecard(innings = innings, overs = overs)
}

/** A side's score and overs as the matches list writes them ("277/4", "48.2"), over all its innings. */
internal fun sideScoreOf(m: HistoryMatch, team: Int): Pair<String, String> {
    val own = m.inn.filter { it.so == 0 && it.t == team }
    val multiDay = m.f.equals("Test", ignoreCase = true) || m.f.equals("MDM", ignoreCase = true)
    val score = own.joinToString(" & ") { listScore(it, multiDay) }
    val overs = own.lastOrNull()?.sc?.tot?.getOrNull(2)?.let { oversOf(it, m.bpo) }.orEmpty()
    return score to overs
}

private fun listScore(inn: HistoryInnings, multiDay: Boolean): String {
    val runs = inn.sc.tot.getOrNull(0) ?: return ""
    val wickets = inn.sc.tot.getOrNull(1) ?: return ""
    if (multiDay && wickets >= ALL_OUT) return "$runs"
    return "$runs/$wickets" + if (inn.dec == 1) "d" else ""
}

private const val ALL_OUT = 10

private fun inningsOf(m: HistoryMatch, inn: HistoryInnings): Innings {
    val sc = inn.sc
    val runs = sc.tot.getOrNull(0) ?: 0
    val wickets = sc.tot.getOrNull(1) ?: 0
    val overs = oversOf(sc.tot.getOrNull(2) ?: 0, m.bpo)
    val score = "$runs-$wickets" + if (inn.dec == 1) " d" else ""
    return Innings(
        teamName = m.t.getOrNull(inn.t) ?: "?",
        score = score,
        overs = "$overs Ov",
        batsmen = sc.bat.map { batsmanOf(m, it) },
        bowlers = sc.bowl.mapNotNull { bowlerOf(m, it) },
        fallOfWickets = sc.fow.mapNotNull { fallOf(m, it) },
        extras = extrasOf(sc.ext),
        total = "$score ($overs Ov)",
    )
}

private fun batsmanOf(m: HistoryMatch, row: JsonArray): BatsmanStats {
    val int = { i: Int -> (row.getOrNull(i) as? JsonPrimitive)?.intOrNull ?: 0 }
    val how = (row.getOrNull(HOW_OUT) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    val runs = int(1)
    val balls = int(2)
    return BatsmanStats(
        name = m.name(int(0)),
        dismissal = how ?: "not out",
        runs = "$runs",
        balls = "$balls",
        fours = "${int(3)}",
        sixes = "${int(4)}",
        strikeRate = if (balls == 0) "0.00" else String.format(Locale.US, "%.2f", runs * PERCENT / balls),
    )
}

private fun bowlerOf(m: HistoryMatch, row: List<Int>): BowlerStats? {
    if (row.size < BOWL_FIELDS) return null
    val balls = row[1]
    val runs = row[3]
    val economy = if (balls == 0) "0.00" else String.format(Locale.US, "%.2f", runs.toDouble() * m.bpo / balls)
    return BowlerStats(
        name = m.name(row[0]),
        overs = oversOf(balls, m.bpo),
        maidens = "${row[2]}",
        runs = "$runs",
        wickets = "${row[4]}",
        noBalls = "${row[6]}",
        wides = "${row[5]}",
        economy = economy,
    )
}

private fun fallOf(m: HistoryMatch, row: JsonArray): FallOfWicket? {
    val int = { i: Int -> (row.getOrNull(i) as? JsonPrimitive)?.intOrNull }
    return FallOfWicket(
        score = "${int(0) ?: return null}",
        overs = (row.getOrNull(3) as? JsonPrimitive)?.content.orEmpty(),
        batsman = m.name(int(2) ?: return null),
        number = int(1) ?: 0,
    )
}

/** "14 (b 1, lb 4, w 8, nb 1)": only the kinds that scored. */
private fun extrasOf(ext: List<Int>): String {
    val labels = listOf("b", "lb", "w", "nb", "p")
    val parts = labels.zip(ext).filter { (_, n) -> n > 0 }.joinToString(", ") { (label, n) -> "$label $n" }
    val total = ext.sum()
    return if (parts.isEmpty()) "$total" else "$total ($parts)"
}

private fun overPointsOf(m: HistoryMatch, inn: HistoryInnings, number: Int): List<OverPoint> {
    var score = 0
    var wickets = 0
    val team = m.t.getOrNull(inn.t)
    return inn.ov.mapIndexedNotNull { index, over ->
        val runs = over.getOrNull(0) ?: return@mapIndexedNotNull null
        score += runs
        wickets += over.getOrNull(1) ?: 0
        OverPoint(innings = number, over = index + 1, runs = runs, score = score, wickets = wickets, team = team)
    }
}

/** Legal balls as overs notation: 290 balls is "48.2", 60 is "10". */
private fun oversOf(balls: Int, bpo: Int): String {
    val whole = balls / bpo
    val part = balls % bpo
    return if (part == 0) "$whole" else "$whole.$part"
}

private const val HOW_OUT = 5
private const val BOWL_FIELDS = 7
private const val PERCENT = 100.0
