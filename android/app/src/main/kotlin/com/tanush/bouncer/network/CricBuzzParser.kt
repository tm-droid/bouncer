package com.tanush.bouncer.network

import android.util.Log
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.MatchProjections
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.PartnershipStats
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

private const val PARSER_TAG = "CricBuzzParser"

private fun selectorCrumb(selector: String, expected: String, foundCount: Int) {
    if (foundCount > 0) return
    crumb(
        "selector_miss: '$selector' expected $expected",
        SentryLevel.WARNING,
        mapOf("selector" to selector, "expected" to expected, "found" to foundCount)
    )
}

internal data class MiniScoreRows(
    val team1Score: String?,
    val team1Overs: String?,
    val team2Score: String?,
    val team2Overs: String?
)

internal fun parseBatsmenFromDoc(doc: Document): List<ScrapedBatsman> {
    val batsmen = mutableListOf<ScrapedBatsman>()
    val rows = doc.select("div.cb-col.cb-col-100.cb-min-itm-rw").ifEmpty {
        doc.select("div.cb-col-100.cb-min-itm-rw").ifEmpty {
            doc.select("div[class*=min-itm-rw]")
        }
    }
    if (rows.isEmpty()) {
        crumb("parseBatsmen: no rows matched any selector", SentryLevel.WARNING)
    }
    for (row in rows) {
        val nameLink = row.select("div.cb-col-50 a").first()
            ?: row.select("div[class*=col-50] a").first()
            ?: continue
        val name = nameLink.text()
        val stats = row.select("div.cb-col-10, div.cb-col-14")
        if (stats.size < 4) continue
        val isStriker = nameLink.parent()?.text()?.contains("*") == true || row.text().contains("*")
        try {
            batsmen.add(ScrapedBatsman(
                name = name.replace("*", "").trim(),
                runs = stats.getOrNull(0)?.text() ?: "0",
                balls = stats.getOrNull(1)?.text() ?: "0",
                fours = stats.getOrNull(2)?.text() ?: "0",
                sixes = stats.getOrNull(3)?.text() ?: "0",
                strikeRate = stats.getOrNull(stats.size - 1)?.text() ?: "-",
                isStriker = isStriker
            ))
        } catch (e: Exception) {
            Log.w(PARSER_TAG, "Error parsing batsman stats for $name: ${e.message}")
            Sentry.captureException(e)
        }
    }
    return batsmen
}

internal fun parseBowlerFromDoc(doc: Document): ScrapedBowler? {
    val bowlerHeader = doc.select("div.cb-col-50:contains(Bowler)").first()
        ?: doc.select("div[class*=col-50]:contains(Bowler)").first()
    if (bowlerHeader == null) {
        crumb("parseBowler: no Bowler header found", SentryLevel.DEBUG)
        return null
    }
    var current = bowlerHeader.parent()?.nextElementSibling()
    while (current != null && current.hasClass("cb-min-itm-rw")) {
        val name = current.select("div.cb-col-50 a").text()
        val stats = current.select("div.cb-col-10")
        if (stats.size >= 4) {
            return try {
                ScrapedBowler(
                    name,
                    stats.getOrNull(0)?.text() ?: "0",
                    stats.getOrNull(1)?.text() ?: "0",
                    stats.getOrNull(2)?.text() ?: "0",
                    stats.getOrNull(3)?.text() ?: "0",
                    stats.getOrNull(stats.size - 1)?.text() ?: "-"
                )
            } catch (e: Exception) {
                Log.w(PARSER_TAG, "Error parsing bowler stats for $name: ${e.message}")
                Sentry.captureException(e)
                null
            }
        }
        current = current.nextElementSibling()
    }
    return null
}

/**
 * Cricbuzz's rendered "Recent:" bar, as a flat glyph list.
 *
 * This is a rolling window of about two overs and it draws `|` between them. The separator is
 * dropped: every consumer treats this list as deliveries, and a `|` among them renders as a ball
 * that never happened.
 */
internal fun parseRecentBallsFromDoc(doc: Document): List<String> {
    val recentBalls = mutableListOf<String>()
    doc.select("div.cb-min-rcnt span").forEach {
        val text = it.text().trim()
        if (text.isNotEmpty() && text != "|") recentBalls.add(text)
    }
    return recentBalls
}

internal fun parseMiniScoreRowsFromDoc(doc: Document): MiniScoreRows {
    var team1Score: String? = null
    var team2Score: String? = null
    var team1Overs: String? = null
    var team2Overs: String? = null
    val scoreRows = doc.select("div.cb-min-bat-rw").ifEmpty {
        doc.select("div[class*=min-bat-rw]")
    }
    if (scoreRows.isEmpty()) {
        crumb("parseMiniScoreRows: no score rows matched any selector", SentryLevel.WARNING)
    }
    scoreRows.forEachIndexed { index, row ->
        val scoreText = row.select("span.cb-font-20").text().trim()
        val oversText = row.select("span.cb-text-gray, span.cb-font-12").text()
            .replace("(", "").replace(")", "").replace("Ov", "").trim()
        when (index) {
            0 -> { team1Score = scoreText.ifBlank { null }; team1Overs = oversText.ifBlank { null } }
            1 -> { team2Score = scoreText.ifBlank { null }; team2Overs = oversText.ifBlank { null } }
        }
    }
    return MiniScoreRows(team1Score, team1Overs, team2Score, team2Overs)
}

internal fun parseRunRatesFromDoc(doc: Document): Pair<String?, String?> {
    val statusText = doc.select(
        "div.cb-text-complete, div.cb-text-live, div.cb-text-inprogress, div.cb-min-inf"
    ).text()
    val currentRunRate = Regex("CRR\\s*:?\\s*([\\d.]+)").find(statusText)?.groupValues?.get(1)
    val requiredRunRate = Regex("RRR\\s*:?\\s*([\\d.]+)").find(statusText)?.groupValues?.get(1)
    return currentRunRate to requiredRunRate
}

internal fun parseCommentaryLegacyCss(doc: Document): List<String> {
    val lines = mutableListOf<String>()
    doc.select("p.cb-com-ln").forEach { lines.add(it.text().trim()) }
    doc.select("div.cb-col.cb-col-100.cb-com-ln").forEach { lines.add(it.text().trim()) }
    return lines.filter { it.length > 5 }.distinct()
}

internal fun parseInningsHeader(header: Element, content: Element): Innings {
    val teamNameEl = header.select("div.font-bold").first()
        ?: header.select("div[class*=font-bold]").first()
    selectorCrumb("div.font-bold", "team name in innings header", teamNameEl?.let { 1 } ?: 0)
    val teamName = teamNameEl?.text() ?: "Unknown"
    val score = header.select("span.font-bold").text()
    val overs = header.select("span:contains(Ov)").text()
        .replace("(", "").replace(")", "").trim()

    val batsmen = parseScorecardBatsmen(content)
    val bowlers = parseScorecardBowlers(content)
    val fows = parseFallOfWickets(content)

    return Innings(
        teamName,
        score,
        overs,
        batsmen,
        bowlers,
        fows,
        parsePartnerships(content),
        scoreLabelValue(content, "Extras"),
        scoreLabelValue(content, "Total")
    )
}

/**
 * The player's name for one `scorecard-*-grid` row.
 *
 * Cricbuzz's current markup puts the name in a `span.hover:underline`. The row's only `<a>`
 * elements are a hidden per-player menu — "View match performance", then "View profile" pointing at
 * `/profiles/{id}/{slug}` — so the long-standing `row.select("a").first()` named **every** batter
 * and **every** bowler "View match performance". Those names reached Appwrite and then the
 * Scorecard tab, where two LazyColumn items sharing a key is a crash, not a glitch (ANDROID-40).
 *
 * Order matters: the span first, then a link that is not one of the menu labels, so markup old
 * enough to have the name in the anchor still parses. Pinned by `CricBuzzScorecardRowFilterTest`
 * against captured markup.
 */
private val MENU_LINK_LABELS = setOf("View match performance", "View profile")

private fun playerNameOf(row: Element): String {
    row.selectFirst("span[class*='hover:underline']")?.text()?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return row.select("a").firstOrNull { it.text().trim() !in MENU_LINK_LABELS }?.text()?.trim().orEmpty()
}

/** The player's Cricbuzz profile link, which is a menu item rather than the name's own anchor. */
private fun profileUrlOf(row: Element): String =
    row.selectFirst("a[href*=/profiles/]")?.attr("href").orEmpty()

internal fun parseScorecardBatsmen(content: Element): List<BatsmanStats> {
    val batsmen = mutableListOf<BatsmanStats>()
    val batRows = content.select("div.grid.scorecard-bat-grid")
    selectorCrumb("div.grid.scorecard-bat-grid", "scorecard batsman rows", batRows.size)
    batRows.forEach { row ->
        val name = playerNameOf(row)
        if (name.isEmpty() || name.contains("Batter")) return@forEach
        val stats = row.children()
        if (stats.size < 6) return@forEach
        val dismissal = row.select("div.text-cbTxtSec").text().ifBlank {
            row.select("div").drop(1).firstOrNull { el ->
                el.select("a").isEmpty() && el.text().any { it.isLetter() }
            }?.text() ?: ""
        }
        try {
            batsmen.add(BatsmanStats(
                name,
                dismissal,
                stats.getOrNull(1)?.text() ?: "0",
                stats.getOrNull(2)?.text() ?: "0",
                stats.getOrNull(3)?.text() ?: "0",
                stats.getOrNull(4)?.text() ?: "0",
                stats.getOrNull(5)?.text() ?: "0",
                profileUrl = profileUrlOf(row)
            ))
        } catch (e: Exception) {
            Log.w(PARSER_TAG, "Error parsing scorecard batsman: $name", e)
            Sentry.captureException(e)
        }
    }
    return batsmen
}

/**
 * Bowler economy from overs and runs conceded.
 *
 * The economy column was previously read as `stats.lastOrNull()?.text()` — the trailing grid cell,
 * which is usually empty, so `economy` arrived as `""`. Downstream that renders as a blank column
 * (`PremiumScorecardTab.kt:438`) or, worse, `toFloatOrNull() ?: 0f` turns it into a flawless 0.00
 * economy (`:205`). Overs and runs are at known indices, so the figure is derivable exactly.
 *
 * Over notation is not a decimal: "19.5" is 119 balls, not 19.5 overs.
 */
private fun bowlerEconomy(overs: String?, runs: String?): String? {
    val balls = CricketConstants.ballsFromOvers(overs) ?: return null
    val conceded = runs?.toIntOrNull() ?: return null
    // Locale.US is load-bearing here rather than cosmetic: this is scraped data being
    // re-serialised, and a comma-decimal locale would produce "4,25", which the callers'
    // toDoubleOrNull cannot read back.
    return MatchProjections.currentRunRate(conceded, balls)?.let { String.format(Locale.US, "%.2f", it) }
}

internal fun parseScorecardBowlers(content: Element): List<BowlerStats> {
    val bowlers = mutableListOf<BowlerStats>()
    val bowlRows = content.select("div.grid.scorecard-bowl-grid")
    selectorCrumb("div.grid.scorecard-bowl-grid", "scorecard bowler rows", bowlRows.size)
    bowlRows.forEach { row ->
        val name = playerNameOf(row)
        if (name.isEmpty() || name.contains("Bowler")) return@forEach
        val stats = row.children()
        if (stats.size < 5) return@forEach
        try {
            val overs = stats.getOrNull(1)?.text() ?: "0"
            val runs = stats.getOrNull(3)?.text() ?: "0"
            bowlers.add(BowlerStats(
                name,
                overs,
                stats.getOrNull(2)?.text() ?: "0",
                runs,
                stats.getOrNull(4)?.text() ?: "0",
                "0",
                "0",
                // Fall back to the grid's own cell only when the overs figure is unparseable.
                bowlerEconomy(overs, runs) ?: stats.lastOrNull()?.text() ?: "",
                profileUrl = profileUrlOf(row)
            ))
        } catch (e: Exception) {
            Log.w(PARSER_TAG, "Error parsing scorecard bowler: $name", e)
            Sentry.captureException(e)
        }
    }
    return bowlers
}

private val FOW_SECTION_HEADER = Regex("^(Fall of Wickets|Powerplays|Partnerships)$", RegexOption.IGNORE_CASE)

/**
 * The rows of one `scorecard-fow-grid` section, as elements.
 *
 * Fall of wickets is a run of sibling grid rows introduced by a bold header row, so the section is
 * bounded by the next header rather than by a container of its own. Rows come back as elements and
 * not as flattened text because the name cell holds the hidden per-player menu alongside the name
 * span — the caller has to run [playerNameOf] over it. Mirrors `fowGridRows` in cricbuzz.js.
 */
private fun fowGridRows(content: Element, header: String): List<Element> {
    val rows = content.select("div.grid.scorecard-fow-grid")
    selectorCrumb("div.grid.scorecard-fow-grid", "fall-of-wickets grid rows", rows.size)
    val out = mutableListOf<Element>()
    var inSection = false
    rows.forEach { row ->
        val head = row.children().firstOrNull()?.text()?.trim().orEmpty()
        if (head.equals(header, ignoreCase = true)) {
            inSection = true
        } else if (FOW_SECTION_HEADER.matches(head)) {
            inSection = false
        } else if (inSection) {
            out.add(row)
        }
    }
    return out
}

private val RUNS_AND_BALLS = Regex("""(\d+)\s*\(\s*(\d+)\s*\)""")

/** `"19(31)"` to `"19" to "31"`, matched out of a cell that also carries a name and menu labels. */
private fun runsAndBalls(text: String): Pair<String, String> =
    RUNS_AND_BALLS.find(text)?.destructured?.let { (runs, balls) -> runs to balls } ?: ("" to "")

/** A bold label ("Extras", "Total") whose value sits in the immediately following sibling. */
private fun scoreLabelValue(content: Element, label: String): String =
    content.select("div.font-bold").firstOrNull { it.text().trim() == label }
        ?.nextElementSibling()?.text()?.trim().orEmpty()

/**
 * Fall of wickets, from the current grid markup.
 *
 * This used to split a `"45-1 (D Warner, 8.2), …"` text blob out of the sibling of a
 * `div:contains(Fall of Wickets)` match — a shape Cricbuzz stopped serving with the App Router
 * rewrite, so it had been returning nothing. `score` is the runs half alone: `mapFallOfWickets`
 * reads it with `toIntOrNull`, for which `"45-1"` is silently 0, and match-sync already writes the
 * split form to `match-scorecard`, so the two sources have to agree.
 */
internal fun parseFallOfWickets(content: Element): List<FallOfWicket> =
    fowGridRows(content, "Fall of Wickets")
        .map { it.children() }
        .filter { it.size >= 3 }
        .mapIndexed { idx, cells ->
            // cells = [name span + hidden menu, "score-wicketNumber", over]
            val scoreCell = cells[1].text().trim()
            val parts = scoreCell.split("-")
            FallOfWicket(
                score = parts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: scoreCell,
                overs = cells[2].text().trim(),
                batsman = playerNameOf(cells[0]),
                number = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: (idx + 1)
            )
        }

/**
 * Partnerships, which are **not** fall-of-wickets rows.
 *
 * They render as `div.scorecard-pship-grid`, which is why the section walk above finds none of them
 * and why `partnerships` reached every device empty. Three cells: player 1 with their own `19(31)`,
 * the partnership's `37(64)`, then `18(33)` with player 2. Contributions are stored as runs alone —
 * `PremiumScorecardTab` reads them with `toIntOrNull`, which `"19(31)"` fails.
 */
internal fun parsePartnerships(content: Element): List<PartnershipStats> {
    val rows = content.select("div.grid.scorecard-pship-grid")
    selectorCrumb("div.grid.scorecard-pship-grid", "partnership rows", rows.size)
    return rows.map { it.children() }
        .filter { it.size >= 3 }
        .map { cells ->
            val (runs, balls) = runsAndBalls(cells[1].text())
            PartnershipStats(
                player1 = playerNameOf(cells[0]),
                player2 = playerNameOf(cells[2]),
                runs = runs,
                balls = balls,
                contribution1 = runsAndBalls(cells[0].text()).first,
                contribution2 = runsAndBalls(cells[2].text()).first
            )
        }
}
