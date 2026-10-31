import Foundation

/// The public configuration is bundled directly from ../shared/backend.json.
struct SharedBackendConfiguration: Decodable {
    let endpoint: String
    let projectId: String
    let databaseId: String
    let tables: [String: String]
    let matchesBucketId: String
    let apiFunctionId: String

    static func bundled() throws -> Self {
        guard let url = Bundle.main.url(forResource: "backend", withExtension: "json") else {
            throw SharedBackendError.configuration
        }
        return try JSONDecoder().decode(Self.self, from: Data(contentsOf: url))
    }
}

enum SharedBackendError: LocalizedError {
    case configuration, invalidResponse, http(Int), paused
    var errorDescription: String? {
        switch self {
        case .configuration: "The match service configuration is unavailable."
        case .invalidResponse: "The match service returned an unreadable response."
        case .http(402): "The match service has reached its usage limit. Saved scores remain available."
        case .http(429): "The match service is busy. Please try again shortly."
        case .http(let code): "The match service is unavailable (\(code))."
        case .paused: "Updates are paused briefly to limit service usage. Saved scores remain available."
        }
    }
}

enum MatchListKind: String, CaseIterable, Identifiable, Codable {
    case live, upcoming, recent
    var id: String { rawValue }
    var title: String { rawValue.capitalized }
    var ttl: TimeInterval { self == .live ? 30 : 600 }
}

struct SharedMatch: Codable, Identifiable, Hashable {
    let id: String
    let title: String
    let status: String
    var state: String?
    var matchFormat: String?
    var seriesName: String?
    var matchDesc: String?
    var startDate: String?
    var teams: [SharedTeam]?
    var isTerminal: Bool { ["complete", "completed", "result", "terminal", "abandoned", "cancelled"].contains(state?.lowercased() ?? "") }
    var isLive: Bool { ["live", "in progress", "innings break"].contains(state?.lowercased() ?? "") }
}
struct SharedTeam: Codable, Hashable {
    var teamId: String?
    var name: String?
    var score: String?
    var overs: String?
    var isBatting: Bool?
}
struct SharedMatchList: Codable {
    struct Matches: Codable { let matches: [SharedMatch] }
    let data: Matches
    let fetchedAt: Double
    func isStale(_ kind: MatchListKind, now: Date = Date()) -> Bool {
        now.timeIntervalSince1970 - fetchedAt / 1000 > kind.ttl
    }
}

/// Preserve the server's sections without inventing missing score or probability values.
/// JSON values also allow old rows and additive parser fields to coexist.
enum SharedJSON: Codable, Equatable {
    case object([String: SharedJSON]), array([SharedJSON]), string(String), number(Double), bool(Bool), null
    init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer()
        if value.decodeNil() { self = .null }
        else if let v = try? value.decode(Bool.self) { self = .bool(v) }
        else if let v = try? value.decode(Double.self) { self = .number(v) }
        else if let v = try? value.decode(String.self) { self = .string(v) }
        else if let v = try? value.decode([SharedJSON].self) { self = .array(v) }
        else { self = .object(try value.decode([String: SharedJSON].self)) }
    }
    func encode(to encoder: Encoder) throws {
        var value = encoder.singleValueContainer()
        switch self {
        case .object(let v): try value.encode(v)
        case .array(let v): try value.encode(v)
        case .string(let v): try value.encode(v)
        case .number(let v): try value.encode(v)
        case .bool(let v): try value.encode(v)
        case .null: try value.encodeNil()
        }
    }
    subscript(_ key: String) -> SharedJSON { if case .object(let v) = self { return v[key] ?? .null }; return .null }
    var text: String {
        switch self { case .string(let v): return v; case .number(let v): return v.formatted(.number.precision(.fractionLength(0...2))); default: return "" }
    }
    var number: Double? { if case .number(let v) = self { return v }; if case .string(let v) = self { return Double(v) }; return nil }
    var array: [SharedJSON] { if case .array(let v) = self { return v }; return [] }
    static func parse(_ raw: String) throws -> Self { try JSONDecoder().decode(Self.self, from: Data(raw.utf8)) }
}
struct SharedAvailability: Codable {
    let available: Bool
    let fetchedAt: Double?
    let stale: Bool
}
struct SharedMatchEnvelope: Codable {
    let schemaVersion: Int
    let matchId: String
    var state: String?
    var summary: SharedMatch?
    var detail: SharedJSON?
    var scorecard: SharedJSON?
    var meta: SharedJSON?
    var commentary: [SharedJSON]?
    var fetchedAt: Double?
    var stale: Bool
    let retryAfterSeconds: Int
    var availability: [String: SharedAvailability]
}
struct SharedStoredRow: Codable {
    var match_id: String?
    var payload: String?
    var scorecard: String?
    var updated_at: SharedJSON?
}
struct SharedCommentary: Codable, Identifiable {
    let commentary: String
    let sort_key: Double
    var ball: String?
    var innings: Int?
    var match_id: String?
    var source: String?
    var ai_generated: Bool?
    var id: Double { sort_key }
}

extension SharedMatchEnvelope {
    /// A missing or failed section never erases a last-good section or refreshes its timestamp.
    func preservingLastGood(_ previous: SharedMatchEnvelope?) -> SharedMatchEnvelope {
        guard let previous, previous.matchId == matchId else { return self }
        var result = self
        func retained(_ key: String) {
            let stamp = previous.availability[key]?.fetchedAt
            result.availability[key] = SharedAvailability(available: true, fetchedAt: stamp, stale: true)
            result.stale = true
        }
        if result.detail == nil, previous.detail != nil { result.detail = previous.detail; retained("detail") }
        if result.scorecard == nil, previous.scorecard != nil { result.scorecard = previous.scorecard; retained("scorecard") }
        if result.meta == nil, previous.meta != nil { result.meta = previous.meta; retained("meta") }
        if result.commentary == nil, previous.commentary != nil { result.commentary = previous.commentary; retained("commentary") }
        return result
    }
}

extension SharedMatchEnvelope {
    func applyingDetail(_ detail: SharedJSON, fetchedAt: Double?, now: Date = Date(), ttl: TimeInterval) -> SharedMatchEnvelope {
        var result = self
        result.detail = detail
        result.fetchedAt = fetchedAt
        result.availability["detail"] = SharedAvailability(available: true, fetchedAt: fetchedAt,
            stale: fetchedAt.map { now.timeIntervalSince1970 - $0 / 1000 > ttl } ?? true)
        for (key, section) in result.availability where key != "detail" && section.available {
            result.availability[key] = SharedAvailability(available: true, fetchedAt: section.fetchedAt,
                stale: section.stale || section.fetchedAt.map { now.timeIntervalSince1970 - $0 / 1000 > ttl } ?? true)
        }
        result.stale = result.availability.values.contains { $0.available && $0.stale }
        return result
    }
}

struct SharedScorecardSnapshot {
    let value: SharedJSON
    let fetchedAt: Double?
    let stale: Bool
}

struct SharedMetadataRow: Identifiable {
    let label: String
    let value: String
    var id: String { label }
    static func rows(_ meta: SharedJSON?) -> [Self] {
        guard let meta else { return [] }
        var rows: [Self] = []
        let venue = ["name", "city", "country"].map { meta["venue"][$0].text }.filter { !$0.isEmpty }.joined(separator: ", ")
        if !venue.isEmpty { rows.append(Self(label: "Venue", value: venue)) }
        let toss = [meta["toss"]["winnerName"].text, meta["toss"]["decision"].text].filter { !$0.isEmpty }.joined(separator: " · ")
        if !toss.isEmpty { rows.append(Self(label: "Toss", value: toss)) }
        if let milliseconds = meta["startMs"].number {
            rows.append(Self(label: "Starts", value: Date(timeIntervalSince1970: milliseconds / 1000).formatted(date: .abbreviated, time: .shortened)))
        }
        return rows
    }
}

extension SharedCommentary {
    static func fromRaw(_ raw: SharedJSON, matchId: String) -> Self? {
        guard let timestamp = raw["timestamp"].number, !raw["commText"].text.isEmpty else { return nil }
        let label = raw["ballMetric"]["label"].text
        return Self(commentary: raw["commText"].text, sort_key: timestamp,
            ball: label.isEmpty ? nil : label, innings: raw["innings"].number.map(Int.init), match_id: matchId,
            source: raw["source"].text.isEmpty ? nil : raw["source"].text, ai_generated: false)
    }
    static func merging(table: [Self], raw: [Self]) -> [Self] {
        var rows = Dictionary(raw.map { ($0.id, $0) }, uniquingKeysWith: { _, new in new })
        for line in table { rows[line.id] = line }
        return rows.values.sorted { $0.sort_key > $1.sort_key }
    }
}
