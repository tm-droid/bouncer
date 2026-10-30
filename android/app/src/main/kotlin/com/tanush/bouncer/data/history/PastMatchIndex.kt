package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.utils.ParsedQuery
import com.tanush.bouncer.utils.SearchScore
import com.tanush.bouncer.utils.expandedSearchQueries
import com.tanush.bouncer.utils.normalizeForSearch
import com.tanush.bouncer.utils.parseQuery
import com.tanush.bouncer.utils.resolveSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Every past match worth finding by name: Cricsheet's search index (23k rows back to 2003) plus the
 * archive's months, held in memory as word postings so a keystroke never scans the rows.
 *
 * - A word matches exactly, by prefix, or (only when those give fewer than [TYPO_BELOW] rows) within
 *   its [SearchScore.editTolerance] against the vocabulary, not the rows.
 * - A resolved "a v b" needs both sides in the match; year, format and stage only boost.
 * - A Cricsheet row that the archive also holds is dropped at build time; one a loaded match links
 *   to ([HistoryMatchLinker]) is dropped per search. An archive row already loaded is dropped too.
 */
class PastMatchIndex private constructor(
    private val csByDate: Map<String, List<HistoryIndexRow>>,
    rows: List<PastMatchRow>,
) {
    private val entries = rows.map(::Entry)
    private val wordIndex: WordPostings
    private val byTeam: Map<String, IntArray>

    @Volatile
    private var linkedCache: Pair<List<CricketMatch>, Set<String>>? = null

    val size: Int get() = entries.size

    /** Cricsheet rows by match id; a match the archive also holds is missing, its archive row stands in. */
    val cricsheetById: Map<String, PastMatchRow> by lazy {
        entries.map { it.row }.filter { it.source == PastSource.CRICSHEET }.associateBy { it.id }
    }

    init {
        val words = HashMap<String, MutableList<Int>>()
        val teams = HashMap<String, MutableList<Int>>()
        val lastDay = HashMap<String, LocalDate>()
        val eventSides = HashMap<String, MutableSet<String>>()
        entries.forEachIndexed { i, entry ->
            entry.words.forEach { words.getOrPut(it) { mutableListOf() } += i }
            listOf(entry.team1, entry.team2).distinct().forEach { teams.getOrPut(it) { mutableListOf() } += i }
            val date = entry.row.date
            if (entry.eventKey != null && date != null) {
                lastDay.merge(entry.eventKey, date) { a, b -> maxOf(a, b) }
                eventSides.getOrPut(entry.eventKey) { HashSet() }.apply { add(entry.team1); add(entry.team2) }
            }
        }
        entries.forEach {
            it.endsEvent = it.eventKey != null && lastDay[it.eventKey] == it.row.date &&
                (eventSides[it.eventKey]?.size ?: 0) >= TOURNAMENT_SIDES
        }
        wordIndex = WordPostings(words.mapValues { it.value.toIntArray() })
        byTeam = teams.mapValues { it.value.toIntArray() }
    }

    /** One row with its searchable text normalised once. */
    private class Entry(val row: PastMatchRow) {
        val team1 = HistoryMatchLinker.canonical(row.team1)
        val team2 = HistoryMatchLinker.canonical(row.team2)
        private val event = normalizeForSearch(row.event.orEmpty())
        val words: Set<String> = normalizeForSearch("${row.team1} ${row.team2} ${row.event.orEmpty()} ${row.venue.orEmpty()}")
            .split(' ').filterTo(HashSet()) { it.isNotEmpty() }
        val eventKey: String? = row.date?.let { d -> event.takeIf { it.isNotEmpty() }?.let { "$it|${d.year}" } }
        val mentionsFinal = Regex("""(^| )final""").containsMatchIn(event)

        /**
         * Last match of its event that year, in an event of three or more sides: a tournament's
         * final, as far as the index can tell. A two-side series has a last match, not a final.
         */
        var endsEvent = false
    }

    /** Ranked past matches for [query]; [loaded] are the matches the app already shows, so they are not repeated. */
    fun search(
        query: String,
        loaded: List<CricketMatch>,
        teams: List<SearchRepository.TeamEntry>,
        limit: Int = MAX_RESULTS,
    ): List<SearchRepository.SearchResult.PastMatchResult> {
        val q = normalizeForSearch(query)
        if (q.length < MIN_QUERY_LENGTH) return emptyList()
        val parsed = parseQuery(q)
        val candidates = candidates(parsed, teams)
        if (candidates.isEmpty()) return emptyList()
        val loadedIds = loaded.mapTo(HashSet()) { it.id }
        val linked = linkedCricsheetIds(loaded)
        return candidates.entries.asSequence()
            .map { (i, base) -> entries[i] to base }
            .filter { (entry, _) -> visible(entry.row, loadedIds, linked) }
            .map { (entry, base) ->
                SearchRepository.SearchResult.PastMatchResult(
                    entry.row, minOf(1.0, base + facetBoost(parsed, entry)) * SECTION_WEIGHT,
                )
            }
            .sortedWith(
                compareByDescending<SearchRepository.SearchResult.PastMatchResult> { it.relevanceScore }
                    .thenByDescending { it.row.date },
            )
            .take(limit)
            .toList()
    }

    private fun visible(row: PastMatchRow, loadedIds: Set<String>, linked: Set<String>): Boolean = when (row.source) {
        PastSource.ARCHIVE -> row.id !in loadedIds
        PastSource.CRICSHEET -> row.id !in linked
    }

    private fun linkedCricsheetIds(loaded: List<CricketMatch>): Set<String> {
        linkedCache?.takeIf { it.first === loaded }?.let { return it.second }
        val linked = loaded.mapNotNullTo(HashSet()) { match ->
            if (match.isCricsheetOnly) null else HistoryMatchLinker.link(match, nearby(match))
        }
        linkedCache = loaded to linked
        return linked
    }

    private fun nearby(match: CricketMatch): List<HistoryIndexRow> = nearby(csByDate, match)

    private fun candidates(parsed: ParsedQuery, teams: List<SearchRepository.TeamEntry>): Map<Int, Double> {
        pairSides(parsed, teams)?.let { (a, b) -> return pairCandidates(a, b, wantsWomen(parsed, teams)) }
        if (parsed.words.isEmpty()) return facetCandidates(parsed)
        val merged = HashMap<Int, Double>()
        for (variant in expandedSearchQueries(parsed.entityText)) {
            wordIndex.candidates(variant.split(' ')).forEach { (i, score) -> merged.merge(i, score, ::maxOf) }
        }
        return merged
    }

    /** The canonical team names each side of "a v b" can mean, or null when there is no pair or a side is no team. */
    private fun pairSides(
        parsed: ParsedQuery,
        teams: List<SearchRepository.TeamEntry>,
    ): Pair<Set<String>, Set<String>>? {
        val (a, b) = parsed.pair ?: return null
        val first = resolveSide(a, teams).mapTo(HashSet(), HistoryMatchLinker::canonical)
        val second = resolveSide(b, teams).mapTo(HashSet(), HistoryMatchLinker::canonical)
        return if (first.isEmpty() || second.isEmpty()) null else first to second
    }

    private fun pairCandidates(a: Set<String>, b: Set<String>, women: Boolean): Map<Int, Double> {
        val out = HashMap<Int, Double>()
        for (team in a) {
            byTeam[team]?.forEach { i ->
                val e = entries[i]
                if (e.row.women != women) return@forEach
                val other = if (e.team1 == team) e.team2 else e.team1
                if (other in b || (e.team1 in b && e.team2 in a)) out[i] = PAIR_SCORE
            }
        }
        return out
    }

    /** "2011 final" names no team: the facets are all there is to go on, so here they filter. */
    private fun facetCandidates(parsed: ParsedQuery): Map<Int, Double> {
        if (parsed.stage == null) return emptyMap()
        val wanted = listOf(parsed.year, parsed.format).count { it != null } + 1
        val out = HashMap<Int, Double>()
        entries.forEachIndexed { i, e -> if (facetHits(parsed, e) == wanted) out[i] = FACET_ONLY_SCORE }
        return out
    }

    /**
     * Year outweighs format outweighs stage, and the three together stay under the 0.05 between a
     * pair's score and 1.0, so a boost is never clamped away and always beats recency.
     */
    private fun facetBoost(parsed: ParsedQuery, entry: Entry): Double {
        val row = entry.row
        var boost = 0.0
        if (parsed.year != null && row.date?.year == parsed.year) boost += YEAR_BOOST
        if (parsed.format != null && formatMatches(parsed.format, row.format)) boost += FORMAT_BOOST
        if (parsed.stage == "FINAL" && (entry.mentionsFinal || entry.endsEvent)) boost += STAGE_BOOST
        return boost
    }

    private fun facetHits(parsed: ParsedQuery, entry: Entry): Int {
        val row = entry.row
        var hits = 0
        if (parsed.year != null && row.date?.year == parsed.year) hits++
        if (parsed.format != null && formatMatches(parsed.format, row.format)) hits++
        if (parsed.stage == "FINAL" && (entry.mentionsFinal || entry.endsEvent)) hits++
        return hits
    }

    companion object {
        private const val ARCHIVE_URL_MONTHS = "idx/years.json"
        private const val INDEX_MAX_AGE_SECONDS = 24 * 60 * 60
        private const val INDEX_MAX_STALE_SECONDS = 7 * 24 * 60 * 60
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_RESULTS = 50
        private const val PAIR_SCORE = 0.95
        private const val FACET_ONLY_SCORE = 0.5
        private const val YEAR_BOOST = 0.02
        private const val FORMAT_BOOST = 0.015
        private const val STAGE_BOOST = 0.01
        private const val TOURNAMENT_SIDES = 3

        /** Past matches rank below anything current in the same query, which the search screen sorts by score. */
        private const val SECTION_WEIGHT = 0.5

        private val loadLock = Mutex()
        private val current = MutableStateFlow<PastMatchIndex?>(null)

        /** The process-wide index; null until [load] has succeeded. */
        val shared: StateFlow<PastMatchIndex?> = current.asStateFlow()

        /** Builds the index from the two static sites once per process; false when Cricsheet's was unreachable. */
        suspend fun load(): Boolean = loadLock.withLock {
            if (current.value != null) return@withLock true
            val built = withContext(Dispatchers.Default) { fetchAndBuild() } ?: return@withLock false
            current.value = built
            true
        }

        /** The index for [cricsheet] rows plus [archive] rows, minus the Cricsheet rows the archive already has. */
        fun build(cricsheet: List<HistoryIndexRow>, archive: List<PastMatchRow>): PastMatchIndex {
            val byDate = cricsheet.groupBy { it.date }
            val duplicated = archive.mapNotNullTo(HashSet()) { row ->
                HistoryMatchLinker.link(row.toCricketMatch(), nearby(byDate, row.toCricketMatch()))
            }
            val rows = cricsheet.filter { it.id !in duplicated }.map(PastMatchRow::of) + archive
            return PastMatchIndex(byDate, rows)
        }

        private fun nearby(byDate: Map<String, List<HistoryIndexRow>>, match: CricketMatch): List<HistoryIndexRow> {
            val day = match.startDateMs?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                ?: return emptyList()
            return (-1L..1L).flatMap { byDate[day.plusDays(it).toString()].orEmpty() }
        }

        private suspend fun fetchAndBuild(): PastMatchIndex? {
            val bytes = StaticSite.fetch(
                HistoryClient.BASE_URL, "idx/search.json", INDEX_MAX_AGE_SECONDS, INDEX_MAX_STALE_SECONDS,
            ) ?: return null
            val cricsheet = runCatching {
                (AppJson.parseToJsonElement(bytes.decodeToString()) as JsonArray)
                    .mapNotNull { (it as? JsonArray)?.let(HistoryIndexRow::of) }
            }.getOrNull() ?: return null
            return build(cricsheet, archiveRows())
        }

        /** Every archived match, empty when the archive is unreachable: Cricsheet alone still answers. */
        private suspend fun archiveRows(): List<PastMatchRow> {
            val years = StaticSite.fetch(
                ArchiveClient.BASE_URL, ARCHIVE_URL_MONTHS, INDEX_MAX_AGE_SECONDS, INDEX_MAX_STALE_SECONDS,
            )?.let { runCatching { AppJson.parseToJsonElement(it.decodeToString()) as JsonObject }.getOrNull() }
                ?: return emptyList()
            val months = years.flatMap { (year, byMonth) ->
                (byMonth as? JsonObject)?.keys.orEmpty().map { "$year-$it" }
            }
            return coroutineScope {
                months.map { month ->
                    async {
                        StaticSite.fetch(
                            ArchiveClient.BASE_URL, "idx/m/$month.json", INDEX_MAX_AGE_SECONDS, INDEX_MAX_STALE_SECONDS,
                        )?.let { bytes ->
                            runCatching {
                                (AppJson.parseToJsonElement(bytes.decodeToString()) as JsonArray)
                                    .mapNotNull { (it as? JsonArray)?.let(PastMatchRow::ofArchive) }
                            }.getOrNull()
                        }.orEmpty()
                    }
                }.awaitAll().flatten()
            }
        }
    }
}

/** "India" is the men's side; the women's match needs "women" typed, or a side that resolves to one. */
private fun wantsWomen(parsed: ParsedQuery, teams: List<SearchRepository.TeamEntry>): Boolean {
    if (parsed.words.any { it in WOMEN_WORDS }) return true
    val (a, b) = parsed.pair ?: return false
    return (resolveSide(a, teams) + resolveSide(b, teams)).all { normalizeForSearch(it).split(' ').any { w -> w in WOMEN_WORDS } }
}

private fun formatMatches(wanted: String, format: String?): Boolean = when (wanted) {
    "T20I" -> format.equals("T20I", true) || format.equals("IT20", true)
    else -> format.equals(wanted, true)
}

private val WOMEN_WORDS = setOf("women", "womens", "woman", "ladies")
private const val MIN_PREFIX_LENGTH = 2
private const val TYPO_BELOW = 10
private const val EXACT_SCORE = 1.0
private const val PREFIX_SCORE = 0.9
private const val TYPO_SCORE = 0.65

/** Word -> rows, with the sorted vocabulary that prefix and typo lookups walk instead of the rows. */
private class WordPostings(private val postings: Map<String, IntArray>) {
    private val vocabulary: Array<String> = postings.keys.sorted().toTypedArray()

    /** Rows holding every word, scored by how closely each word matched. */
    fun candidates(words: List<String>): Map<Int, Double> {
        var rows: Map<Int, Double>? = null
        for (word in words) {
            val found = rowsFor(word)
            val current = rows
            rows = if (current == null) found else current.mapNotNull { (i, s) -> found[i]?.let { i to (s + it) / 2 } }.toMap()
            if (rows.isEmpty()) return emptyMap()
        }
        return rows.orEmpty()
    }

    private fun rowsFor(word: String): Map<Int, Double> {
        val out = HashMap<Int, Double>()
        postings[word]?.forEach { out[it] = EXACT_SCORE }
        if (word.length >= MIN_PREFIX_LENGTH) {
            prefixWords(word).forEach { w -> postings[w]?.forEach { out.merge(it, PREFIX_SCORE, ::maxOf) } }
        }
        val tolerance = SearchScore.editTolerance(word)
        if (out.size < TYPO_BELOW && tolerance > 0) addTypos(word, tolerance, out)
        return out
    }

    private fun addTypos(word: String, tolerance: Int, out: MutableMap<Int, Double>) {
        for (candidate in vocabulary) {
            if (kotlin.math.abs(candidate.length - word.length) > tolerance) continue
            val distance = SearchScore.editDistance(word, candidate)
            if (distance == 0 || distance > tolerance) continue
            val score = TYPO_SCORE * (1.0 - distance.toDouble() / word.length)
            postings[candidate]?.forEach { out.merge(it, score, ::maxOf) }
        }
    }

    private fun prefixWords(prefix: String): List<String> {
        var at = java.util.Arrays.binarySearch(vocabulary, prefix)
        if (at < 0) at = -at - 1
        val found = ArrayList<String>()
        while (at < vocabulary.size && vocabulary[at].startsWith(prefix)) found += vocabulary[at++]
        return found
    }

}
