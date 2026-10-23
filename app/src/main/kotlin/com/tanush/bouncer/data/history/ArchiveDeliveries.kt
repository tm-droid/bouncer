package com.tanush.bouncer.data.history

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A backfilled match's `balls` (`archive-data.md`: `{v:1, p:[[name, cricbuzzId]], inn:[{i, b, partial?}]}`)
 * as the Cricsheet model, so the commentary lines, overs and worm come from the code that already
 * draws a Cricsheet match. [partial] are the innings numbers (1-based) whose source lacks some
 * deliveries; their overs are left out, because a worm or manhattan of an incomplete innings
 * would state a score the match did not have.
 */
data class ArchivedDeliveries(val match: HistoryMatch, val partial: List<Int>)

private val NOT_A_DISMISSAL_BOWLER_WICKET = setOf("retired hurt", "retired not out")

/** Null when the file has no usable `balls` or fewer than two sides. */
internal fun ArchivedMatch.deliveries(): ArchivedDeliveries? {
    val balls = balls as? JsonObject ?: return null
    val sides = sideNames().takeIf { it.size == 2 } ?: return null
    val people = (balls["p"] as? JsonArray).orEmpty().map { person ->
        val cells = person as? JsonArray
        listOfNotNull((cells?.getOrNull(0) as? JsonPrimitive)?.content, (cells?.getOrNull(1) as? JsonPrimitive)?.content)
    }
    val cardTeams = scorecard()?.innings.orEmpty().map { it.teamName }
    val partial = mutableListOf<Int>()
    val innings = (balls["inn"] as? JsonArray).orEmpty().mapIndexedNotNull { position, element ->
        val inn = element as? JsonObject ?: return@mapIndexedNotNull null
        val number = (inn["i"] as? JsonPrimitive)?.intOrNull ?: (position + 1)
        val isPartial = inn.isPartial()
        if (isPartial) partial += number
        inningsOf(inn, battingSide(cardTeams.getOrNull(number - 1), sides, number), isPartial)
    }
    if (innings.isEmpty()) return null
    return ArchivedDeliveries(HistoryMatch(v = 1, id = id, t = sides, p = people, inn = innings), partial)
}

private fun JsonObject.isPartial(): Boolean =
    (this["partial"] as? JsonPrimitive)?.let { it.content == "1" || it.content == "true" } == true

/** The side batting innings [number], from the card's short name, else by the alternation of a limited-overs match. */
private fun battingSide(cardName: String?, sides: List<String>, number: Int): Int =
    cardName?.let { fullName(it, sides) }?.let(sides::indexOf)?.takeIf { it >= 0 } ?: ((number - 1) % 2)

private fun inningsOf(inn: JsonObject, team: Int, isPartial: Boolean): HistoryInnings? {
    val rows = (inn["b"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonArray)?.let(::normalised) }
    if (rows.isEmpty()) return null
    return HistoryInnings(t = team, b = rows, ov = if (isPartial) emptyList() else oversOf(rows))
}

/** The match's two sides, full names, from its list entry. */
private fun ArchivedMatch.sideNames(): List<String> =
    ((meta as? JsonObject)?.get("teams") as? JsonArray).orEmpty().mapNotNull {
        ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)
    }

/** The one of [sides] that a card's short name ("Afg", "UAE", "Eng") stands for, else the short name. */
internal fun fullName(short: String, sides: List<String>): String {
    val key = short.substringBefore(" 1st").substringBefore(" 2nd").trim().lowercase().filter { it.isLetterOrDigit() }
    if (key.isEmpty()) return short
    val hits = sides.filter { side ->
        val words = side.lowercase().split(' ').filter(String::isNotBlank)
        val squashed = words.joinToString("") { w -> w.filter(Char::isLetterOrDigit) }
        squashed == key || (key.length >= 2 && squashed.startsWith(key)) || words.joinToString("") { it.take(1) } == key
    }
    return hits.singleOrNull() ?: short
}

/** One delivery with its single `[kind, outIdx]` wicket wrapped in the list [HistoryDelivery.of] reads. */
private fun normalised(row: JsonArray): JsonArray {
    val wicket = row.getOrNull(HistoryDelivery.WICKET_AT)
    val single = wicket is JsonArray && wicket.firstOrNull().let { it is JsonPrimitive && it.isString }
    return if (single) JsonArray(row.take(HistoryDelivery.WICKET_AT) + listOf(JsonArray(listOf(wicket)))) else row
}

/** Per over `[runs, wickets]` in bowling order; an over the file skips counts as `[0, 0]`. */
private fun oversOf(rows: List<JsonArray>): List<List<Int>> {
    val byOver = sortedMapOf<Int, IntArray>()
    rows.forEach { row ->
        val d = HistoryDelivery.of(row) ?: return@forEach
        val over = byOver.getOrPut(d.over) { IntArray(2) }
        over[0] += d.batRuns + d.extraRuns
        over[1] += d.wickets.count { it.first.lowercase() !in NOT_A_DISMISSAL_BOWLER_WICKET }
    }
    if (byOver.isEmpty()) return emptyList()
    val last = byOver.lastKey()
    return (0..last).map { over -> byOver[over]?.let { listOf(it[0], it[1]) } ?: listOf(0, 0) }
}

/** What a backfilled match's `info` says: toss, officials, player of the match and the squad lists. */
data class ArchivedInfo(
    val tossWinner: String?,
    /** "bat" or "bowl". */
    val tossDecision: String?,
    val umpires: List<String>,
    val thirdUmpire: String?,
    val referee: String?,
    val playersOfTheMatch: List<String>,
    /** Squad lists by side name; they can include players who did not play, so they are not playing elevens. */
    val squads: List<Pair<String, List<String>>>,
) {
    val isEmpty: Boolean
        get() = tossWinner == null && umpires.isEmpty() && thirdUmpire == null && referee == null &&
            playersOfTheMatch.isEmpty() && squads.isEmpty()
}

internal fun ArchivedMatch.archivedInfo(): ArchivedInfo? {
    val info = info as? JsonObject ?: return null
    val text = { e: JsonElement? -> (e as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf(String::isNotBlank) }
    val names = { e: JsonElement? -> (e as? JsonArray).orEmpty().mapNotNull { text((it as? JsonObject)?.get("name")) } }
    val toss = info["toss"] as? JsonObject
    val decision = text(toss?.get("decision"))?.lowercase()?.let {
        if ("bat" in it) "bat" else if ("bowl" in it || "field" in it) "bowl" else null
    }
    val teams = ((meta as? JsonObject)?.get("teams") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val nameOfTeam = { id: String -> teams.firstOrNull { text(it["teamId"]) == id }?.let { text(it["name"]) } }
    val squads = (info["squads"] as? JsonObject).orEmpty().mapNotNull { (id, players) ->
        val list = names(players)
        nameOfTeam(id)?.takeIf { list.isNotEmpty() }?.let { it to list }
    }
    return ArchivedInfo(
        tossWinner = text(toss?.get("winnerName")),
        tossDecision = decision,
        umpires = names(info["umpires"]),
        thirdUmpire = text((info["thirdUmpire"] as? JsonObject)?.get("name")),
        referee = text((info["referee"] as? JsonObject)?.get("name")),
        playersOfTheMatch = names(info["playersOfTheMatch"]),
        squads = squads,
    ).takeUnless { it.isEmpty }
}
