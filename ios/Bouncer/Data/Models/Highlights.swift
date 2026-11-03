import Foundation

/// One `highlights` row: a YouTube clip the backend tied to a fixture. Only the columns the app
/// reads are decoded; rows without a web URL are dropped (nothing could open them).
struct Highlight: Decodable, Equatable, Hashable, Identifiable, Sendable {
    var id: String
    var title: String
    var channel: String
    var imageURL: URL?
    var url: URL
    var homeTeam: String
    var awayTeam: String
    var leagueName: String
    /// ISO timestamp of the fixture the clip belongs to ("2026-10-18T00:00:00.000Z").
    var startDate: String

    init(id: String, title: String, channel: String = "", imageURL: URL? = nil, url: URL,
         homeTeam: String, awayTeam: String, leagueName: String = "", startDate: String) {
        self.id = id; self.title = title; self.channel = channel; self.imageURL = imageURL; self.url = url
        self.homeTeam = homeTeam; self.awayTeam = awayTeam; self.leagueName = leagueName; self.startDate = startDate
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        guard let id = w.string("highlight_id"), !id.isEmpty,
              let raw = w.string("url"), let url = URL(string: raw), ["http", "https"].contains(url.scheme?.lowercased()) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "highlight has no id or web url"))
        }
        self.id = id
        self.url = url
        title = w.string("title") ?? ""
        channel = w.string("channel") ?? ""
        imageURL = w.string("img_url").flatMap(URL.init(string:))
        homeTeam = w.string("match_home_team") ?? ""
        awayTeam = w.string("match_away_team") ?? ""
        leagueName = w.string("match_league_name") ?? ""
        startDate = w.string("match_start_date") ?? ""
    }
}

/// The fixture a clip is matched against.
struct HighlightTarget: Equatable, Hashable, Sendable {
    var team1: String
    var team2: String
    /// Series name and match title, compared with a clip's league name.
    var title: String
    /// UTC day of the first ball, "2026-10-18".
    var day: String

    init?(team1: String, team2: String, title: String, startsAt: Date?) {
        guard !team1.isEmpty, !team2.isEmpty, let startsAt else { return nil }
        self.team1 = team1; self.team2 = team2; self.title = title
        day = HighlightDays.string(forDayNumber: HighlightDays.dayNumber(of: startsAt))
    }
}

/// Day arithmetic on ISO date prefixes, in UTC.
enum HighlightDays {
    private static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    static func dayNumber(of date: Date) -> Int { Int((date.timeIntervalSince1970 / 86_400).rounded(.down)) }

    /// Days since 1970-01-01 for the "yyyy-MM-dd" prefix of `iso`, nil when it does not parse.
    static func dayNumber(iso: String) -> Int? {
        let parts = iso.prefix(10).split(separator: "-")
        guard parts.count == 3, let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]),
              let date = calendar.date(from: DateComponents(year: y, month: m, day: d)),
              calendar.dateComponents([.year, .month, .day], from: date).day == d else { return nil }
        return dayNumber(of: date)
    }

    static func string(forDayNumber n: Int) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: TimeInterval(n) * 86_400))
        return String(format: "%04d-%02d-%02d", c.year ?? 1970, c.month ?? 1, c.day ?? 1)
    }

    static func daysBetween(_ a: String, _ b: String) -> Int? {
        guard let x = dayNumber(iso: a), let y = dayNumber(iso: b) else { return nil }
        return abs(x - y)
    }
}

/// Picks the clips that belong to one match (HighlightFuzzyMatcher.kt, with a single candidate).
enum HighlightMatcher {
    static let threshold = 65
    private static let teamsWeight = 60.0
    private static let leagueWeight = 20.0
    private static let dateWeight = 20.0
    private static let maxDayGap = 1
    private static let teamMinSimilarity = 0.92
    /// Tokens naming a different side of the same country or club: "India" and "India Women" are never one team.
    private static let sideQualifiers: Set<String> = ["women", "w", "a", "u19", "u-19", "xi", "lions", "emerging", "legends", "masters"]

    static func matching(_ clips: [Highlight], for target: HighlightTarget) -> [Highlight] {
        clips.filter { score($0, for: target) >= threshold }
    }

    /// Agreement between a clip and the match, 0-100, normalised over the signals that can be compared.
    static func score(_ clip: Highlight, for target: HighlightTarget) -> Int {
        let gap = HighlightDays.daysBetween(clip.startDate, target.day)
        if let gap, gap > maxDayGap { return 0 }
        guard let teams = teamsScore(clip, target.team1, target.team2) else { return 0 }
        var earned = teams
        var available = teamsWeight
        if let league = leagueScore(clip.leagueName, target.title) {
            earned += league
            available += leagueWeight
        }
        if let gap {
            earned += gap == 0 ? dateWeight : dateWeight / 2
            available += dateWeight
        }
        return Int(earned / available * 100)
    }

    /// Each clip team is paired with a different match team; the better of the two pairings counts and
    /// both sides must be the same team.
    private static func teamsScore(_ clip: Highlight, _ t1: String, _ t2: String) -> Double? {
        let home = clip.homeTeam.lowercased().trimmingCharacters(in: .whitespaces)
        let away = clip.awayTeam.lowercased().trimmingCharacters(in: .whitespaces)
        let a = t1.lowercased().trimmingCharacters(in: .whitespaces)
        let b = t2.lowercased().trimmingCharacters(in: .whitespaces)
        let straight = (sameSide(home, a), sameSide(away, b))
        let swapped = (sameSide(home, b), sameSide(away, a))
        let best = swapped.0 + swapped.1 > straight.0 + straight.1 ? swapped : straight
        guard min(best.0, best.1) >= teamMinSimilarity else { return nil }
        return (best.0 + best.1) / 2 * teamsWeight
    }

    private static func sameSide(_ x: String, _ y: String) -> Double {
        qualifiers(x) == qualifiers(y) ? jaroWinkler(x, y) : 0
    }

    private static func qualifiers(_ name: String) -> Set<String> {
        Set(name.split(separator: " ").map(String.init).filter(sideQualifiers.contains))
    }

    private static func leagueScore(_ league: String, _ title: String) -> Double? {
        let l = league.lowercased().trimmingCharacters(in: .whitespaces)
        let t = title.lowercased()
        guard !l.isEmpty, !t.isEmpty else { return nil }
        let similarity = t.contains(l) || l.contains(t) ? 1.0 : jaroWinkler(l, t)
        return similarity * leagueWeight
    }

    static func jaroWinkler(_ a: String, _ b: String) -> Double {
        let s1 = Array(a), s2 = Array(b)
        let j = jaro(s1, s2)
        if j < 0.7 { return j }
        var prefix = 0
        for i in 0..<min(4, s1.count, s2.count) {
            if s1[i] == s2[i] { prefix += 1 } else { break }
        }
        return j + Double(prefix) * 0.1 * (1 - j)
    }

    private static func jaro(_ s1: [Character], _ s2: [Character]) -> Double {
        if s1 == s2 { return 1 }
        if s1.isEmpty || s2.isEmpty { return 0 }
        let window = max(s1.count, s2.count) / 2 - 1
        if window < 0 { return 0 }
        var m1 = [Bool](repeating: false, count: s1.count)
        var m2 = [Bool](repeating: false, count: s2.count)
        var matches = 0
        for i in s1.indices {
            let lo = max(0, i - window), hi = min(i + window + 1, s2.count)
            guard lo < hi else { continue }
            for j in lo..<hi where !m2[j] && s1[i] == s2[j] {
                m1[i] = true; m2[j] = true; matches += 1
                break
            }
        }
        if matches == 0 { return 0 }
        var transpositions = 0, k = 0
        for i in s1.indices where m1[i] {
            while !m2[k] { k += 1 }
            if s1[i] != s2[k] { transpositions += 1 }
            k += 1
        }
        let m = Double(matches)
        return (m / Double(s1.count) + m / Double(s2.count) + (m - Double(transpositions) / 2) / m) / 3
    }
}
