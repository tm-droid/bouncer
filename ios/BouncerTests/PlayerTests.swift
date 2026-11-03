import Foundation
import Testing
@testable import Bouncer

struct PlayerTests {
    @Test func searchDecodesAndDropsRowsWithoutId() throws {
        let r = try WireJSON.decode(PlayerSearchResponse.self, from: try Fixture.data("player-search-sharma.json"))
        #expect(r.players.map(\.id) == ["12086", "576"])
        #expect(r.players[0].country == "India")
        #expect(r.players[0].imageURL == nil)
    }

    @Test func bestMatchPrefersExactNameThenFirst() throws {
        let r = try WireJSON.decode(PlayerSearchResponse.self, from: try Fixture.data("player-search-sharma.json")).players
        #expect(PlayerRepository.bestMatch(for: "rohit sharma", in: r)?.id == "576")
        #expect(PlayerRepository.bestMatch(for: "R Sharma", in: r)?.id == "12086")
        #expect(PlayerRepository.bestMatch(for: "x", in: []) == nil)
    }

    @Test func profileFromTableRow() throws {
        let row = try Fixture.data("player-profile-row.json")
        let now = ISO8601DateFormatter().date(from: "2026-10-25T00:00:00Z")!
        let p = try #require(PlayerProfile(row: row, id: "1413", now: now))
        #expect(p.name == "Virat Kohli" && p.country == "India" && p.role == "Batsman")
        #expect(p.formatsWithStats == ["Test", "ODI", "T20I", "IPL"])
        #expect(p.stats["Test"]?.runs == 9230)
        #expect(p.stats["ODI"]?.highScore == "183")
        #expect(p.stats["Test"]?.hasBowling == false)
        // Older than a week: not offered.
        #expect(PlayerProfile(row: row, id: "1413", now: now.addingTimeInterval(30 * 86_400)) == nil)
    }

    @Test func rowWithoutStatsIsRejected() {
        let row = Data(#"{"name":"A","stats_json":"{}"}"#.utf8)
        #expect(PlayerProfile(row: row, id: "1") == nil)
    }

    @Test func parsesFlightProfile() throws {
        let p = try #require(PlayerProfileParser.parse(html: try Fixture.string("player-profile.html"), id: "1413", fallbackName: "x"))
        #expect(p.name == "Virat Kohli" && p.country == "India" && p.role == "Batsman")
        #expect(p.dateOfBirth == "November 05, 1988")
        #expect(p.battingStyle == "Right Handed Bat" && p.bowlingStyle == "Right-arm medium")
        #expect(p.imageURL?.absoluteString == "https://www.cricbuzz.com/a/img/v1/152x152/i1/c616517/player.jpg")
        #expect(p.bio.isEmpty)  // "$29" is a back-reference, not prose
        #expect(p.teams.first == "India" && p.teams.count == 10)
        #expect(p.formatsWithStats == ["Test", "ODI", "T20I", "IPL"])
        let test = try #require(p.stats["Test"])
        #expect(test.matches == 123 && test.innings == 210 && test.runs == 9230 && test.highScore == "254")
        #expect(test.average == 46.85 && test.strikeRate == 55.58 && test.hundreds == 30 && test.fifties == 31)
        #expect(test.fours == 1027 && test.sixes == 30)
        #expect(p.stats["T20I"]?.runs == 4188)
    }

    @Test func parsesRecentForm() throws {
        let p = try #require(PlayerProfileParser.parse(html: try Fixture.string("player-profile.html"), id: "1413", fallbackName: "x"))
        #expect(p.recentForm.count == 5)
        #expect(p.recentForm[0] == RecentInnings(runs: 0, balls: 1, opponent: "WI", format: "ODI", date: "03 Oct 26"))
        #expect(p.recentForm[2].runs == 139 && p.recentForm[2].balls == 88)
    }

    @Test func pageWithoutPlayerYieldsNil() {
        #expect(PlayerProfileParser.parse(html: "<html>404</html>", id: "1", fallbackName: "x") == nil)
    }

    @Test func bornLineAndTrend() {
        let today = ISO8601DateFormatter().date(from: "2026-11-05T12:00:00Z")!
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        #expect(PlayerForm.bornLine("November 05, 1988 (37 years)", today: today, calendar: cal) == "5 Nov 1988 · 38")
        #expect(PlayerForm.bornLine("sometime", today: today, calendar: cal) == "sometime")
        #expect(PlayerForm.trend([80, 70, 60, 10, 10, 10]) == "Rising")
        #expect(PlayerForm.trend([5, 5, 5, 50, 50, 50]) == "Declining")
        #expect(PlayerForm.trend([30, 30, 30]) == "Stable")
        #expect(PlayerForm.trend([]) == "Stable")
    }

    @Test func profileURLSlug() {
        #expect(PlayerRepository.profileURL(id: "1413", name: "Virat Kohli").absoluteString == "https://www.cricbuzz.com/profiles/1413/virat-kohli")
    }

    @Test func nameOnlyResolvesViaSearchThenTable() async throws {
        let api = FakeAPI()
        await api.set(execution: "/player-search", try Fixture.data("player-search.json"))
        await api.set(row: "player-profiles", id: "cb_1413", try Fixture.data("player-profile-row.json"))
        // The fixture row was saved 2026-10-23; read it a day later so it is inside the 7-day window.
        let repo = PlayerRepository(api: api, fetcher: { _ in throw URLError(.notConnectedToInternet) },
                                    now: { ISO8601DateFormatter().date(from: "2026-10-24T00:00:00Z")! })
        let p = try await repo.profile(id: nil, name: "Virat Kohli")
        #expect(p.id == "1413" && p.stats["ODI"] != nil)
        _ = try await repo.profile(id: "1413", name: "Virat Kohli")
        #expect(await api.rowCalls == ["player-profiles/cb_1413"])
        #expect(await api.executeCalls.count == 1)
        #expect(await api.executeCalls[0].body == #"{"q":"virat kohli"}"#)
    }

    @Test func missingRowFallsBackToScrape() async throws {
        let api = FakeAPI()
        let html = try Fixture.string("player-profile.html")
        let repo = PlayerRepository(api: api, fetcher: { url in
            #expect(url.absoluteString == "https://www.cricbuzz.com/profiles/1413/virat-kohli")
            return html
        })
        let p = try await repo.profile(id: "1413", name: "Virat Kohli")
        #expect(p.recentForm.count == 5)
        #expect(await api.executeCalls.isEmpty)
    }

    @Test func unknownNameThrowsNotFound() async throws {
        let api = FakeAPI()
        let repo = PlayerRepository(api: api, fetcher: { _ in "" })
        await #expect(throws: AppwriteError.notFound) { try await repo.profile(id: nil, name: "Nobody") }
    }
}
