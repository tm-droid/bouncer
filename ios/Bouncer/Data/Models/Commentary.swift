import Foundation

/// The server's verdict on a delivery, classified from the raw text before it was rewritten.
/// `nil` on `CommentaryEntry.event` means the server had no opinion; `.none` means an ordinary ball.
enum CommentaryEventKind: String, Sendable, Equatable, Hashable {
    case wicket = "WICKET"
    case six = "SIX"
    case four = "FOUR"
    case none = "NONE"

    static let wicketFilter = ["WICKET"]
    static let boundaryFilter = ["FOUR", "SIX"]
}

/// One `match-commentary` row. Also the Realtime payload shape for that table.
struct CommentaryEntry: Decodable, Equatable, Hashable, Sendable, Identifiable {
    var matchId: String = ""
    var innings: Int?
    /// "18.5", or "Ball 72" for The Hundred.
    var ball: String?
    var sortKey: Int64 = 0
    var text: String = ""
    var event: CommentaryEventKind?
    var runs: Int?
    /// WIDE / NOBALL / BYE / LEGBYE, nil when none or unknown.
    var extra: String?
    var provider: String?

    var id: Int64 { sortKey }

    init(matchId: String = "", innings: Int? = nil, ball: String? = nil, sortKey: Int64 = 0, text: String = "",
         event: CommentaryEventKind? = nil, runs: Int? = nil, extra: String? = nil, provider: String? = nil) {
        self.matchId = matchId; self.innings = innings; self.ball = ball; self.sortKey = sortKey
        self.text = text; self.event = event; self.runs = runs; self.extra = extra; self.provider = provider
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        matchId = w.string("match_id") ?? ""
        innings = w.int("innings")
        ball = w.string("ball").flatMap { $0.isEmpty ? nil : $0 }
        sortKey = w.int64("sort_key") ?? 0
        text = w.string("commentary") ?? ""
        event = w.string("event").flatMap(CommentaryEventKind.init(rawValue:))
        runs = w.int("runs")
        extra = w.string("extra").flatMap { $0.isEmpty ? nil : $0 }
        provider = w.string("provider")
    }

    var isWicket: Bool { event == .wicket }
    var isBoundary: Bool { event == .four || event == .six }
}

/// `{total, rows:[...]}` list response. Rows that fail to decode are skipped.
struct RowList<Row: Decodable>: Decodable {
    var total: Int = 0
    var rows: [Row] = []

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        total = w.int("total") ?? 0
        rows = w.array("rows")
    }
}

enum CommentaryFilter: String, Sendable, CaseIterable {
    case all, wickets, boundaries

    /// Values for the `event` equality query; nil for `.all`.
    var eventValues: [String]? {
        switch self {
        case .all: return nil
        case .wickets: return CommentaryEventKind.wicketFilter
        case .boundaries: return CommentaryEventKind.boundaryFilter
        }
    }

    func matches(_ entry: CommentaryEntry) -> Bool {
        switch self {
        case .all: return true
        case .wickets: return entry.isWicket
        case .boundaries: return entry.isBoundary
        }
    }
}
