import Foundation

/// Appwrite query, encoded to the JSON string the REST API expects in `queries[]`.
/// Key order matches the official SDKs: method, attribute, values.
enum Query: Equatable, Sendable {
    case equal(String, [String])
    case lessThan(String, Int64)
    case greaterThan(String, Int64)
    case orderDesc(String)
    case orderAsc(String)
    case limit(Int)
    case select([String])
    /// Matches when any nested query matches.
    case or([Query])

    var json: String {
        switch self {
        case let .equal(attr, values):
            return Self.build("equal", attr, values.map(WireJSON.quote))
        case let .lessThan(attr, v):
            return Self.build("lessThan", attr, [String(v)])
        case let .greaterThan(attr, v):
            return Self.build("greaterThan", attr, [String(v)])
        case let .orderDesc(attr):
            return "{\"method\":\"orderDesc\",\"attribute\":\(WireJSON.quote(attr))}"
        case let .orderAsc(attr):
            return "{\"method\":\"orderAsc\",\"attribute\":\(WireJSON.quote(attr))}"
        case let .limit(n):
            return "{\"method\":\"limit\",\"values\":[\(n)]}"
        case let .select(cols):
            return "{\"method\":\"select\",\"values\":[\(cols.map(WireJSON.quote).joined(separator: ","))]}"
        case let .or(queries):
            return "{\"method\":\"or\",\"values\":[\(queries.map(\.json).joined(separator: ","))]}"
        }
    }

    private static func build(_ method: String, _ attr: String, _ values: [String]) -> String {
        "{\"method\":\"\(method)\",\"attribute\":\(WireJSON.quote(attr)),\"values\":[\(values.joined(separator: ","))]}"
    }
}

/// The `match-commentary` queries the app issues. Pure functions, tested against Android's shapes.
enum CommentaryQueries {
    static let firstPageLimit = 48
    static let pageLimit = 150
    static let eventPageLimit = 60
    static let columns = ["match_id", "innings", "ball", "sort_key", "commentary", "event", "runs", "extra", "provider"]

    /// Newest-first feed page. First page is small (reads bill per row); older pages are deeper.
    static func feed(matchId: String, olderThan: Int64? = nil) -> [Query] {
        var q: [Query] = [.equal("match_id", [matchId])]
        if let olderThan { q.append(.lessThan("sort_key", olderThan)) }
        q.append(.orderDesc("sort_key"))
        q.append(.limit(olderThan == nil ? firstPageLimit : pageLimit))
        q.append(.select(columns))
        return q
    }

    /// Rows strictly newer than `newerThan`: what the feed missed while its socket was down.
    static func catchUp(matchId: String, newerThan: Int64) -> [Query] {
        [.equal("match_id", [matchId]), .greaterThan("sort_key", newerThan),
         .orderDesc("sort_key"), .limit(pageLimit), .select(columns)]
    }

    /// One page of wickets (`["WICKET"]`) or boundaries (`["FOUR","SIX"]`).
    static func events(matchId: String, events: [String], olderThan: Int64? = nil) -> [Query] {
        var q: [Query] = [.equal("match_id", [matchId]), .equal("event", events)]
        if let olderThan { q.append(.lessThan("sort_key", olderThan)) }
        q.append(.orderDesc("sort_key"))
        q.append(.limit(eventPageLimit))
        q.append(.select(columns))
        return q
    }
}
