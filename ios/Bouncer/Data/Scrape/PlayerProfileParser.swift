import Foundation

/// `/profiles/{id}/{slug}` out of Cricbuzz's App Router flight stream. Port of Android's `PlayerProfileRsc`.
/// The stream carries a player object (`faceImageId`, `name`, `bat`, `bowl`, `recentBatting`) and a
/// sibling holding `battingStats`/`bowlingStats` as transposed tables: a format is a column, and each
/// statistic is read by its row label.
enum PlayerProfileParser {
    static let maxRecentInnings = 10
    /// Cricbuzz's column header for T20 internationals, against the label the app shows.
    private static let headerT20 = "T20"
    private static let labelT20 = "T20I"

    /// Nil when the page carries no player object (a 404 body, a redirect): the caller's signal to
    /// fail, never a reason to show an empty profile.
    static func parse(html: String, id: String, fallbackName: String) -> PlayerProfile? {
        let payloads = CricbuzzFlightParser.payloads(in: html)
        guard let player = payloads.lazy.compactMap({ p in
            CricbuzzFlightParser.findObject(in: p) { $0["faceImageId"] != nil && $0["name"] != nil }
        }).first else { return nil }
        let holder = payloads.lazy.compactMap { p in
            CricbuzzFlightParser.findObject(in: p) { $0["battingStats"] != nil }
        }.first

        return PlayerProfile(
            id: id,
            name: string(player["name"]) ?? fallbackName,
            country: string(player["intlTeam"]) ?? "",
            role: string(player["role"]) ?? "",
            // DoB carries a running age ("November 05, 1988 (37 years)") that is stale once cached.
            dateOfBirth: string(player["DoBFormat"]) ?? string(player["DoB"]) ?? "",
            battingStyle: string(player["bat"]) ?? "",
            bowlingStyle: string(player["bowl"]) ?? "",
            imageURL: string(player["faceImageId"]).flatMap(PlayerProfile.imageURL(faceImageId:)),
            bio: string(player["bio"]) ?? "",
            teams: (string(player["teams"]) ?? "").split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty },
            stats: holder.map(formatStats) ?? [:],
            recentForm: recentForm(player))
    }

    private static func formatStats(_ holder: [String: Any]) -> [String: FormatStats] {
        guard let batting = table(holder["battingStats"]) else { return [:] }
        let bowling = table(holder["bowlingStats"])
        var out: [String: FormatStats] = [:]
        for header in batting.formats {
            let bat = batting.column(header)
            let bowl = bowling?.column(header) ?? [:]
            var s = FormatStats()
            s.matches = int(bat["Matches"]); s.innings = int(bat["Innings"]); s.runs = int(bat["Runs"])
            s.highScore = bat["Highest"] ?? "-"
            s.average = double(bat["Average"]); s.strikeRate = double(bat["SR"])
            s.hundreds = int(bat["100s"]); s.fifties = int(bat["50s"])
            s.fours = int(bat["Fours"]); s.sixes = int(bat["Sixes"])
            s.wickets = int(bowl["Wickets"]); s.bowlingAverage = double(bowl["Avg"]); s.economy = double(bowl["Eco"])
            s.bestBowling = bowl["BBI"] ?? "-"; s.fiveWickets = int(bowl["5w"])
            out[header == headerT20 ? labelT20 : header] = s
        }
        return out
    }

    /// `{headers:["ROWHEADER","Test",…], values:[{values:["Matches","123",…]}]}`.
    private struct Table {
        var formats: [String]
        var rows: [String: [String]]

        func column(_ format: String) -> [String: String] {
            guard let i = formats.firstIndex(of: format) else { return [:] }
            return rows.compactMapValues { $0.indices.contains(i) ? $0[i] : nil }
        }
    }

    private static func table(_ any: Any?) -> Table? {
        guard let t = any as? [String: Any], let headers = (t["headers"] as? [Any])?.compactMap(string),
              headers.count >= 2, let values = t["values"] as? [Any] else { return nil }
        var rows: [String: [String]] = [:]
        for row in values {
            guard let cells = ((row as? [String: Any])?["values"] as? [Any])?.compactMap(string), let label = cells.first else { continue }
            rows[label] = Array(cells.dropFirst())
        }
        return Table(formats: Array(headers.dropFirst()), rows: rows)
    }

    /// `recentBatting.rows[].values` is `[matchId, "74(60)", "ENG", "ODI", "19 Jul 26"]`; the match id
    /// leads and is not shown, so cells are read from the end of the row.
    private static func recentForm(_ player: [String: Any]) -> [RecentInnings] {
        guard let rows = (player["recentBatting"] as? [String: Any])?["rows"] as? [Any] else { return [] }
        return rows.prefix(maxRecentInnings).compactMap { row in
            guard let cells = ((row as? [String: Any])?["values"] as? [Any])?.compactMap(string), cells.count >= 4,
                  let m = cells[cells.count - 4].firstMatch(of: #/^(\d+)\*?\s*(?:\((\d+)\))?/#),
                  let runs = Int(m.1) else { return nil }
            return RecentInnings(runs: runs, balls: m.2.flatMap { Int($0) } ?? 0, opponent: cells[cells.count - 3],
                                 format: cells[cells.count - 2], date: cells[cells.count - 1])
        }
    }

    /// Scalar to string; blank values and React back-references (`"$29"`) are treated as absent.
    private static func string(_ any: Any?) -> String? {
        let s: String?
        if let v = any as? String { s = v }
        else if let n = any as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() { s = n.stringValue }
        else { s = nil }
        guard let s, !s.trimmingCharacters(in: .whitespaces).isEmpty, !s.hasPrefix("$") else { return nil }
        return s
    }

    private static func int(_ s: String?) -> Int { Int(s?.trimmingCharacters(in: .whitespaces) ?? "") ?? 0 }
    private static func double(_ s: String?) -> Double { Double(s?.trimmingCharacters(in: .whitespaces) ?? "") ?? 0 }
}
