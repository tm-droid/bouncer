import Foundation

/// Text normalisation for search (Android `normalizeForSearch`): accent-folded, lowercased,
/// punctuation-free, single-spaced. "St. Kitts" and "St Kitts" are the same name.
enum SearchText {
    static func normalize(_ text: String) -> String {
        let folded = text.folding(options: .diacriticInsensitive, locale: nil).lowercased()
            .replacing(#/[.'’`]/#, with: "")
        return folded.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }.joined(separator: " ")
    }

    /// Competition acronyms expanded to the words stored series names contain ("ipl" -> "indian premier").
    private static let aliases: [String: [String]] = Dictionary(uniqueKeysWithValues: [
        ("ipl", ["Indian Premier"]), ("bbl", ["Big Bash"]), ("wbbl", ["Women's Big Bash"]),
        ("psl", ["Pakistan Super"]), ("cpl", ["Caribbean Premier"]), ("sa20", ["SA20"]),
        ("lpl", ["Lanka Premier"]), ("tnpl", ["Tamil Nadu Premier"]), ("ilt20", ["ILT20", "International League"]),
        ("mlc", ["Major League"]), ("the hundred", ["Hundred"]), ("wpl", ["Women's Premier"]),
        ("wc", ["World Cup"]), ("t20 wc", ["World Cup"]), ("t20wc", ["World Cup"]), ("odi wc", ["World Cup"]),
        ("cwc", ["World Cup"]), ("ct", ["Champions Trophy"]), ("wtc", ["World Test Championship"]),
        ("acc", ["Asia Cup"]),
    ].map { (normalize($0.0), $0.1.map(normalize)) })

    /// The query itself first, then its alias expansion whole and word by word ("ipl 2026" -> "indian premier 2026").
    static func expanded(_ q: String) -> [String] {
        guard !q.isEmpty else { return [] }
        let tokens = q.split(separator: " ").map(String.init)
        var out = [q] + (aliases[q] ?? [])
        if tokens.count > 1, tokens.contains(where: { aliases[$0] != nil }) {
            let perToken = tokens.reduce([""]) { prefixes, token in
                let options = [token] + (aliases[token] ?? [])
                return prefixes.flatMap { p in options.map { "\(p) \($0)".trimmingCharacters(in: .whitespaces) } }
            }
            out += perToken
        }
        var seen = Set<String>()
        return out.filter { seen.insert($0).inserted }
    }
}

/// Relevance of an already-normalised query against a target, 0-1 (Android `SearchScore`). Each band's
/// floor sits above the next band's ceiling, so the kind of match dominates and coverage orders within it.
enum SearchScore {
    static let prefixMin = 0.95
    static let wordPrefixMin = 0.85
    static let substringMin = 0.70
    static let fuzzyMax = 0.65
    /// A query equal to a team's code: as certain as typing the name.
    static let acronym = 0.99

    static func of(_ query: String, _ target: String, allowFuzzy: Bool = true) -> Double {
        guard !query.isEmpty, !target.isEmpty else { return 0 }
        let coverage = min(1, Double(query.count) / Double(target.count))
        if target.hasPrefix(query) { return prefixMin + (1 - prefixMin) * coverage }

        let words = target.split(separator: " ").map(String.init)
        if words.contains(where: { $0.hasPrefix(query) }) {
            return wordPrefixMin + (prefixMin - wordPrefixMin) * coverage
        }
        if let r = target.range(of: query) {
            // A match late in the string is weaker evidence than one near the front.
            let position = 1 - Double(target.distance(from: target.startIndex, to: r.lowerBound)) / Double(target.count)
            return substringMin + (wordPrefixMin - substringMin) * coverage * position
        }
        let tokens = query.split(separator: " ").map(String.init)
        if tokens.count > 1 { return tokenScore(tokens, words, coverage, allowFuzzy) }
        return allowFuzzy ? fuzzyWordScore(query, words) : 0
    }

    /// Every query word must prefix a target word or sit within its edit tolerance, in any order.
    private static func tokenScore(_ tokens: [String], _ words: [String], _ coverage: Double, _ allowFuzzy: Bool) -> Double {
        var anyFuzzy = false
        var weighted = 0.0
        var total = 0
        for token in tokens {
            total += token.count
            if words.contains(where: { $0.hasPrefix(token) }) { weighted += Double(token.count); continue }
            guard allowFuzzy else { return 0 }
            let distance = words.map { editDistance(token, $0) }.min() ?? Int.max
            guard distance <= editTolerance(token) else { return 0 }
            anyFuzzy = true
            weighted += Double(token.count) * (1 - Double(distance) / Double(token.count))
        }
        if !anyFuzzy { return wordPrefixMin + (prefixMin - wordPrefixMin) * coverage }
        return min(max(fuzzyMax * weighted / Double(total), 0), fuzzyMax)
    }

    private static func fuzzyWordScore(_ query: String, _ words: [String]) -> Double {
        guard let distance = words.map({ editDistance(query, $0) }).min(), distance <= editTolerance(query) else { return 0 }
        return min(max((1 - Double(distance) / Double(query.count)) * fuzzyMax, 0), fuzzyMax)
    }

    /// None up to 3 characters, one up to 7, two from 8.
    static func editTolerance(_ query: String) -> Int {
        query.count <= 3 ? 0 : query.count <= 7 ? 1 : 2
    }

    /// Optimal-string-alignment distance: Levenshtein with an adjacent swap ("kolhi") as one edit.
    static func editDistance(_ s: String, _ t: String) -> Int {
        let a = Array(s), b = Array(t)
        if a == b { return 0 }
        if a.isEmpty { return b.count }
        if b.isEmpty { return a.count }
        var prevPrev = [Int](repeating: 0, count: b.count + 1)
        var prev = Array(0...b.count)
        var curr = prev
        for i in 1...a.count {
            curr[0] = i
            for j in 1...b.count {
                let cost = a[i - 1] == b[j - 1] ? 0 : 1
                var best = min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if i > 1, j > 1, a[i - 1] == b[j - 2], a[i - 2] == b[j - 1] { best = min(best, prevPrev[j - 2] + 1) }
                curr[j] = best
            }
            (prevPrev, prev, curr) = (prev, curr, prevPrev)
        }
        return prev[b.count]
    }
}

struct Scored<Item> {
    var item: Item
    var score: Double
}

struct SearchTeam: Equatable {
    var name: String
    var acronym: String
    /// Loaded matches this team plays in.
    var fixtures: Int
}

struct SearchResults {
    var matches: [Scored<MatchSummary>] = []
    var teams: [Scored<SearchTeam>] = []
    var series: [Scored<SeriesSummary>] = []

    var isEmpty: Bool { matches.isEmpty && teams.isEmpty && series.isEmpty }
}

/// Local fuzzy search over what is already loaded (Android `SearchRepository`), plus recent queries.
enum SearchRepository {
    static let minQueryLength = 2
    static let minPlayerQueryLength = 3
    static let maxMatches = 20, maxTeams = 10, maxSeries = 10
    private static let matchThreshold = 0.3, entityThreshold = 0.4
    private static let seriesNameWeight = 0.9, matchDescWeight = 0.6
    private static let statusHitScore = 0.3, categoryOnlySeriesScore = 0.45

    /// `teamNames` are the curated names (TeamLogos keys); teams seen in `matches` are added to them.
    static func search(_ query: String, matches: [MatchSummary], series: [SeriesSummary], teamNames: [String],
                       isFavourite: (String) -> Bool = { _ in false }) -> SearchResults {
        let q = SearchText.normalize(query)
        guard q.count >= minQueryLength else { return SearchResults() }
        var seenIDs = Set<String>()
        let loaded = matches.filter { seenIDs.insert($0.id).inserted }
        let teams = corpus(loaded, teamNames)
        // A query that is itself a team's name or code is not a typo: "india" is two edits from "indies".
        let exactEntity = teams.contains { SearchText.normalize($0.name) == q || SearchText.normalize($0.acronym) == q }
        return SearchResults(
            matches: searchMatches(q, loaded, allowFuzzy: !exactEntity, isFavourite: isFavourite),
            teams: searchTeams(q, teams, isFavourite: isFavourite),
            series: searchSeries(q, series))
    }

    /// Loaded sides first, then the curated names; the order stands in for popularity.
    private static func corpus(_ matches: [MatchSummary], _ curated: [String]) -> [SearchTeam] {
        var fixtures: [String: Int] = [:]
        var names: [String] = []
        for team in matches.flatMap(\.teams) {
            let key = SearchText.normalize(team.name)
            guard !key.isEmpty else { continue }
            if fixtures[key] == nil { names.append(team.name) }
            fixtures[key, default: 0] += 1
        }
        var seen = Set(names.map(SearchText.normalize))
        names += curated.filter { seen.insert(SearchText.normalize($0)).inserted }
        return names.map { SearchTeam(name: $0, acronym: TeamAcronym.make($0), fixtures: fixtures[SearchText.normalize($0)] ?? 0) }
    }

    private static func searchMatches(_ q: String, _ matches: [MatchSummary], allowFuzzy: Bool,
                                      isFavourite: (String) -> Bool) -> [Scored<MatchSummary>] {
        let queries = SearchText.expanded(q)
        func score(_ m: MatchSummary) -> Double {
            let team = m.teams.map { t in
                SearchText.normalize(t.acronym) == q ? SearchScore.acronym : SearchScore.of(q, SearchText.normalize(t.name), allowFuzzy: allowFuzzy)
            }.max() ?? 0
            let series = queries.map { SearchScore.of($0, SearchText.normalize(m.seriesName), allowFuzzy: allowFuzzy) }.max() ?? 0
            let status = SearchText.normalize(m.status).contains(q) ? statusHitScore : 0
            return max(team,
                       SearchScore.of(q, SearchText.normalize(m.title), allowFuzzy: allowFuzzy),
                       series * seriesNameWeight,
                       SearchScore.of(q, SearchText.normalize(m.matchDesc), allowFuzzy: allowFuzzy) * matchDescWeight,
                       status)
        }
        func stateRank(_ m: MatchSummary) -> Int { m.phase == .live ? 0 : m.phase == .upcoming ? 1 : 2 }
        /// Within a state: finished newest first, upcoming soonest first, unknown start last.
        func recency(_ m: MatchSummary) -> Double {
            guard let s = m.startsAt?.timeIntervalSince1970 else { return .greatestFiniteMagnitude }
            return m.phase == .upcoming ? s : -s
        }
        let hits = matches.enumerated().compactMap { i, m -> (i: Int, m: MatchSummary, score: Double)? in
            let s = score(m)
            return s > matchThreshold ? (i, m, s) : nil
        }
        return hits.sorted { a, b in
            if a.score != b.score { return a.score > b.score }
            if stateRank(a.m) != stateRank(b.m) { return stateRank(a.m) < stateRank(b.m) }
            if recency(a.m) != recency(b.m) { return recency(a.m) < recency(b.m) }
            let fa = a.m.teams.contains { isFavourite($0.name) }, fb = b.m.teams.contains { isFavourite($0.name) }
            return fa != fb ? fa : a.i < b.i
        }
        .prefix(maxMatches).map { Scored(item: $0.m, score: $0.score) }
    }

    private static func searchTeams(_ q: String, _ teams: [SearchTeam], isFavourite: (String) -> Bool) -> [Scored<SearchTeam>] {
        let hits = teams.enumerated().compactMap { i, t -> (i: Int, t: SearchTeam, score: Double)? in
            let name = SearchScore.of(q, SearchText.normalize(t.name))
            let s = SearchText.normalize(t.acronym) == q ? max(name, SearchScore.acronym) : name
            return s > entityThreshold ? (i, t, s) : nil
        }
        return hits.sorted { a, b in
            if a.score != b.score { return a.score > b.score }
            let fa = isFavourite(a.t.name), fb = isFavourite(b.t.name)
            if fa != fb { return fa }
            return a.t.fixtures != b.t.fixtures ? a.t.fixtures > b.t.fixtures : a.i < b.i
        }
        .prefix(maxTeams).map { Scored(item: $0.t, score: $0.score) }
    }

    private static func searchSeries(_ q: String, _ series: [SeriesSummary]) -> [Scored<SeriesSummary>] {
        let queries = SearchText.expanded(q)
        let hits = series.enumerated().compactMap { i, s -> (i: Int, s: SeriesSummary, score: Double)? in
            let name = SearchText.normalize(s.name)
            let nameScore = queries.map { SearchScore.of($0, name) }.max() ?? 0
            let category = SearchText.normalize(s.category)
            let score = max(nameScore, !category.isEmpty && category.hasPrefix(q) ? categoryOnlySeriesScore : 0)
            return score > entityThreshold ? (i, s, score) : nil
        }
        // Equal scores keep the corpus order.
        return hits.sorted { $0.score != $1.score ? $0.score > $1.score : $0.i < $1.i }
            .prefix(maxSeries).map { Scored(item: $0.s, score: $0.score) }
    }

    /// Relevance of a remote player hit, used only to order the Players section among the others.
    static func playerScore(_ query: String, _ name: String) -> Double {
        SearchScore.of(SearchText.normalize(query), SearchText.normalize(name))
    }

    static let trending = ["India", "Australia", "IPL", "World Cup", "Virat Kohli", "Joe Root", "T20", "ODI", "Champions Trophy", "Ashes"]
}

/// Recent queries, newest first, stored as one `|||`-joined string (Android's format).
enum SearchRecents {
    static let limit = 10
    private static let separator = "|||"

    static func decode(_ raw: String) -> [String] { raw.components(separatedBy: separator).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty } }
    static func encode(_ queries: [String]) -> String { queries.joined(separator: separator) }

    /// `query` moved to the front, case-insensitive duplicates dropped; too-short queries are ignored.
    static func adding(_ query: String, to queries: [String]) -> [String] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= SearchRepository.minQueryLength else { return queries }
        let rest = queries.filter { $0.caseInsensitiveCompare(trimmed) != .orderedSame }
        return Array(([trimmed] + rest).prefix(limit))
    }
}
