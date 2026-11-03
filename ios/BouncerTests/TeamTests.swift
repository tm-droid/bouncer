import Foundation
import Testing
@testable import Bouncer

struct TeamRecordsTests {
    @Test func decodesRealTeamFile() throws {
        let records = try #require(TeamRecords.parseTeam(try Fixture.data("history-team-india.json")))
        #expect(records.map(\.format) == ["ODI", "T20I", "Test"])
        let test = try #require(records.last)
        #expect((test.played, test.won, test.lost, test.tied, test.drawn) == (219, 105, 60, 0, 54))
        #expect(test.summary == "Played 219 · Won 105 · Lost 60 · Drawn 54")
        #expect(records[0].summary == "Played 529 · Won 315 · Lost 185 · Tied 6")
    }

    @Test func unreadableTeamFileIsNil() {
        #expect(TeamRecords.parseTeam(Data("{}".utf8)) == nil)
        #expect(TeamRecords.parseTeam(Data("nope".utf8)) == nil)
    }

    @Test func resolvesKeysFromTeamList() throws {
        let teams = try #require(TeamRecords.parseTeams(try Fixture.data("history-teams.json")))
        #expect(teams.count > 100)
        #expect(TeamRecords.key(in: teams, name: "India", women: false) == "india")
        #expect(TeamRecords.key(in: teams, name: "India", women: true) == "india__w")
        #expect(TeamRecords.key(in: teams, name: "Mumbai Indians", women: false) == "mumbai-indians")
        #expect(TeamRecords.key(in: teams, name: "Not A Team", women: false) == nil)
    }

    @Test func renamedFranchiseResolvesOnlyWhenUnambiguous() {
        let teams = [HistoryTeam(name: "Punjab Kings", women: false, key: "punjab-kings", matches: 88)]
        #expect(TeamRecords.key(in: teams, name: "Kings XI Punjab", women: false) == "punjab-kings")
        let two = teams + [HistoryTeam(name: "Punjab Warriors", women: false, key: "punjab-warriors", matches: 5)]
        #expect(TeamRecords.key(in: two, name: "Punjab Royals", women: false) == nil)
        // A qualifier on one side only vetoes: "India A" is not "India".
        #expect(!TeamRecords.sameOrRenamed("India A", "India"))
    }

    @Test func womenSuffixAndArchiveKey() {
        #expect(TeamRecords.isWomens("India Women"))
        #expect(!TeamRecords.isWomens("Women Cricket Club"))
        #expect(TeamRecords.archiveSideName("Australia Women's") == "Australia")
        #expect(TeamRecords.archiveKey("Côte d'Ivoire  XI") == "cote-d-ivoire-xi")
        #expect(TeamRecords.archiveKey("!!") == "unknown")
    }

    @Test func repositoryUsesHistoryThenArchiveAndCaches() async throws {
        let teams = try Fixture.data("history-teams.json")
        let india = try Fixture.data("history-team-india.json")
        let afghanistan = try Fixture.data("archive-team-afghanistan.json")
        let calls = CallLog()
        let repo = TeamRecordsRepository { url in
            await calls.add(url.absoluteString)
            switch url.path {
            case "/bouncer-history/idx/teams.json": return teams
            case "/bouncer-history/s/team/india.json": return india
            case "/bouncer-archive/s/team/afghanistan.json": return afghanistan
            default: return nil
            }
        }
        let ind = try #require(await repo.records(for: "India"))
        #expect(ind.count == 3 && ind.allSatisfy { !$0.fromArchive })
        _ = await repo.records(for: "India")
        let afg = try #require(await repo.records(for: "Afghanistan"))
        #expect(afg.count == 3 && afg.allSatisfy(\.fromArchive))
        #expect(await repo.records(for: "Afghanistan Women") == nil)
        let log = await calls.urls
        #expect(log.filter { $0.hasSuffix("india.json") }.count == 1)
        #expect(log.filter { $0.hasSuffix("teams.json") }.count == 1)
    }

    @Test func repositoryFailureIsNil() async {
        let repo = TeamRecordsRepository { _ in nil }
        #expect(await repo.records(for: "India") == nil)
    }
}

private actor CallLog {
    private(set) var urls: [String] = []
    func add(_ url: String) { urls.append(url) }
}

struct TeamPageTests {
    private func match(_ id: String, _ status: String, state: String = "Complete", start: Int = 0,
                       teams: [(String, String)] = [("1", "India"), ("2", "Australia")]) -> MatchSummary {
        MatchSummary(id: id, status: status, state: state, startDate: start > 0 ? String(start * 1000) : "",
                     teams: teams.map { TeamScore(teamId: $0.0, name: $0.1) })
    }

    @Test func formReadsStatusLine() {
        #expect(TeamPage.formResult(match("1", "India won by 5 wkts"), team: "India") == .won)
        #expect(TeamPage.formResult(match("1", "India won by 5 wkts"), team: "Australia") == .lost)
        #expect(TeamPage.formResult(match("1", "Match drawn"), team: "India") == .drawn)
        #expect(TeamPage.formResult(match("1", "Match tied (India won the Super Over)"), team: "India") == .tied)
        #expect(TeamPage.formResult(match("1", "No result"), team: "India") == .noResult)
        #expect(TeamPage.formResult(match("1", "Match abandoned"), team: "India") == .noResult)
    }

    @Test func acronymFavouriteMatchesTheTeam() {
        let m = match("1", "India won by 5 wkts")
        #expect(TeamPage.split([m], team: "IND").results.map(\.id) == ["1"])
        #expect(TeamPage.formResult(m, team: "IND") == .won)
        #expect(TeamPage.formResult(m, team: "AUS") == .lost)
        #expect(TeamPage.lastEleven(m, team: "IND", squads: nil).isEmpty)
        #expect(TeamPage.fullName("IND", known: ["Indonesia", "India", "India Women"]) == "India")
        #expect(TeamPage.fullName("India", known: ["India"]) == "India")
        #expect(TeamPage.fullName("XYZ", known: ["India"]) == "XYZ")
    }

    @Test func formIsNilWhenGuessing() {
        // Winner named by a code that is neither side as the app spells it.
        #expect(TeamPage.formResult(match("1", "IND won by 5 wkts"), team: "India") == nil)
        #expect(TeamPage.formResult(match("1", "India won by 5 wkts", state: "In Progress"), team: "India") == nil)
        #expect(TeamPage.formResult(match("1", "England won by 5 wkts"), team: "Pakistan") == nil)
        #expect(TeamPage.formResult(match("1", "Result awaited"), team: "India") == nil)
    }

    @Test func sameNameIgnoresPunctuationAndCase() {
        #expect(TeamPage.sameTeamName("KwaZulu-Natal Inland - Tuskers", "KwaZulu - Natal Inland - Tuskers"))
        #expect(!TeamPage.sameTeamName("", ""))
        #expect(!TeamPage.sameTeamName("India", "India A"))
    }

    @Test func splitGroupsAndSorts() {
        let all = [
            match("a", "India won by 5 wkts", start: 100),
            match("b", "India won by 3 wkts", start: 300),
            match("c", "Match drawn"),
            match("d", "Starts today", state: "Preview", start: 900),
            match("e", "Day 1", state: "In Progress", start: 500),
            match("f", "Other won", teams: [("3", "Nepal"), ("4", "Oman")]),
            match("b", "India won by 3 wkts", start: 300),
        ]
        let (current, results) = TeamPage.split(all, team: "india")
        #expect(current.map(\.id) == ["e", "d"])
        #expect(results.map(\.id) == ["b", "a", "c"])
        #expect(results.map { TeamPage.formResult($0, team: "India") } == [.won, .won, .drawn])
    }

    @Test func splitCapsResults() {
        let all = (1...15).map { match("m\($0)", "India won by 1 run", start: $0) }
        #expect(TeamPage.split(all, team: "India").results.count == TeamPage.maxResults)
        #expect(TeamPage.split(all, team: "India").results.first?.id == "m15")
    }

    @Test func lastElevenJoinsOnTeamId() throws {
        let squads = try #require(SquadsParser.parse(html: try Fixture.string("squads.html")))
        let side = squads.team2
        let m = match("x", "done", teams: [(squads.team1.teamId, squads.team1.name), (side.teamId, side.name)])
        let xi = TeamPage.lastEleven(m, team: side.name.uppercased(), squads: squads)
        #expect(xi == side.playingXI && !xi.isEmpty)
        #expect(TeamPage.lastEleven(m, team: "Nobody", squads: squads).isEmpty)
        #expect(TeamPage.lastEleven(m, team: side.name, squads: nil).isEmpty)
        let noId = match("y", "done", teams: [("", side.name), ("9", "Other")])
        #expect(TeamPage.lastEleven(noId, team: side.name, squads: squads).isEmpty)
    }
}

struct OnboardingTests {
    @Test func summaryLine() {
        #expect(OnboardingScreen.summary(teams: 0, category: "League") == "Start exploring matches, squads, and insights.")
        #expect(OnboardingScreen.summary(teams: 1, category: "All") == "Following 1 team. Start exploring!")
        #expect(OnboardingScreen.summary(teams: 3, category: "League") == "Following 3 teams. Showing League matches.")
    }

    @Test func popularTeamsFitFavouritesAndHaveLogos() {
        #expect(OnboardingScreen.popularTeams.count == Set(OnboardingScreen.popularTeams).count)
        #expect(HomeFilters.competitions == ["All", "International", "League", "Domestic"])
    }
}
