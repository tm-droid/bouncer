import Foundation
import Testing
@testable import Bouncer

@MainActor
struct GraphsTests {
    private func card(_ name: String) throws -> Scorecard {
        try #require(try WireJSON.decode(ScorecardRow.self, from: Fixture.data(name)).decodeScorecard())
    }

    @Test func wormComesFromContiguousOversOfTheFirstTwoInnings() throws {
        let worm = try #require(OverWorm(overs: try card("scorecard-162753.json").overs))
        #expect(worm.team1Name == "PS-W")
        #expect(worm.team1Scores.first == 2 && worm.team1Scores[1] == 16)
        #expect(worm.perOver.count >= worm.team1Scores.count)
        // A card whose first plotted over is not over 1 has no worm: a hole cannot be drawn.
        #expect(OverWorm(overs: try card("scorecard-171297.json").overs) == nil)
    }

    @Test func missingOverEndsASeries() {
        func o(_ innings: Int, _ over: Int, _ score: Int) -> OverSummary {
            overSummary("{\"innings\":\(innings),\"over\":\(over),\"runs\":5,\"score\":\(score),\"team\":\"X\"}")
        }
        let worm = OverWorm(overs: [o(1, 1, 5), o(1, 2, 10), o(1, 4, 20), o(2, 1, 7), o(3, 1, 99)])
        #expect(worm?.team1Scores == [5, 10])
        #expect(worm?.team2Scores == [7])
        #expect(worm?.perOver.map(\.over) == [1, 2, 4])
        #expect(worm?.perOver.first?.team2Runs == 5)
    }

    @Test func wagonWheelsKeepOnlyConsistentWheels() throws {
        let c = try card("scorecard-171297.json")
        let first = WagonWheels.wheels(c.wagon, innings: 1)
        #expect(!first.isEmpty && first.allSatisfy { $0.zones.reduce(0, +) == $0.runs })
        var bad = WagonEntry.sample(name: "Bad", runs: 10, zones: [1, 1, 1, 1, 1, 1, 1, 1])
        #expect(WagonWheels.wheels([bad], innings: 1).isEmpty)
        bad.zones = [0, 0, 5, 5, 0, 0, 0, 0]
        #expect(WagonWheels.wheels([bad], innings: 1).count == 1)
        #expect(WagonWheels.weights(bad.zones) == [0, 0, 1, 1, 0, 0, 0, 0])
    }

    @Test func serverSeriesFollowsScreenOrderAndContinuesAcrossInnings() {
        func o(_ innings: Int, _ over: Int, _ p: Double?) -> String {
            "{\"innings\":\(innings),\"over\":\(over)\(p.map { ",\"p\":\($0)" } ?? "")}"
        }
        let card = try! WireJSON.decode(Scorecard.self, from: "{\"probabilityTeamId\":\"B\",\"overs\":[\([o(2, 1, 0.7), o(1, 1, 0.4), o(1, 2, nil), o(1, 3, 0.5)].joined(separator: ","))]}")
        let flipped = ProbabilityHistory.server(card, firstId: "A", secondId: "B")
        #expect(flipped.map(\.over) == [1, 3, 4])
        #expect(flipped.map(\.team1).map { ($0 * 10).rounded() } == [6, 5, 3])
        #expect(ProbabilityHistory.server(card, firstId: "B", secondId: "A").first?.team1 == 0.4)
        #expect(ProbabilityHistory.server(try! WireJSON.decode(Scorecard.self, from: "{}"), firstId: "A", secondId: "B").isEmpty)
    }

    @Test func mergedSeriesEndsOnTheSessionReading() {
        let server = [ProbabilityPoint(over: 1, team1: 0.5), ProbabilityPoint(over: 2, team1: 0.6)]
        let session = [ProbabilityPoint(over: 2.5, team1: 0.7)]
        #expect(ProbabilityHistory.merged(server: server, session: session).last == ProbabilityPoint(over: 2.5, team1: 0.7))
        #expect(ProbabilityHistory.merged(server: [server[0]], session: session) == session)
        #expect(ProbabilityHistory.merged(server: server, session: []) == server)
    }

    @Test func storeAccumulatesOneSessionPointPerBallCount() async throws {
        let api = FakeAPI()
        let live = try Fixture.data("live-detail-162753.json")
        await api.set(row: AppwriteConfig.Table.liveDetail, id: "162753", live)
        let store = MatchDetailStore(matchId: "162753", api: api, realtime: FakeRealtime(), presenceInterval: .seconds(3600))
        await store.refreshLive()
        await store.refreshLive()
        #expect(store.sessionProbability.count == 1)
        let p = try #require(store.sessionProbability.first)
        #expect(p.over == 115.0 / 6)
        #expect(abs(p.team1 - 0.18327753809181882) < 1e-9)
    }
}

private func overSummary(_ json: String) -> OverSummary { try! WireJSON.decode(OverSummary.self, from: json) }

private extension WagonEntry {
    static func sample(name: String, runs: Int, zones: [Int]) -> WagonEntry {
        let json = "{\"innings\":1,\"name\":\"\(name)\",\"runs\":\(runs),\"balls\":10,\"zones\":\(zones)}"
        return try! WireJSON.decode(WagonEntry.self, from: json)
    }
}
