import Foundation

/// One date heading on a series' matches page ("Fri, 25 Sep 2026") and the matches under it.
struct SeriesMatchDay: Equatable, Sendable {
    var label: String
    var matches: [MatchSummary]
}

/// One row of a points table. `form` is oldest first, as Cricbuzz writes it.
struct PointsRow: Equatable, Sendable {
    var teamId: String
    var name: String
    var shortName: String
    var imageId: String
    var played: Int
    var won: Int
    var lost: Int
    var tied: Int
    var noResult: Int
    var nrr: Double
    var points: Int
    var form: [String]
    var qualified: Bool

    var tiedOrNoResult: Int { tied + noResult }
    /// Cricbuzz's crest for this side (`teamImageId`), as Android's `cricbuzzTeamLogoUrl`.
    var logoURL: URL? {
        imageId.isEmpty ? nil : URL(string: "https://static.cricbuzz.com/a/img/v1/72x72/i1/c\(imageId)/i.jpg")
    }
}

/// A pool or stage of a points table ("Pool A", "Super Eights"); a plain league is one group.
struct PointsGroup: Equatable, Sendable {
    var name: String
    var rows: [PointsRow]
}

/// One entry in a series' stat-type menu, e.g. `mostRuns` / "Most Runs" / "Batting".
struct SeriesStatType: Equatable, Sendable {
    var value: String
    var header: String
    var category: String
}

/// A player's line in a stats table: the id is empty when Cricbuzz sends none.
struct SeriesStatsRow: Equatable, Sendable {
    var playerId: String
    var playerName: String
    /// One cell per header after "PLAYER".
    var cells: [String]
}

/// One format's table for one stat type.
struct SeriesStatsTable: Equatable, Sendable {
    /// "ODI", "T20", "TEST" from the payload's `{format}StatsList` key.
    var format: String
    /// Column headers, starting with "PLAYER".
    var headers: [String]
    var rows: [SeriesStatsRow]
}

/// Everything one `/stats?statsType=X` page carries: the stat-type menu and a table per format, so a
/// format switch costs no fetch.
struct SeriesStats: Equatable, Sendable {
    var types: [SeriesStatType]
    var selectedType: String
    var tables: [SeriesStatsTable]
    /// The format Cricbuzz shows first, when it names one the tables carry.
    var defaultFormat: String?

    static let empty = SeriesStats(types: [], selectedType: "", tables: [], defaultFormat: nil)
}
