package com.tanush.bouncer.data.history

/** "India won by 6 wickets", "Match tied", "No result"; always something the status classifier reads as over. */
fun resultText(m: HistoryMatch): String {
    val o = m.o
    val winner = m.t.getOrNull(o.w)
    val method = o.m?.let { " (${if (it == "D/L") "DLS" else it})" }.orEmpty()
    return when {
        winner != null -> "$winner ${margin(o.by)}$method"
        o.r == "tie" -> tiedText(m)
        o.r == "draw" -> "Match drawn"
        o.r == "no result" -> "No result"
        else -> "Match complete"
    }
}

private fun margin(by: Map<String, Int>): String {
    val runs = by["runs"]
    val wickets = by["wickets"]
    return when {
        by["innings"] != null && runs != null -> "won by an innings and ${count(runs, "run")}"
        runs != null -> "won by ${count(runs, "run")}"
        wickets != null -> "won by ${count(wickets, "wicket")}"
        else -> "won the match"
    }
}

private fun count(n: Int, unit: String): String = if (n == 1) "1 $unit" else "$n ${unit}s"

private fun tiedText(m: HistoryMatch): String {
    val breaker = m.t.getOrNull(m.o.el) ?: return "Match tied"
    val how = if (m.inn.any { it.so == 1 }) "super over" else "bowl-out"
    return "Match tied ($breaker won the $how)"
}
