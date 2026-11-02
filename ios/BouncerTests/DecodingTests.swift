import Foundation
import Testing
@testable import Bouncer

struct DecodingTests {
    @Test func liveMatchesList() throws {
        let file = try WireJSON.decode(MatchListFile.self, from: Fixture.data("live-matches.json"))
        #expect(file.matches.count == 6)
        #expect(file.fetchedAt != nil)
        let test = try #require(file.matches.first(where: { $0.id == "171297" }))
        #expect(test.matchFormat == "TEST")
        #expect(test.phase == .live)
        #expect(test.isMultiDay)
        #expect(test.teams[0].score == "346 & 32/0")
        #expect(test.teams[0].inningsScores == ["346", "32/0"])
        #expect(test.startsAt == Date(timeIntervalSince1970: 1793397600))
        #expect(test.teams[0].acronym == "IA")
    }

    @Test func recentAndUpcomingPhases() throws {
        let recent = try WireJSON.decode(MatchListFile.self, from: Fixture.data("recent-matches.json"))
        #expect(recent.matches.allSatisfy { $0.phase == .finished })
        let upcoming = try WireJSON.decode(MatchListFile.self, from: Fixture.data("upcoming-matches.json"))
        #expect(upcoming.matches.count == 2)
        #expect(upcoming.matches.allSatisfy { $0.phase == .upcoming })
        #expect(upcoming.matches[0].battingTeam == nil)
    }

    @Test func activeSeriesIsFlatArray() throws {
        let file = try WireJSON.decode(ActiveSeriesFile.self, from: Fixture.data("active-series.json"))
        #expect(file.series.count == 12)
        #expect(file.series[0].id == "11902")
        #expect(file.archiveCursor == "2026")
        #expect(file.fetchedAt != nil)
    }

    @Test func liveDetailPayloadIsJSONString() throws {
        let row = try WireJSON.decode(LiveDetailRow.self, from: Fixture.data("live-detail-162753.json"))
        #expect(row.matchId == "162753")
        let p = try #require(row.decodePayload())
        #expect(p.batsmen.count == 2)
        #expect(p.striker?.name == "Lilly Mills")
        #expect(p.bowler?.name == "Annabel Sutherland")
        #expect(p.recentBalls == ["W"])
        #expect(p.team2Name == nil)
        #expect(p.situation?.wicketsLost == 9)
        #expect(p.situation?.long == nil)
        #expect(p.winProbabilityDraw == nil)
        #expect(p.winProbabilityFactors.count == 3)
    }

    @Test func testMatchSituationHasLongForm() throws {
        let row = try WireJSON.decode(LiveDetailRow.self, from: Fixture.data("live-detail-171297.json"))
        let p = try #require(row.decodePayload())
        #expect(p.team1Score == "346 & 32/0")
        #expect(p.situation?.long?.lead == -46)
        #expect(p.situation?.long?.scheduledDays == 5)
        #expect(p.winProbabilityDraw != nil)
        #expect(p.phase == .live)
    }

    @Test func odiLiveDetail() throws {
        let p = try #require(try WireJSON.decode(LiveDetailRow.self, from: Fixture.data("live-detail-176364.json")).decodePayload())
        #expect(p.status == "Innings Break")
        #expect(p.situation?.matchFormat == "ODI")
    }

    @Test func scorecard() throws {
        let row = try WireJSON.decode(ScorecardRow.self, from: Fixture.data("scorecard-162753.json"))
        let card = try #require(row.decodeScorecard())
        #expect(card.innings.count == 1)
        let inn = card.innings[0]
        #expect(inn.batsmen.first?.name == "Katie Mack")
        #expect(inn.bowlers.first?.wides == "6")
        #expect(inn.fallOfWickets.first?.number == 1)
        #expect(inn.partnerships.first?.runs == "39")
        #expect(inn.scoreSlashed == "121/8")
        #expect(!card.overs.isEmpty)
        #expect(!card.wagon.isEmpty)
        #expect(card.probabilityTeamId == "360")
    }

    @Test func testScorecardHasThreeInnings() throws {
        let card = try #require(try WireJSON.decode(ScorecardRow.self, from: Fixture.data("scorecard-171297.json")).decodeScorecard())
        #expect(card.innings.count == 3)
    }

    @Test func commentaryPage() throws {
        let list = try WireJSON.decode(RowList<CommentaryEntry>.self, from: Fixture.data("commentary-page.json"))
        #expect(list.total > 0)
        #expect(!list.rows.isEmpty)
        let first = list.rows[0]
        #expect(first.matchId == "171297")
        #expect(first.sortKey > 1_700_000_000_000)
        #expect(first.ball == "10.6")
        #expect(first.event == CommentaryEventKind.none)
        #expect(first.extra == nil)
        #expect(zip(list.rows, list.rows.dropFirst()).allSatisfy { $0.sortKey > $1.sortKey })
    }

    @Test func commentaryWickets() throws {
        let list = try WireJSON.decode(RowList<CommentaryEntry>.self, from: Fixture.data("commentary-wickets.json"))
        #expect(!list.rows.isEmpty)
        #expect(list.rows.allSatisfy({ $0.isWicket }))
    }

    @Test(arguments: ["match-context-171297.json", "match-context-162753.json"])
    func matchContext(file: String) throws {
        let ctx = try WireJSON.decode(MatchContext.self, from: Fixture.data(file))
        #expect(ctx.v == 2)
        #expect(ctx.teams.count == 2)
        #expect(ctx.venue?.name.isEmpty == false)
        #expect(ctx.toss?.decision == "Bowling" || ctx.toss?.decision == "Batting")
        #expect(ctx.forecast?.hours.count == 3)
        #expect(ctx.teams[0].members.count == 3)
    }

    @Test func contextExtrasOnT20() throws {
        let ctx = try WireJSON.decode(MatchContext.self, from: Fixture.data("match-context-162753.json"))
        #expect(ctx.isWomens)
        #expect(ctx.h2h?.games == 21)
        #expect(ctx.preview?.text.isEmpty == false)
        #expect(ctx.teams[0].members[0].record?.runs == 7273)
        let test = try WireJSON.decode(MatchContext.self, from: Fixture.data("match-context-171297.json"))
        #expect(test.h2h == nil)
        #expect(test.teams[0].members[0].record == nil)
    }

    @Test func newsListDropsAds() throws {
        let list = try WireJSON.decode(NewsList.self, from: Fixture.data("news-list.json"))
        #expect(list.stories.count >= 3)
        #expect(list.stories[0].id == 140760)
        #expect(list.stories[0].headline.contains("de Zorzi"))
        #expect(list.stories[0].publishedAt != nil)
        #expect(list.stories[0].coverImage?.id == "1114409")
    }

    @Test func newsArticle() throws {
        let a = try WireJSON.decode(NewsArticle.self, from: Fixture.data("news-article.json"))
        #expect(a.storyId == "140760")
        #expect(a.paragraphs.count == 5)
        #expect(!a.isStale)
        let stale = try WireJSON.decode(NewsArticle.self, from: Data(#"{"storyId":"1","paragraphs":["a"],"_bouncerStale":true}"#.utf8))
        #expect(stale.isStale)
    }

    @Test func toleratesMissingAndMistypedKeys() throws {
        let t = try WireJSON.decode(TeamScore.self, from: Data(#"{"name":5,"isBatting":"true"}"#.utf8))
        #expect(t.name == "5")
        #expect(t.isBatting)
        #expect(t.score == "")
        let p = try WireJSON.decode(LiveDetailPayload.self, from: Data("{}".utf8))
        #expect(p.batsmen.isEmpty && p.bowler == nil)
    }
}

struct HelperTests {
    @Test func phaseFromState() {
        #expect(MatchPhase.from(state: "In Progress") == .live)
        #expect(MatchPhase.from(state: "Stumps") == .live)
        #expect(MatchPhase.from(state: "Badlight") == .live)
        #expect(MatchPhase.from(state: "Complete") == .finished)
        #expect(MatchPhase.from(state: "Preview") == .upcoming)
        #expect(MatchPhase.from(state: "Upcoming") == .upcoming)
        #expect(MatchPhase.from(state: "", status: "Match drawn, England 2nd innings 340/6") == .finished)
        #expect(MatchPhase.from(state: "", status: "Match starts at Nov 03, 06:45 GMT") == .upcoming)
    }

    @Test func testScoreFormatting() {
        #expect(ScoreFormat.format(innings: [(346, 10), (32, 0)], multiDay: true) == "346 & 32/0")
        #expect(ScoreFormat.format(innings: [(180, 6)], multiDay: false) == "180/6")
        #expect(ScoreFormat.format(innings: [(180, 10)], multiDay: false) == "180/10")
        #expect(ScoreFormat.slashed("121-8") == "121/8")
        #expect(ScoreFormat.slashed("121/8") == "121/8")
        #expect(ScoreFormat.parse("121-8")?.wickets == 8)
        #expect(ScoreFormat.parse("346")?.wickets == nil)
    }

    @Test func acronyms() {
        #expect(TeamAcronym.make("Royal Challengers Bengaluru") == "RCB")
        #expect(TeamAcronym.make("India") == "IND")
        #expect(TeamAcronym.make("") == "")
    }

    @Test func battingTeam() {
        let m = MatchSummary(teams: [TeamScore(name: "A"), TeamScore(name: "B", isBatting: true)])
        #expect(m.battingTeam?.name == "B")
    }
}
