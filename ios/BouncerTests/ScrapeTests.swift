import Foundation
import Testing
@testable import Bouncer

struct ScrapeTests {
    @Test func flightPayloadsFound() throws {
        let html = try Fixture.string("squads.html")
        let payloads = CricbuzzFlightParser.payloads(in: html)
        #expect(payloads.count >= 2)
    }

    @Test func normalizeStripsIdAndImportMarker() {
        #expect(CricbuzzFlightParser.normalize("20:[1,2]") == "[1,2]")
        #expect(CricbuzzFlightParser.normalize("1a:I[\"x\"]") == "[\"x\"]")
        #expect(CricbuzzFlightParser.normalize("5:Thello") == nil)
        #expect(CricbuzzFlightParser.normalize("{\"a\":1}") == "{\"a\":1}")
    }

    @Test func parensInsideStringsAreIgnored() {
        let html = #"<script>self.__next_f.push([1,"3:{\"k\":\"a)b(c\"}\n"])</script>"#
        let p = CricbuzzFlightParser.payloads(in: html)
        #expect((p.first as? [String: Any])?["k"] as? String == "a)b(c")
    }

    @Test func squadsFromPlayingXI() throws {
        let squads = try #require(SquadsParser.parse(html: try Fixture.string("squads.html")))
        #expect(squads.team2.shortName == "NZA" || squads.team1.shortName == "NZA")
        #expect(squads.hasPlayingXI)
        let nz = squads.team1.shortName == "NZA" ? squads.team1 : squads.team2
        #expect(nz.teamId == "80")
        #expect(nz.playingXI.count == 11)
        let captain = try #require(nz.playingXI.first(where: { $0.captain }))
        #expect(captain.name == "Rhys Mariu")
        #expect(captain.id == "15204")
        #expect(captain.role == "Batter")
        #expect(nz.playingXI.contains { $0.role == "WK-Batter" })
        #expect(!nz.bench.isEmpty)
        #expect(nz.squad.isEmpty)
    }

    @Test func squadPhotosSkipTheStockSilhouette() throws {
        let squads = try #require(SquadsParser.parse(html: try Fixture.string("squads.html")))
        let everyone = [squads.team1, squads.team2].flatMap { $0.playingXI + $0.bench + $0.squad }
        let clarke = try #require(everyone.first { $0.name == "Kristian Clarke" })
        #expect(clarke.imageURL == PlayerProfile.imageURL(faceImageId: "829847"))
        // Rhys Mariu has only Cricbuzz's placeholder photo, so the row shows initials.
        #expect(try #require(everyone.first { $0.name == "Rhys Mariu" }).imageURL == nil)
    }

    @Test func noSquadsInUnrelatedHTML() {
        #expect(SquadsParser.parse(html: "<html><script>self.__next_f.push([1,\"1:[1]\"])</script></html>") == nil)
    }

    @Test func squadsRepositoryCachesAndSingleFlights() async throws {
        let html = try Fixture.string("squads.html")
        let counter = Counter()
        let repo = SquadsRepository(fetcher: { _ in
            await counter.bump()
            try await Task.sleep(nanoseconds: 50_000_000)
            return html
        })
        async let a = repo.squads(matchId: "171297")
        async let b = repo.squads(matchId: "171297")
        let (x, y) = await (a, b)
        #expect(x != nil && x == y)
        _ = await repo.squads(matchId: "171297")
        #expect(await counter.value == 1)
    }
}

actor Counter {
    private(set) var value = 0
    func bump() { value += 1 }
}
