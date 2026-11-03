import Foundation
import Testing
@testable import Bouncer

struct WidgetTests {
    private func live() throws -> [WidgetMatch] { WidgetSnapshotDecoder.decode(try Fixture.data("live-matches.json")) }
    private func upcoming() throws -> [WidgetMatch] { WidgetSnapshotDecoder.decode(try Fixture.data("upcoming-matches.json")) }

    // MARK: decoder

    @Test func decodesListFile() throws {
        let matches = try live()
        #expect(matches.count == 6)
        let test = try #require(matches.first(where: { $0.id == "171297" }))
        #expect(test.format == "TEST")
        #expect(test.series == "India A tour of New Zealand, 2026")
        #expect(test.teams[0].score == "346 & 32/0")
        #expect(test.teams[1].name == "New Zealand A")
        #expect(test.startsAt == Date(timeIntervalSince1970: 1793397600))
        #expect(test.phase == .live)
        #expect(try upcoming().allSatisfy { $0.phase == .upcoming })
    }

    @Test func decoderToleratesGarbage() {
        #expect(WidgetSnapshotDecoder.decode(Data("nope".utf8)).isEmpty)
        #expect(WidgetSnapshotDecoder.decode(Data(#"{"data":{"matches":[{"title":"no id"},{"id":7,"teams":[{"name":"A","score":12}]}]}}"#.utf8))
            .map(\.id) == ["7"])
    }

    // MARK: selection

    @Test func liveFavouriteBeatsOtherLive() throws {
        let m = WidgetMatchSelector.select(live: try live(), upcoming: try upcoming(), favourites: ["Oman"])
        #expect(m?.id == "176364")
    }

    @Test func favouriteMatchesByInitials() throws {
        let m = WidgetMatchSelector.select(live: try live(), upcoming: [], favourites: ["PSW"])
        #expect(m?.id == "162753")
    }

    @Test func anyLiveWhenNoFavouriteLive() throws {
        let m = WidgetMatchSelector.select(live: try live(), upcoming: try upcoming(), favourites: ["Chitwan Rhinos"])
        #expect(m?.id == "171297")
    }

    @Test func upcomingFavouriteThenEarliestUpcoming() throws {
        let up = try upcoming()
        #expect(WidgetMatchSelector.select(live: [], upcoming: up, favourites: ["Janakpur Bolts"])?.id == "177690")
        #expect(WidgetMatchSelector.select(live: [], upcoming: up.reversed(), favourites: [])?.id == "177681")
    }

    @Test func finishedAndEmptyYieldNothing() throws {
        let finished = WidgetMatch(id: "1", status: "X won by 5 runs", state: "Complete", teams: [])
        #expect(WidgetMatchSelector.select(live: [finished], upcoming: [], favourites: []) == nil)
        #expect(WidgetMatchSelector.select(live: [], upcoming: [], favourites: ["India"]) == nil)
    }

    @Test func deepLink() {
        #expect(WidgetBackend.matchURL("123").absoluteString == "bouncer://match/123")
    }

    // MARK: Live Activity mapping

    private func summary() throws -> MatchSummary {
        let file = try WireJSON.decode(MatchListFile.self, from: Fixture.data("live-matches.json"))
        return try #require(file.matches.first(where: { $0.id == "162753" }))
    }

    @Test func attributesAndInitialState() throws {
        let s = try summary()
        let attrs = try #require(LiveActivityController.attributes(for: s))
        #expect(attrs.matchId == "162753")
        #expect(attrs.team1Id == "360" && attrs.team2Id == "359")
        let state = LiveActivityController.state(for: s)
        #expect(state.team1Score == "124/9" && state.team2Score == "72/4")
        #expect(state.phase == .live)
        #expect(LiveActivityController.attributes(for: MatchSummary(id: "x")) == nil)
    }

    @Test func payloadSlotsAlignByTeamId() throws {
        let s = try summary()
        let attrs = try #require(LiveActivityController.attributes(for: s))
        let row = try WireJSON.decode(LiveDetailRow.self, from: Fixture.data("live-detail-162753.json"))
        let payload = try #require(row.decodePayload())
        let state = LiveActivityController.state(from: payload, attributes: attrs, previous: LiveActivityController.state(for: s))
        // payload team1 (id 360) is the activity's side 1; team2 is absent so side 2 keeps its score.
        #expect(state.team1Score == "121/9" && state.team1Overs == "19.1")
        #expect(state.team2Score == "72/4")
        #expect(state.battingSide == 1)
        #expect(state.recentBalls == ["W"])
        #expect(state.statusLine == "Melbourne Stars Women opt to bowl")
    }

    @Test func swappedSlotsMapToTheOtherSide() throws {
        var attrs = try #require(LiveActivityController.attributes(for: try summary()))
        swap(&attrs.team1Id, &attrs.team2Id)   // side 1 is now id 359, so payload team1 (360) is side 2
        let row = try WireJSON.decode(LiveDetailRow.self, from: Fixture.data("live-detail-162753.json"))
        let state = LiveActivityController.state(from: try #require(row.decodePayload()), attributes: attrs, previous: .init())
        #expect(state.team2Score == "121/9")
        #expect(state.team1Score == "")
        #expect(state.battingSide == 2)
    }

    @Test func finishedPayloadClearsBattingSide() throws {
        let attrs = try #require(LiveActivityController.attributes(for: try summary()))
        var payload = LiveDetailPayload()
        payload.status = "Perth Scorchers Women won by 10 runs"
        var previous = LiveActivityController.state(for: try summary())
        previous.battingSide = 2
        let state = LiveActivityController.state(from: payload, attributes: attrs, previous: previous)
        #expect(state.phase == .finished)
        #expect(state.battingSide == 0)
        #expect(state.team1Score == "124/9")
    }
}
