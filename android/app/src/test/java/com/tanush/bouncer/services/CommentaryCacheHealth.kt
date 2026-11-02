package com.tanush.bouncer.services

import com.tanush.bouncer.utils.BallMetric
import com.tanush.bouncer.utils.CommentaryFormatter
import com.tanush.bouncer.utils.ballMetricOfLine

/**
 * Health checks over an `ai-commentary-cache` export.
 *
 * Every threshold here corresponds to a defect that actually reached production and sat in the
 * shared Appwrite table for months. The point is not to measure quality — it is to notice the
 * specific failure shapes again if they come back:
 *
 *  - one ball number on a third of all rows (the model copying the prompt's own example)
 *  - a cache key that never collides, so the same delivery is regenerated on every poll
 *  - model refusals cached as if they were commentary
 *  - raw scraper lines cached verbatim when the hallucination check rejected the AI output
 *
 * Run against a fresh export with:
 * ```
 * ./gradlew testDebugUnitTest --tests "*CommentaryCacheHealthTest" \
 *   -Dcommentary.cache.csv=/path/to/export.csv
 * ```
 * The checks are implemented once, here, rather than duplicated in a standalone script — a second
 * implementation would drift from [CommentaryFormatter] and [ballMetricOfLine], which are the same
 * functions the production path uses to decide what a ball number and a refusal are.
 */
object CommentaryCacheHealth {

    /** Share of prefixed rows one ball number may hold. `18.5` held 64%. */
    const val MAX_SINGLE_BALL_SHARE = 0.05

    /** Share of rows that may be byte-identical to another row. */
    const val MAX_EXACT_DUPLICATE_RATE = 0.02

    /** Share of rows that may sit in a near-duplicate cluster. */
    const val MAX_NEAR_DUPLICATE_RATE = 0.10

    /** Token overlap at which two rows count as near-duplicates. */
    const val NEAR_DUPLICATE_JACCARD = 0.6

    /**
     * Below this many prefixed rows the distribution checks are not meaningful.
     *
     * A perfectly healthy 15-ball sample has every ball number appearing exactly once, which is
     * 6.7% each — above the 5% ceiling. The ceiling describes a large export, so it may only be
     * applied to one.
     */
    const val MIN_ROWS_FOR_DISTRIBUTION_CHECKS = 50

    /**
     * A raw Cricbuzz commentary line: "<Bowler> to <Batsman>, <outcome>". The AI rewrite never
     * produces this shape, so an unprefixed row matching it is the scraper snippet cached verbatim.
     * A heuristic, deliberately narrow — it is checked against a threshold of zero, so a loose
     * pattern would produce false failures.
     */
    private val RAW_SCRAPER_LINE = Regex("""^[A-Z][\w'\-. ]{1,40} to [A-Z][\w'\-. ]{1,40},\s""")

    private val TOKEN_SPLIT = Regex("""[^a-z0-9]+""")

    data class Violation(val rule: String, val detail: String)

    data class Report(
        val rows: Int,
        val prefixedRows: Int,
        val topBallNumber: String?,
        val topBallNumberShare: Double,
        val distinctOvers: Int,
        val exactDuplicateRate: Double,
        val nearDuplicateRate: Double,
        val metaRows: Int,
        val rawScraperRows: Int,
        val violations: List<Violation>
    ) {
        val healthy: Boolean get() = violations.isEmpty()
    }

    fun analyse(commentaries: List<String>): Report {
        val rows = commentaries.filter { it.isNotBlank() }
        val metrics = rows.mapNotNull { ballMetricOfLine(it.trimStart()) }
        val labelCounts = metrics.groupingBy { it.label }.eachCount()
        val top = labelCounts.maxByOrNull { it.value }
        val topShare = if (metrics.isEmpty()) 0.0 else (top?.value ?: 0).toDouble() / metrics.size
        val distinctOvers = metrics.map { overBucketOf(it) }.distinct().size

        val exactDuplicateRows = rows.groupingBy { it.trim() }.eachCount()
            .filterValues { it > 1 }.values.sum()
        val exactRate = rows.rateOf(exactDuplicateRows)

        val nearRate = rows.rateOf(nearDuplicateRowCount(rows))

        val metaRows = rows.count { CommentaryFormatter.isMetaResponse(it) }
        val rawRows = rows.count { row ->
            ballMetricOfLine(row.trimStart()) == null && RAW_SCRAPER_LINE.containsMatchIn(row.trim())
        }

        return Report(
            rows = rows.size,
            prefixedRows = metrics.size,
            topBallNumber = top?.key,
            topBallNumberShare = topShare,
            distinctOvers = distinctOvers,
            exactDuplicateRate = exactRate,
            nearDuplicateRate = nearRate,
            metaRows = metaRows,
            rawScraperRows = rawRows,
            violations = emptyList()
        ).let { it.copy(violations = violationsOf(it, exactDuplicateRows)) }
    }

    private fun violationsOf(report: Report, exactDuplicateRows: Int): List<Violation> = buildList {
        // Below the minimum sample every ball number looks over-represented: 15 unique deliveries
        // is 6.7% each, above the 5% ceiling. The ceiling describes a large export.
        val distributionMeaningful = report.prefixedRows >= MIN_ROWS_FOR_DISTRIBUTION_CHECKS
        if (distributionMeaningful && report.topBallNumberShare > MAX_SINGLE_BALL_SHARE) {
            add(
                Violation(
                    "single-ball-number-share",
                    "${report.topBallNumber} is ${report.topBallNumberShare.pct()} of " +
                        "${report.prefixedRows} prefixed rows (max ${MAX_SINGLE_BALL_SHARE.pct()})"
                )
            )
        }
        if (distributionMeaningful && report.distinctOvers < 2) {
            add(
                Violation(
                    "ball-number-spread",
                    "${report.distinctOvers} distinct over(s) across ${report.rows} rows — " +
                        "expected at least 2"
                )
            )
        }
        if (report.exactDuplicateRate > MAX_EXACT_DUPLICATE_RATE) {
            add(
                Violation(
                    "exact-duplicates",
                    "$exactDuplicateRows of ${report.rows} rows are byte-identical to another " +
                        "(${report.exactDuplicateRate.pct()}, max ${MAX_EXACT_DUPLICATE_RATE.pct()})"
                )
            )
        }
        if (report.nearDuplicateRate > MAX_NEAR_DUPLICATE_RATE) {
            add(
                Violation(
                    "near-duplicates",
                    "${report.nearDuplicateRate.pct()} of rows sit in a near-duplicate cluster at " +
                        "Jaccard $NEAR_DUPLICATE_JACCARD (max ${MAX_NEAR_DUPLICATE_RATE.pct()})"
                )
            )
        }
        if (report.metaRows > 0) {
            add(Violation("meta-responses", "${report.metaRows} model refusal/meta rows cached (max 0)"))
        }
        if (report.rawScraperRows > 0) {
            add(
                Violation(
                    "raw-scraper-lines",
                    "${report.rawScraperRows} raw scraper lines cached verbatim (max 0)"
                )
            )
        }
    }

    /** The Hundred has no overs, so its counter is bucketed into over-equivalents to compare. */
    private fun overBucketOf(metric: BallMetric): Int = when (metric) {
        is BallMetric.OverBall -> metric.over
        is BallMetric.BallCount -> metric.ball / 6
    }

    private fun List<String>.rateOf(count: Int): Double =
        if (isEmpty()) 0.0 else count.toDouble() / size

    /**
     * Rows belonging to a cluster of two or more rows with token overlap at or above
     * [NEAR_DUPLICATE_JACCARD], after stripping the ball-number prefix — the prefix was the only
     * thing distinguishing many of the duplicates, and only because it was fabricated.
     */
    private fun nearDuplicateRowCount(rows: List<String>): Int {
        val tokenSets = rows.map { row ->
            val stripped = ballMetricOfLine(row.trimStart())
                ?.let { row.trimStart().removePrefix(it.label).removePrefix(":") }
                ?: row
            stripped.lowercase().split(TOKEN_SPLIT).filter { it.isNotEmpty() }.toSet()
        }
        val clustered = BooleanArray(rows.size)
        for (i in rows.indices) {
            for (j in i + 1 until rows.size) {
                if (clustered[i] && clustered[j]) continue
                if (jaccard(tokenSets[i], tokenSets[j]) >= NEAR_DUPLICATE_JACCARD) {
                    clustered[i] = true
                    clustered[j] = true
                }
            }
        }
        return clustered.count { it }
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        return intersection.toDouble() / (a.size + b.size - intersection)
    }

    private fun Double.pct(): String = "%.1f%%".format(this * 100)

    /**
     * The `commentary` column of an Appwrite CSV export.
     *
     * A hand-rolled RFC 4180 reader rather than a split on commas: commentary contains commas,
     * embedded newlines and doubled quotes, and a naive split silently shreds a third of the rows
     * into fragments that then look like distinct entries.
     */
    fun commentaryColumnOf(csv: String): List<String> {
        val records = parseCsv(csv)
        if (records.isEmpty()) return emptyList()
        val header = records.first()
        val index = header.indexOf("commentary")
        require(index >= 0) { "export has no 'commentary' column; found ${header.joinToString()}" }
        return records.drop(1).mapNotNull { it.getOrNull(index) }
    }

    private fun parseCsv(csv: String): List<List<String>> = CsvReader(csv).read()

    private class CsvReader(private val csv: String) {
        private val records = mutableListOf<List<String>>()
        private var record = mutableListOf<String>()
        private val field = StringBuilder()
        private var inQuotes = false
        private var i = 0

        fun read(): List<List<String>> {
            while (i < csv.length) {
                step(csv[i])
                i++
            }
            endRecord()
            return records
        }

        private fun step(c: Char) = if (inQuotes) stepQuoted(c) else stepUnquoted(c)

        private fun stepQuoted(c: Char) {
            when {
                // Doubled quote inside a quoted field is an escaped quote, not the closing one.
                c == '"' && csv.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
        }

        private fun stepUnquoted(c: Char) {
            when (c) {
                '"' -> inQuotes = true
                ',' -> endField()
                '\n' -> endRecord()
                '\r' -> {
                    if (csv.getOrNull(i + 1) == '\n') i++
                    endRecord()
                }
                else -> field.append(c)
            }
        }

        private fun endField() {
            record.add(field.toString())
            field.clear()
        }

        private fun endRecord() {
            endField()
            if (record.any { it.isNotEmpty() }) records.add(record)
            record = mutableListOf()
        }
    }
}
