import Foundation

/// Backend base URL and key the widget extension needs; mirrors AppwriteConfig without importing the Data layer.
enum WidgetBackend {
    static let suiteName = "group.Testing.Bouncer"
    static let favouritesKey = "favouriteTeams"
    static let projectId = "6a02f7f3000e6e100b19"
    static func fileURL(_ file: String) -> URL {
        URL(string: "https://sgp.cloud.appwrite.io/v1/storage/buckets/cricket-stats/files/\(file)/download")!
    }
    static func matchURL(_ id: String) -> URL { URL(string: "bouncer://match/\(id)")! }
}

struct WidgetTeam: Equatable, Hashable {
    var name: String = ""
    var score: String = ""
    var overs: String = ""
    var isBatting: Bool = false

    /// Initials of the significant words ("Royal Challengers Bengaluru" -> "RCB"); same rule as the app.
    var acronym: String {
        let noise: Set<String> = ["cricket", "team", "national", "the", "of", "and", "xi", "club", "county"]
        let words = name.components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty && !noise.contains($0.lowercased()) }
        if words.count < 2 { return String((words.first ?? name).uppercased().prefix(3)) }
        return String(words.compactMap { $0.first.map { String($0).uppercased() } }.joined().prefix(3))
    }
}

enum WidgetMatchPhase { case upcoming, live, finished }

/// The few fields of a match-list entry the widgets draw.
struct WidgetMatch: Equatable, Identifiable {
    var id: String
    var status: String = ""
    var state: String = ""
    var format: String = ""
    var series: String = ""
    var startsAt: Date?
    var teams: [WidgetTeam] = []

    var team1: WidgetTeam? { teams.first }
    var team2: WidgetTeam? { teams.count > 1 ? teams[1] : nil }

    var phase: WidgetMatchPhase {
        switch state.trimmingCharacters(in: .whitespaces).lowercased() {
        case "preview", "upcoming", "scheduled", "not started", "fixture": return .upcoming
        case "complete", "completed", "result", "abandon", "abandoned", "no result", "cancelled", "canceled",
             "drawn", "draw", "tied", "forfeited", "postponed": return .finished
        case "":
            let s = status.lowercased()
            if s.isEmpty || s.hasPrefix("match starts") { return .upcoming }
            return s.contains("won by") ? .finished : .live
        default: return .live
        }
    }
}

/// Reads the `{ "data": { "matches": [...] } }` envelope of the `*-matches` storage files. Tolerant:
/// numbers and strings are interchangeable, malformed entries are skipped.
enum WidgetSnapshotDecoder {
    static func decode(_ data: Data) -> [WidgetMatch] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let body = root["data"] as? [String: Any],
              let rows = body["matches"] as? [[String: Any]] else { return [] }
        return rows.compactMap(match)
    }

    private static func match(_ row: [String: Any]) -> WidgetMatch? {
        let id = string(row["id"])
        guard !id.isEmpty else { return nil }
        let teams = (row["teams"] as? [[String: Any]] ?? []).map { t in
            WidgetTeam(name: string(t["name"]), score: string(t["score"]), overs: string(t["overs"]),
                       isBatting: (t["isBatting"] as? Bool) ?? false)
        }
        let start = Double(string(row["startDate"])).flatMap { $0 > 0 ? Date(timeIntervalSince1970: $0 / 1000) : nil }
        return WidgetMatch(id: id, status: string(row["status"]), state: string(row["state"]),
                           format: string(row["matchFormat"]), series: string(row["seriesName"]),
                           startsAt: start, teams: teams)
    }

    private static func string(_ any: Any?) -> String {
        switch any {
        case let s as String: return s
        case let n as NSNumber: return n.stringValue
        default: return ""
        }
    }
}

/// Which match the widgets show: live favourite, any live, next favourite upcoming, next upcoming.
enum WidgetMatchSelector {
    static func select(live: [WidgetMatch], upcoming: [WidgetMatch], favourites: [String]) -> WidgetMatch? {
        let liveNow = live.filter { $0.phase == .live }
        let next = upcoming.filter { $0.phase == .upcoming }
            .sorted { ($0.startsAt ?? .distantFuture) < ($1.startsAt ?? .distantFuture) }
        return liveNow.first(where: { isFavourite($0, favourites) }) ?? liveNow.first
            ?? next.first(where: { isFavourite($0, favourites) }) ?? next.first
    }

    /// True when either side's full name or initials equals a favourite (case-insensitive), as the app does.
    static func isFavourite(_ match: WidgetMatch, _ favourites: [String]) -> Bool {
        guard !favourites.isEmpty else { return false }
        let favs = Set(favourites.map { $0.lowercased() })
        return match.teams.contains { team in
            let initials = team.name.lowercased().split(separator: " ").compactMap(\.first).map(String.init).joined()
            return favs.contains(team.name.lowercased()) || (!initials.isEmpty && favs.contains(initials))
        }
    }
}
