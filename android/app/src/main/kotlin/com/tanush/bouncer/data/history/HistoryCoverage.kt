package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.utils.normalizeForSearch
import com.tanush.bouncer.utils.parseQuery
import com.tanush.bouncer.utils.resolveSide

/**
 * Where Afghanistan's matches and records come from, so a page can say so instead of looking empty.
 *
 * The Cricsheet maintainer has withheld every Afghanistan match since 2024-11-14 (`history-data.md`);
 * no rebuild of the history site brings them back. Bouncer's own archive holds them instead
 * (`archive-data.md`): scorecards from 2009, ball-by-ball where its source has it, and records
 * counted from those scorecards.
 */
object HistoryCoverage {
    private const val AFGHANISTAN = "afghanistan"

    /** [notice] when any of [names] is an Afghanistan side, else null. Case and a "Women"/"A" suffix are ignored. */
    fun afghanistanNotice(names: Iterable<String>, notice: String): String? =
        notice.takeIf { names.any(::isAfghanistan) }

    /**
     * [notice] when [query] names Afghanistan: a side of "a v b", or any word that can only mean it
     * ("afg" through the acronym or prefix machinery of [resolveSide]; "a" could mean many sides, so it does not).
     */
    fun afghanistanNotice(query: String, teams: List<SearchRepository.TeamEntry>, notice: String): String? {
        val parsed = parseQuery(normalizeForSearch(query))
        val sides = parsed.pair?.toList() ?: (parsed.words + parsed.entityText)
        val names = sides.any { side ->
            resolveSide(side, teams).let { it.isNotEmpty() && it.all(::isAfghanistan) }
        }
        return notice.takeIf { names }
    }

    private fun isAfghanistan(name: String): Boolean = normalizeForSearch(name).startsWith(AFGHANISTAN)
}
