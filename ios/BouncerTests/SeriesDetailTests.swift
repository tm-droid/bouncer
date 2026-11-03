import Foundation
import Testing
@testable import Bouncer

struct SeriesDetailTests {
    // MARK: slug

    @Test func slugReadFromLandingLink() throws {
        let html = try Fixture.string("series-landing.html")
        #expect(SeriesSlug.of(html: html, seriesId: "13180") == "csa-t20-challenge-2026")
        #expect(SeriesSlug.of(html: html, seriesId: "999") == nil)
    }

    @Test func unsluggedLinkIsNotASlug() {
        #expect(SeriesSlug.of(html: #"<a href="/cricket-series/13180/matches">"#, seriesId: "13180") == nil)
        #expect(SeriesSlug.of(html: #"href="/cricket-series/11595/australia-tour-of-south-africa-2026/stats""#, seriesId: "11595")
                == "australia-tour-of-south-africa-2026")
    }

    // MARK: schedule

    @Test func scheduleGroupsByDateAndIgnoresTicker() throws {
        let days = SeriesScheduleParser.parse(html: try Fixture.string("series-matches.html"))
        #expect(days.map(\.label) == ["Fri, 25 Sep 2026", "Sat, 26 Sep 2026", "Tue, 03 Nov 2026", "Thu, 05 Nov 2026"])
        // The fixture also carries a bare `matchInfo` ticker object that must not become a day.
        #expect(days.allSatisfy { !$0.matches.isEmpty })
    }

    @Test func finishedMatchParsesScoresAndPhase() throws {
        let days = SeriesScheduleParser.parse(html: try Fixture.string("series-matches.html"))
        let m = try #require(days.flatMap(\.matches).first { $0.id == "172981" })
        #expect(m.phase == .finished)
        #expect(m.seriesId == "13180")
        #expect(m.seriesName == "CSA T20 Challenge 2026")
        #expect(m.matchDesc == "Pool A")
        #expect(m.matchFormat == "T20")
        #expect(m.teams.map(\.name) == ["Warriors", "North West - Eastvaal Renault Dragons"])
        #expect(m.teams.map(\.score) == ["128/6", "131/3"])
        #expect(m.teams.map(\.overs) == ["20", "15.3"])
        #expect(m.startsAt == Date(timeIntervalSince1970: 1_790_337_600))
        #expect(m.status.hasSuffix("won by 7 wkts"))
    }

    @Test func abandonedAndUpcomingPhases() throws {
        let matches = SeriesScheduleParser.parse(html: try Fixture.string("series-matches.html")).flatMap(\.matches)
        #expect(try #require(matches.first { $0.id == "173001" }).phase == .finished)
        let upcoming = try #require(matches.first { $0.id == "173587" })
        #expect(upcoming.phase == .upcoming)
        #expect(upcoming.teams.allSatisfy { !$0.hasScore })
    }

    @Test func testMatchScoreJoinsInnings() {
        let json = #"""
        {"matchInfo":{"matchId":1,"seriesId":2,"seriesName":"S","matchDesc":"1st Test","matchFormat":"TEST","startDate":"1","state":"In Progress","status":"Day 3",
        "team1":{"teamId":10,"teamName":"A"},"team2":{"teamId":20,"teamName":"B"}},
        "matchScore":{"battingTeamId":20,"team1Score":{"inngs1":{"runs":266,"wickets":10,"overs":80},"inngs2":{"runs":83,"wickets":2,"overs":20.1}},"team2Score":{"inngs1":{"runs":300,"wickets":5,"overs":90}}}}
        """#
        let obj = CricbuzzFlightParser.parseJSON(json) as? [String: Any]
        let m = SeriesScheduleParser.match(obj)
        #expect(m?.teams.map(\.score) == ["266 & 83/2", "300/5"])
        #expect(m?.teams.map(\.overs) == ["20.1", "90"])
        #expect(m?.teams.map(\.isBatting) == [false, true])
        #expect(m?.phase == .live)
    }

    @Test func matchWithoutStatusIsDropped() {
        let obj = CricbuzzFlightParser.parseJSON(#"{"matchInfo":{"matchId":1,"team1":{"teamName":"A"},"team2":{"teamName":"B"}}}"#) as? [String: Any]
        #expect(SeriesScheduleParser.match(obj) == nil)
    }

    @Test func scheduleOpensOnNextMatch() {
        func m(_ state: String, start: Date) -> MatchSummary {
            MatchSummary(id: UUID().uuidString, state: state, startDate: String(Int64(start.timeIntervalSince1970 * 1000)))
        }
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let day: TimeInterval = 86_400
        let days = [
            SeriesMatchDay(label: "a", matches: [m("Complete", start: now - 5 * day)]),
            SeriesMatchDay(label: "b", matches: [m("Preview", start: now - 3 * day)]),   // washed out long ago
            SeriesMatchDay(label: "c", matches: [m("Preview", start: now + day)]),
        ]
        #expect(SeriesSchedule.anchorIndex(days, now: now) == 2)
        #expect(SeriesSchedule.anchorIndex(days + [SeriesMatchDay(label: "d", matches: [m("In Progress", start: now)])], now: now) == 2)
        #expect(SeriesSchedule.anchorIndex([days[0]], now: now) == 0)
        let live = [days[0], SeriesMatchDay(label: "x", matches: [m("In Progress", start: now - day)])]
        #expect(SeriesSchedule.anchorIndex(live, now: now) == 1)
    }

    @Test func dayHeadingFallsBackToCricbuzzLabel() {
        let m = MatchSummary(id: "1", state: "Complete")
        #expect(SeriesSchedule.heading(SeriesMatchDay(label: "Fri, 25 Sep 2026", matches: [m])) == "Fri, 25 Sep 2026 · 1 match")
        #expect(SeriesSchedule.heading(SeriesMatchDay(label: "x", matches: [m, m])) == "x · 2 matches")
    }

    // MARK: points table

    @Test func pointsTableKeepsGroupsInOrder() throws {
        let groups = PointsTableParser.parse(html: try Fixture.string("series-points-table.html"))
        #expect(groups.map(\.name) == ["Super Eights", "Pool B", "Pool A"])
        #expect(groups.allSatisfy { $0.rows.count == 4 })
        let titans = try #require(groups[1].rows.first)
        #expect(titans.name == "Titans")
        #expect(titans.shortName == "TIT")
        #expect(titans.teamId == "89")
        #expect(titans.played == 7 && titans.won == 6 && titans.lost == 1)
        #expect(titans.points == 28)
        #expect(titans.nrr == 3.357)
        #expect(titans.qualified)
        #expect(titans.form == ["W", "W", "L", "L", "L"])
        #expect(titans.logoURL?.absoluteString == "https://static.cricbuzz.com/a/img/v1/72x72/i1/c172182/i.jpg")
    }

    @Test func singleGroupTableAndNegativeNRR() throws {
        let groups = PointsTableParser.parse(html: try Fixture.string("series-points-table-single.html"))
        #expect(groups.count == 1)
        let tas = try #require(groups[0].rows.first)
        #expect(tas.name == "Tasmania")
        #expect(tas.nrr == -0.440)
        #expect(tas.points == 18)
        #expect(!tas.qualified)
        #expect(PointsFormat.cutoff(groups[0].rows) == nil)
    }

    @Test func noTableInUnrelatedHTML() {
        #expect(PointsTableParser.parse(html: "<html><script>self.__next_f.push([1,\"1:[1]\"])</script></html>").isEmpty)
    }

    @Test func nrrAndCutoffFormatting() {
        #expect(PointsFormat.nrr(1.029) == "+1.029")
        #expect(PointsFormat.nrr(-1.05) == "-1.050")
        #expect(PointsFormat.nrr(0) == "0.000")
        func row(_ q: Bool) -> PointsRow {
            PointsRow(teamId: "", name: "", shortName: "", imageId: "", played: 0, won: 0, lost: 0, tied: 0, noResult: 0,
                      nrr: 0, points: 0, form: [], qualified: q)
        }
        #expect(PointsFormat.cutoff([row(true), row(true), row(false), row(false)]) == 1)
        #expect(PointsFormat.cutoff([row(true), row(true)]) == nil)
        #expect(PointsFormat.cutoff([row(false), row(false)]) == nil)
    }

    // MARK: stats

    @Test func statsMenuAndSingleFormatTable() throws {
        let stats = try #require(SeriesStatsParser.parse(html: try Fixture.string("series-stats-t20.html")))
        #expect(stats.selectedType == "mostRuns")
        #expect(stats.types.first == SeriesStatType(value: "mostRuns", header: "Most Runs", category: "Batting"))
        #expect(stats.types.count > 5)
        #expect(stats.types.allSatisfy { !$0.value.isEmpty })
        #expect(stats.tables.map(\.format) == ["T20"])
        #expect(stats.defaultFormat == "T20")
        let table = stats.tables[0]
        #expect(table.headers == ["PLAYER", "MATCHES", "INNS", "RUNS", "AVG", "SR", "4s", "6s"])
        #expect(table.rows.count == 4)
        let top = table.rows[0]
        #expect(top.playerId == "50545")
        #expect(top.playerName == "Lhuan-dre Pretorius")
        #expect(top.cells == ["7", "7", "453", "75.50", "198.68", "50", "24"])
    }

    @Test func multiFormatStatsPickCricbuzzDefault() throws {
        let stats = try #require(SeriesStatsParser.parse(html: try Fixture.string("series-stats-multi.html")))
        #expect(stats.tables.map(\.format) == ["ODI", "TEST"])
        #expect(stats.defaultFormat == "ODI")
        let test = try #require(stats.tables.first { $0.format == "TEST" })
        #expect(test.rows[0].playerName == "Matt Renshaw")
        #expect(test.rows[0].cells[2] == "369")
    }

    @Test func statsRowShapes() throws {
        let html = #"<script>self.__next_f.push([1,"1:{\"initialStats\":{\"t20StatsList\":{\"headers\":[\"PLAYER\",\"M\"],\"values\":[{\"values\":[\"7\",\"A\",\"3\"]},{\"values\":[\"B\",\"4\"]},{\"values\":[\"x\"]},{\"values\":[\"9\",\" \",\"1\"]}]}},\"statsTypes\":{\"types\":[]}}\n"])</script>"#
        let stats = try #require(SeriesStatsParser.parse(html: html))
        let rows = stats.tables[0].rows
        #expect(rows == [SeriesStatsRow(playerId: "7", playerName: "A", cells: ["3"]),
                         SeriesStatsRow(playerId: "", playerName: "B", cells: ["4"])])
        #expect(stats.selectedType == "")
        #expect(stats.defaultFormat == nil)
    }

    @Test func noStatsInUnrelatedHTML() {
        #expect(SeriesStatsParser.parse(html: "<html></html>") == nil)
    }

    // MARK: scraper

    @Test func scraperFindsSlugOnceAndCaches() async throws {
        let landing = try Fixture.string("series-landing.html")
        let matches = try Fixture.string("series-matches.html")
        let points = try Fixture.string("series-points-table.html")
        let stats = try Fixture.string("series-stats-t20.html")
        let log = URLLog()
        let scraper = SeriesDetailScraper(fetcher: { url in
            await log.add(url.absoluteString)
            let path = url.absoluteString
            if path.hasSuffix("/13180/matches") { return landing }
            if path.hasSuffix("/matches") { return matches }
            if path.hasSuffix("/points-table") { return points }
            return stats
        })
        let days = try await scraper.schedule(seriesId: "13180")
        #expect(days.count == 4)
        _ = try await scraper.schedule(seriesId: "13180")
        let groups = try await scraper.pointsTable(seriesId: "13180")
        #expect(groups.count == 3)
        let s = try await scraper.stats(seriesId: "13180", statsType: "mostRuns")
        #expect(s.tables.count == 1)
        _ = try await scraper.stats(seriesId: "13180", statsType: "mostRuns")
        let base = "https://www.cricbuzz.com/cricket-series/13180"
        #expect(await log.urls == ["\(base)/matches", "\(base)/csa-t20-challenge-2026/matches",
                                   "\(base)/csa-t20-challenge-2026/points-table",
                                   "\(base)/csa-t20-challenge-2026/stats?statsType=mostRuns"])
    }

    @Test func scraperFailsWhenNoSlugAnywhere() async {
        let scraper = SeriesDetailScraper(fetcher: { _ in "<html></html>" })
        await #expect(throws: SeriesDetailScraper.SlugNotFound.self) { try await scraper.schedule(seriesId: "1") }
    }

    @Test func statsWithoutSectionAreEmptyNotAnError() async throws {
        let landing = try Fixture.string("series-landing.html")
        let scraper = SeriesDetailScraper(fetcher: { url in url.absoluteString.hasSuffix("/13180/matches") ? landing : "<html></html>" })
        let stats = try await scraper.stats(seriesId: "13180", statsType: nil)
        #expect(stats == .empty)
    }
}

private actor URLLog {
    private(set) var urls: [String] = []
    func add(_ url: String) { urls.append(url) }
}
