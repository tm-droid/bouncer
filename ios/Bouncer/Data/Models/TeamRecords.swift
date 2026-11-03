import Foundation

/// One side's results in one format, from `s/team/<key>.json` `f`: `[played, won, lost, tied, drawn, no result]`.
struct FormatRecord: Equatable, Sendable {
    var format: String
    var played: Int
    var won: Int
    var lost: Int
    var tied: Int
    var drawn: Int
    /// True when counted from Bouncer's own archive because Cricsheet holds nothing for the side.
    var fromArchive = false

    /// "Played 219 · Won 105 · Lost 60 · Drawn 54"; tied and drawn only when there were any.
    var summary: String {
        var parts = ["Played \(played)", "Won \(won)", "Lost \(lost)"]
        if tied > 0 { parts.append("Tied \(tied)") }
        if drawn > 0 { parts.append("Drawn \(drawn)") }
        return parts.joined(separator: " · ")
    }
}

/// One side in `idx/teams.json`: `[name, gender, key, matches]`.
struct HistoryTeam: Equatable, Sendable {
    var name: String
    var women: Bool
    var key: String
    var matches: Int
}

/// Parsers and name rules for the history site (`TeamRecords` in HistoryRecords.kt / HistoryTeams.kt).
enum TeamRecords {
    private static let womenSuffix = #/\s+Women(?:'s)?$/#.ignoresCase()
    private static let qualifiers: Set<String> = ["a", "u19", "u 19", "under 19", "emerging", "academy", "development"]
    private static let noise: Set<String> = ["cricket", "team", "national", "the", "of", "and", "club", "county", "women", "w", "xi"]

    /// A side's name as Cricsheet writes it: a women's side is the plain name, the gender apart.
    static func archiveSideName(_ name: String) -> String {
        name.trimmingCharacters(in: .whitespaces).replacing(womenSuffix, with: "")
    }

    static func isWomens(_ name: String) -> Bool {
        name.trimmingCharacters(in: .whitespaces).contains(womenSuffix)
    }

    /// The archive's `s/team` file key: the name lowercased, accents folded, punctuation dropped, spaces to hyphens.
    static func archiveKey(_ name: String) -> String {
        let folded = name.folding(options: .diacriticInsensitive, locale: nil).lowercased()
        let words = folded.components(separatedBy: CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyz0123456789").inverted)
            .filter { !$0.isEmpty }
        return words.isEmpty ? "unknown" : words.joined(separator: "-")
    }

    static func parseTeams(_ data: Data) -> [HistoryTeam]? {
        guard let rows = (try? JSONSerialization.jsonObject(with: data)) as? [[Any]] else { return nil }
        return rows.compactMap { cells in
            guard cells.count >= 3, let name = cells[0] as? String, let key = cells[2] as? String else { return nil }
            return HistoryTeam(name: name, women: cells[1] as? String == "female", key: key,
                               matches: cells.count > 3 ? (cells[3] as? Int ?? 0) : 0)
        }
    }

    /// Formats the side has played, busiest first; nil when the file is not a team record.
    static func parseTeam(_ data: Data) -> [FormatRecord]? {
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let f = root["f"] as? [String: Any] else { return nil }
        return f.compactMap { format, row -> FormatRecord? in
            guard let n = (row as? [Any])?.map({ $0 as? Int ?? 0 }), n.count >= 6, n[0] > 0 else { return nil }
            return FormatRecord(format: format, played: n[0], won: n[1], lost: n[2], tied: n[3], drawn: n[4])
        }
        .sorted { ($0.played, $1.format) > ($1.played, $0.format) }
    }

    /// Normalised, with the women's marker dropped: gender is compared on its own.
    static func canonical(_ name: String) -> String {
        TeamAcronym.normalize(name).split(separator: " ").filter { $0 != "women" && $0 != "w" }.joined(separator: " ")
    }

    private static func qualifierSet(_ name: String) -> Set<String> {
        let words = Set(canonical(name).split(separator: " ").map(String.init))
        return qualifiers.filter { q in q.split(separator: " ").allSatisfy { words.contains(String($0)) } }
    }

    private static func significantWords(_ name: String) -> Set<String> {
        Set(canonical(name).split(separator: " ").map(String.init).filter { !noise.contains($0) && !qualifiers.contains($0) })
    }

    /// The same side or a renamed one ("Kings XI Punjab" / "Punjab Kings"): equal, or same qualifiers and a shared significant word.
    static func sameOrRenamed(_ a: String, _ b: String) -> Bool {
        if canonical(a) == canonical(b) { return true }
        return qualifierSet(a) == qualifierSet(b) && !significantWords(a).isDisjoint(with: significantWords(b))
    }

    /// The `s/team` key only when the name is certainly that side: an exact name, else a single renamed candidate.
    static func key(in teams: [HistoryTeam], name: String, women: Bool) -> String? {
        let sameGender = teams.filter { $0.women == women }
        let exact = sameGender.filter { canonical($0.name) == canonical(name) }
        if !exact.isEmpty { return exact.count == 1 ? exact[0].key : nil }
        let renamed = sameGender.filter { sameOrRenamed($0.name, name) }
        return renamed.count == 1 ? renamed[0].key : nil
    }
}
