import Foundation
import Testing
@testable import Bouncer

private final class LockedDate: @unchecked Sendable {
    var value: Date
    init(_ value: Date) { self.value = value }
}

struct HighlightsTests {
    private func clip(_ home: String, _ away: String, league: String = "", date: String = "2026-10-18T00:00:00.000Z") -> Highlight {
        Highlight(id: "1", title: "t", url: URL(string: "https://www.youtube.com/watch?v=x")!, homeTeam: home, awayTeam: away,
                  leagueName: league, startDate: date)
    }

    private func target(_ t1: String, _ t2: String, title: String = "", day: String = "2026-10-18") -> HighlightTarget {
        HighlightTarget(team1: t1, team2: t2, title: title, startsAt: ISO8601DateFormatter().date(from: day + "T09:30:00Z"))!
    }

    @Test func decodesRealRowsAndDropsOnesWithoutAWebURL() throws {
        let list = try WireJSON.decode(RowList<Highlight>.self, from: Fixture.data("highlights-oct17-19.json"))
        #expect(list.rows.count == 3)
        let first = try #require(list.rows.first)
        #expect(first.id == "17061")
        #expect(first.homeTeam == "South Africa" && first.awayTeam == "Australia")
        #expect(first.channel == "Cricket Expert")
        #expect(first.imageURL?.host == "i.ytimg.com")
        let bad = #"{"rows":[{"highlight_id":"9","url":"javascript:alert(1)"},{"highlight_id":"10","url":"https://youtu.be/a"}]}"#
        #expect(try WireJSON.decode(RowList<Highlight>.self, from: bad).rows.map(\.id) == ["10"])
    }

    @Test func realClipsMatchOnlyTheirOwnFixture() throws {
        let rows = try WireJSON.decode(RowList<Highlight>.self, from: Fixture.data("highlights-oct17-19.json")).rows
        let test = HighlightMatcher.matching(rows, for: target("South Africa", "Australia", title: "Australia in South Africa 2nd Test"))
        #expect(test.map(\.id) == ["17061"])
        // The sides may be listed in either order.
        #expect(HighlightMatcher.matching(rows, for: target("Sri Lanka", "Pakistan", title: "Pakistan Triangular Series")).map(\.id) == ["17063"])
        // A men's fixture never takes the women's clip, nor the other way round.
        #expect(HighlightMatcher.matching(rows, for: target("Australia", "New Zealand")).isEmpty)
        #expect(HighlightMatcher.matching(rows, for: target("Australia Women", "New Zealand Women")).map(\.id) == ["17062"])
    }

    @Test func dayGapAboveOneVetoesTheMatch() {
        let c = clip("India", "England", league: "England tour of India")
        #expect(HighlightMatcher.score(c, for: target("India", "England", title: "England tour of India")) == 100)
        #expect(HighlightMatcher.score(c, for: target("India", "England", day: "2026-10-19")) > HighlightMatcher.threshold)
        #expect(HighlightMatcher.score(c, for: target("India", "England", day: "2026-10-20")) == 0)
        // An unparseable clip date carries no information, so it cannot veto.
        #expect(HighlightMatcher.score(clip("India", "England", date: ""), for: target("India", "England")) >= HighlightMatcher.threshold)
    }

    @Test func oneSharedTeamIsNotEnough() {
        #expect(HighlightMatcher.score(clip("Derbyshire", "Durham"), for: target("Durham", "Middlesex")) == 0)
        #expect(HighlightMatcher.score(clip("India", "England"), for: target("India Women", "England Women")) == 0)
    }

    @Test func queriesAskForThreeDaysSelectedColumnsAndACap() {
        let json = HighlightsRepository.queries(for: target("A", "B", day: "2026-03-01")).map(\.json)
        #expect(json.count == 5)
        #expect(json[0] == #"{"method":"equal","attribute":"match_start_date","values":["2026-02-28T00:00:00.000Z","2026-03-01T00:00:00.000Z","2026-03-02T00:00:00.000Z"]}"#)
        #expect(json[1] == #"{"method":"or","values":[{"method":"equal","attribute":"match_home_team","values":["A","B"]},{"method":"equal","attribute":"match_away_team","values":["A","B"]}]}"#)
        #expect(json[3] == #"{"method":"limit","values":[20]}"#)
        #expect(json[4].contains("\"highlight_id\"") && !json[3].contains("embed_url"))
    }

    @Test func cacheServesUntilTheTTLLapsesAndKeepsEmptyResults() async {
        let clock = LockedDate(Date(timeIntervalSince1970: 1_000))
        let cache = HighlightCache(now: { clock.value })
        let t = target("A", "B")
        #expect(await cache.clips(for: t) == nil)
        await cache.store([], for: t, ttl: 600)
        #expect(await cache.clips(for: t) == [])
        clock.value = Date(timeIntervalSince1970: 1_599)
        #expect(await cache.clips(for: t) == [])
        clock.value = Date(timeIntervalSince1970: 1_601)
        #expect(await cache.clips(for: t) == nil)
    }

    @Test func dayArithmeticRejectsImpossibleDates() {
        #expect(HighlightDays.dayNumber(iso: "2026-02-30T00:00:00.000Z") == nil)
        #expect(HighlightDays.daysBetween("2026-10-18T00:00:00.000Z", "2026-10-19") == 1)
        #expect(HighlightDays.string(forDayNumber: HighlightDays.dayNumber(iso: "2026-12-31")! + 1) == "2027-01-01")
    }

    @Test func tabsFollowAndroidPositions() {
        #expect(DetailTab.tabs(for: .live, graphs: true, highlights: true) == [.live, .scorecard, .commentary, .graphs, .squads, .info, .highlights])
        #expect(DetailTab.tabs(for: .finished, graphs: true, highlights: true) == [.summary, .scorecard, .commentary, .graphs, .highlights, .squads, .info])
        #expect(DetailTab.tabs(for: .finished) == [.summary, .scorecard, .commentary, .squads, .info])
        #expect(DetailTab.tabs(for: .upcoming, graphs: true, highlights: true) == [.info, .squads, .commentary])
    }
}

@MainActor
struct HighlightsStoreTests {
    private func list(_ name: String) throws -> Data { try Fixture.data(name) }

    private func finishedSummary() -> MatchSummary {
        MatchSummary(id: "1", title: "2nd Test", status: "Match drawn", state: "Complete", matchFormat: "TEST", seriesName: "Australia in South Africa",
                     startDate: "1792281600000", teams: [TeamScore(name: "South Africa"), TeamScore(name: "Australia")])
    }

    @Test func loadsOnceAndKeepsOnlyMatchingClips() async throws {
        let api = FakeAPI()
        await api.set(list: HighlightsRepository.table, try list("highlights-oct17-19.json"))
        let store = MatchDetailStore(matchId: "1", summary: finishedSummary(), api: api, realtime: FakeRealtime())
        let target = try #require(HighlightTarget(team1: "South Africa", team2: "Australia", title: "Australia in South Africa",
                                                  startsAt: Date(timeIntervalSince1970: 1_792_281_600)))
        await store.loadHighlights(for: target)
        await store.loadHighlights(for: target)
        #expect(store.highlights.map(\.id) == ["17061"])
        #expect(await api.listCalls.count == 1)
        #expect(await api.listCalls.first?.table == "highlights")
    }

    @Test func upcomingMatchesDoNotRead() async throws {
        let api = FakeAPI()
        let store = MatchDetailStore(matchId: "1", summary: MatchSummary(id: "1", state: "Preview"), api: api, realtime: FakeRealtime())
        await store.loadHighlights(for: HighlightTarget(team1: "A", team2: "B", title: "", startsAt: Date()))
        #expect(await api.listCalls.isEmpty)
    }
}
