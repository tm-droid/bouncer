import Foundation

/// One reading of the win-probability line: `team1` is the first team's share (0...1) after `over`
/// overs of the match, counted across innings.
struct ProbabilityPoint: Equatable, Sendable {
    var over: Double
    var team1: Double
}

enum ProbabilityHistory {
    /// True when the server's team1 is this screen's second team (ProbabilityHistory.kt, serverTeam1IsSecondTeam).
    static func isFlipped(serverTeam1Id: String?, firstId: String?, secondId: String?) -> Bool {
        guard let id = serverTeam1Id, !id.isEmpty, let first = firstId, !first.isEmpty, let second = secondId, !second.isEmpty else { return false }
        return id != first && id == second
    }

    /// The series `bouncer-match-sync` stamped on `overs[]`, in the screen's team order. Overs of a
    /// later innings continue after the last over of the one before.
    static func server(_ card: Scorecard?, firstId: String?, secondId: String?) -> [ProbabilityPoint] {
        guard let card, let teamId = card.probabilityTeamId, !teamId.isEmpty else { return [] }
        let flip = isFlipped(serverTeam1Id: teamId, firstId: firstId, secondId: secondId)
        var offset = 0, lastOver = 0
        var innings = 0
        var points: [ProbabilityPoint] = []
        for o in card.overs.sorted(by: { ($0.innings, $0.over) < ($1.innings, $1.over) }) {
            if o.innings != innings {
                offset += lastOver
                innings = o.innings
            }
            lastOver = o.over
            guard let p = o.probability else { continue }
            points.append(ProbabilityPoint(over: Double(offset + o.over), team1: min(max(flip ? 1 - p : p, 0), 1)))
        }
        return points
    }

    /// The server's series ending on this session's latest reading, which is newer than the last
    /// completed over; with no server series the session's own.
    static func merged(server: [ProbabilityPoint], session: [ProbabilityPoint]) -> [ProbabilityPoint] {
        guard server.count >= 2 else { return session }
        guard let live = session.last, let end = server.last else { return server }
        return server + [ProbabilityPoint(over: end.over + 0.5, team1: live.team1)]
    }
}

/// Both innings of a limited-overs match as the worm and the run-rate bars draw them (OverWorm.kt).
/// Scores are cumulative for overs 1...n with no gaps: a missing over ends the series there.
struct OverWorm: Equatable {
    struct Over: Equatable {
        var over: Int
        var team1Runs: Int
        var team2Runs: Int?
    }

    var team1Name: String
    var team2Name: String
    var team1Scores: [Int]
    var team2Scores: [Int]
    var perOver: [Over]

    /// Nil when the first innings has no plottable over 1. Innings 3+ (a Test) are not a worm.
    init?(overs: [OverSummary]) {
        let first = Dictionary(overs.filter { $0.innings == 1 }.map { ($0.over, $0) }, uniquingKeysWith: { _, new in new })
        let second = Dictionary(overs.filter { $0.innings == 2 }.map { ($0.over, $0) }, uniquingKeysWith: { _, new in new })
        func contiguous(_ byOver: [Int: OverSummary]) -> [Int] {
            var scores: [Int] = []
            while let o = byOver[scores.count + 1] { scores.append(o.score) }
            return scores
        }
        team1Scores = contiguous(first)
        guard !team1Scores.isEmpty else { return nil }
        team2Scores = contiguous(second)
        team1Name = first.values.sorted { $0.over < $1.over }.first(where: { !$0.team.isEmpty })?.team ?? ""
        team2Name = second.values.sorted { $0.over < $1.over }.first(where: { !$0.team.isEmpty })?.team ?? ""
        perOver = Set(first.keys).union(second.keys).sorted().compactMap { n in
            first[n].map { Over(over: n, team1Runs: $0.runs, team2Runs: second[n]?.runs) }
        }
    }
}

/// Wagon wheels (WagonWheel.kt): eight zones clockwise from the top, bowler's end at the bottom.
enum WagonWheels {
    static let zoneCount = 8

    /// The innings' batters who scored. A wheel whose zones are not eight, or do not add up to its
    /// runs, is left out: it would be a picture of something else.
    static func wheels(_ wagon: [WagonEntry], innings: Int) -> [WagonEntry] {
        wagon.filter { w in
            w.innings == innings && w.runs > 0 && !w.name.trimmingCharacters(in: .whitespaces).isEmpty
                && w.zones.count == zoneCount && w.zones.allSatisfy { $0 >= 0 } && w.zones.reduce(0, +) == w.runs
        }
    }

    /// Fill strength of each zone, 0...1 against the batter's busiest zone.
    static func weights(_ zones: [Int]) -> [Double] {
        guard let most = zones.max(), most > 0 else { return zones.map { _ in 0 } }
        return zones.map { Double($0) / Double(most) }
    }

    /// Where a zone sits, in words, for a reader who cannot see the wheel.
    static let zoneWords = [
        "behind the wicket, right", "square, right", "wide of straight, right", "straight, right",
        "straight, left", "wide of straight, left", "square, left", "behind the wicket, left",
    ]
}

/// Everything the Graphs tab draws, derived from the store. A tab with nothing to draw is not offered.
struct GraphsModel: Equatable {
    struct InningsWheels: Equatable, Identifiable {
        var id: Int
        var team: String
        var wheels: [WagonEntry]
    }

    var probability: [ProbabilityPoint] = []
    var worm: OverWorm?
    var requiredRunRate: Double?
    /// Newest innings first: during a chase the chase is what is being read.
    var wheels: [InningsWheels] = []

    @MainActor
    init(store: MatchDetailStore) {
        let card = store.scorecard
        let ids = store.teamIds
        probability = ProbabilityHistory.merged(server: ProbabilityHistory.server(card, firstId: ids.first, secondId: ids.second),
                                                session: store.sessionProbability)
        worm = card.flatMap { OverWorm(overs: $0.overs) }
        requiredRunRate = store.phase == .live ? store.live?.requiredRunRate.trimmingCharacters(in: .whitespaces).nonEmptyDouble : nil
        if let card {
            wheels = card.innings.indices.reversed().compactMap { i in
                let w = WagonWheels.wheels(card.wagon, innings: i + 1)
                return w.isEmpty ? nil : InningsWheels(id: i + 1, team: card.innings[i].teamName, wheels: w)
            }
        }
    }

    var hasWinProbability: Bool { probability.count >= 2 }
    var isEmpty: Bool { !hasWinProbability && worm == nil && wheels.isEmpty }
}

private extension String {
    var nonEmptyDouble: Double? { Double(self).flatMap { $0 > 0 ? $0 : nil } }
}
