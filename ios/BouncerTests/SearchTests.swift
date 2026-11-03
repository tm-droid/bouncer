import Foundation
import Testing
@testable import Bouncer

struct SearchTests {
    @Test func normalizeFoldsAccentsAndPunctuation() {
        #expect(SearchText.normalize("St. Kitts") == "st kitts")
        #expect(SearchText.normalize("  Müller-Women's ") == "muller womens")
    }

    @Test func scoreBandsOrderedByKindOfMatch() {
        let prefix = SearchScore.of("ind", "india")
        let word = SearchScore.of("kings", "punjab kings")
        let sub = SearchScore.of("dia", "india")
        let fuzzy = SearchScore.of("kholi", "kohli")
        #expect(prefix >= SearchScore.prefixMin && prefix < 1)
        #expect(word >= SearchScore.wordPrefixMin && word < SearchScore.prefixMin)
        #expect(sub >= SearchScore.substringMin && sub < SearchScore.wordPrefixMin)
        #expect(fuzzy > 0 && fuzzy <= SearchScore.fuzzyMax)
        #expect(SearchScore.of("india", "india") == 1)
        #expect(SearchScore.of("xyz", "india") == 0)
    }

    @Test func swappedLettersAndWordOrderStillMatch() {
        #expect(SearchScore.editDistance("kolhi", "kohli") == 1)
        #expect(SearchScore.of("kohli virat", "virat kohli") >= SearchScore.wordPrefixMin)
        #expect(SearchScore.of("virat kolhi", "virat kohli") > 0.4)
        // Short queries get no typo tolerance.
        #expect(SearchScore.of("in", "won") == 0)
        #expect(SearchScore.of("india", "indies", allowFuzzy: false) == 0)
    }

    @Test func aliasesExpandWholeAndPerWord() {
        #expect(SearchText.expanded("ipl") == ["ipl", "indian premier"])
        #expect(SearchText.expanded("ipl 2026").contains("indian premier 2026"))
        #expect(SearchText.expanded("india") == ["india"])
    }

    private func match(_ id: String, _ a: String, _ b: String, state: String = "Complete", series: String = "Tri Series",
                       start: Int64 = 1_700_000_000_000) -> MatchSummary {
        MatchSummary(id: id, title: "\(a) vs \(b)", state: state, seriesName: series, startDate: String(start),
                     teams: [TeamScore(name: a), TeamScore(name: b)])
    }

    @Test func teamAcronymOutranksLongerNameAndDedupes() {
        let r = SearchRepository.search("mi", matches: [match("1", "Mumbai Indians", "MI Cape Town")], series: [],
                                        teamNames: ["Mumbai Indians"])
        #expect(r.teams.first?.item.name == "Mumbai Indians")
        #expect(r.teams.map(\.item.name).filter { $0 == "Mumbai Indians" }.count == 1)
    }

    @Test func matchRankingPrefersLiveThenNewest() {
        let old = match("old", "India", "Australia", start: 1_600_000_000_000)
        let new = match("new", "India", "England", start: 1_700_000_000_000)
        let live = match("live", "India", "Pakistan", state: "In Progress", start: 1_650_000_000_000)
        let r = SearchRepository.search("india", matches: [old, new, live, new], series: [], teamNames: [])
        #expect(r.matches.map(\.item.id) == ["live", "new", "old"])
    }

    @Test func exactTeamQueryIsNotFuzzyCorrected() {
        let r = SearchRepository.search("india", matches: [match("1", "West Indies", "England")], series: [], teamNames: ["India"])
        #expect(r.matches.isEmpty)
        #expect(r.teams.first?.item.name == "India")
    }

    private func series(_ id: String, _ name: String, _ category: String) throws -> SeriesSummary {
        let json = "{\"id\":\"\(id)\",\"name\":\"\(name)\",\"category\":\"\(category)\"}"
        return try WireJSON.decode(SeriesSummary.self, from: Data(json.utf8))
    }

    @Test func seriesFoundByAcronymAndCategory() throws {
        let ipl = try series("1", "Indian Premier League 2026", "LEAGUE")
        let other = try series("2", "Ashes 2026", "INTERNATIONAL")
        let r = SearchRepository.search("ipl", matches: [], series: [ipl, other], teamNames: [])
        #expect(r.series.map(\.item.id) == ["1"])
        #expect(SearchRepository.search("inter", matches: [], series: [other], teamNames: []).series.count == 1)
    }

    @Test func shortQueryReturnsNothing() {
        #expect(SearchRepository.search("i", matches: [match("1", "India", "Australia")], series: [], teamNames: ["India"]).isEmpty)
    }

    @Test func recentsMoveToFrontDedupeAndCap() {
        var list = SearchRecents.adding("India", to: [])
        list = SearchRecents.adding("ipl", to: list)
        list = SearchRecents.adding("INDIA", to: list)
        #expect(list == ["INDIA", "ipl"])
        #expect(SearchRecents.adding("x", to: list) == list)
        for i in 0..<15 { list = SearchRecents.adding("query \(i)", to: list) }
        #expect(list.count == SearchRecents.limit)
        #expect(SearchRecents.decode(SearchRecents.encode(list)) == list)
    }
}
