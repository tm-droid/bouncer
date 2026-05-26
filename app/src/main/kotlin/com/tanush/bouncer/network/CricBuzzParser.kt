package com.tanush.bouncer.network

import android.util.Log
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

internal fun parseRecentBallsFromDoc(doc: Document): List<String> {
    val recentBalls = mutableListOf<String>()
    doc.select("div.cb-min-rcnt span").forEach {
        val text = it.text().trim()
        if (text.isNotEmpty()) recentBalls.add(text)
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

    return Innings(teamName, score, overs, batsmen, bowlers, fows, mutableListOf(), "", "")
}

internal fun parseScorecardBatsmen(content: Element): List<BatsmanStats> {
    val batsmen = mutableListOf<BatsmanStats>()
    val batRows = content.select("div.grid.scorecard-bat-grid")
    selectorCrumb("div.grid.scorecard-bat-grid", "scorecard batsman rows", batRows.size)
    batRows.forEach { row ->
        val name = row.select("a").first()?.text() ?: ""
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
                stats.getOrNull(5)?.text() ?: "0"
            ))
        } catch (e: Exception) {
            Log.w(PARSER_TAG, "Error parsing scorecard batsman: $name", e)
            Sentry.captureException(e)
        }
    }
    return batsmen
}

internal fun parseScorecardBowlers(content: Element): List<BowlerStats> {
    val bowlers = mutableListOf<BowlerStats>()
    val bowlRows = content.select("div.grid.scorecard-bowl-grid")
    selectorCrumb("div.grid.scorecard-bowl-grid", "scorecard bowler rows", bowlRows.size)
    bowlRows.forEach { row ->
        val name = row.select("a").first()?.text() ?: ""
        if (name.isEmpty() || name.contains("Bowler")) return@forEach
        val stats = row.children()
        if (stats.size < 5) return@forEach
        try {
            bowlers.add(BowlerStats(
                name,
                stats.getOrNull(1)?.text() ?: "0",
                stats.getOrNull(2)?.text() ?: "0",
                stats.getOrNull(3)?.text() ?: "0",
                stats.getOrNull(4)?.text() ?: "0",
                "0",
                "0",
                stats.lastOrNull()?.text() ?: ""
            ))
        } catch (e: Exception) {
            Log.w(PARSER_TAG, "Error parsing scorecard bowler: $name", e)
            Sentry.captureException(e)
        }
    }
    return bowlers
}

internal fun parseFallOfWickets(content: Element): List<FallOfWicket> {
    val fows = mutableListOf<FallOfWicket>()
    val fowSection = content.select("div:contains(Fall of Wickets)").first()
    selectorCrumb("div:contains(Fall of Wickets)", "FOW section", fowSection?.let { 1 } ?: 0)
    fowSection ?: return fows
    val fowText = fowSection.nextElementSibling()?.text() ?: ""
    fowText.split("),").forEachIndexed { idx, item ->
        val parts = item.trim().split(" (")
        if (parts.size < 2) return@forEachIndexed
        val detailParts = parts[1].replace(")", "").split(", ")
        if (detailParts.size < 2) return@forEachIndexed
        fows.add(FallOfWicket(
            score = parts[0],
            overs = detailParts[1],
            batsman = detailParts[0],
            number = idx + 1
        ))
    }
    return fows
}
