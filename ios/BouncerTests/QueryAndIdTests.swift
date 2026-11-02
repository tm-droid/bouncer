import Foundation
import Testing
@testable import Bouncer

struct MatchRowIdTests {
    /// Reference implementation of the JS rule, independent of the one under test.
    private func jsEquivalent(_ id: String) -> String {
        let kept = id.unicodeScalars.filter {
            ("a"..."z").contains($0) || ("A"..."Z").contains($0) || ("0"..."9").contains($0) || $0 == "." || $0 == "_" || $0 == "-"
        }
        return String(String.UnicodeScalarView(kept.prefix(36)))
    }

    static let cases = [
        "12345", "already-clean_id.v2", "with spaces in it", "slash/and:colon", "emoji🏏inside",
        "ünïcödé-áccents", String(repeating: "a", count: 36), String(repeating: "b", count: 37),
        String(repeating: "c", count: 200), "!!!", "", "cricbuzz#98765?x=1", "  leading and trailing  ",
        "tab\tnewline\nreturn\r", "ID_WITH_UPPER-123.4",
    ]

    @Test(arguments: cases)
    func matchesJavascriptRule(input: String) {
        #expect(MatchRowId.sanitize(input) == jsEquivalent(input))
        #expect(MatchRowId.sanitize(input).count <= 36)
    }

    @Test func knownValues() {
        #expect(MatchRowId.sanitize("with spaces in it") == "withspacesinit")
        #expect(MatchRowId.sanitize("slash/and:colon") == "slashandcolon")
        #expect(MatchRowId.sanitize("!!!") == "")
        #expect(MatchRowId.sanitize("already-clean_id.v2") == "already-clean_id.v2")
        #expect(MatchRowId.sanitize(String(repeating: "b", count: 37)).count == 36)
    }

    @Test func stripsMultibyteBeforeTruncating() {
        let input = String(repeating: "🏏", count: 50) + String(repeating: "x", count: 40)
        #expect(MatchRowId.sanitize(input) == String(repeating: "x", count: 36))
    }

    @Test func contextIdIs32() {
        #expect(MatchRowId.contextId(String(repeating: "a", count: 50)).count == 32)
        #expect(MatchRowId.contextFile("171297") == "mc-171297")
    }
}

struct QueryTests {
    @Test func encodings() {
        #expect(Query.equal("match_id", ["171297"]).json == #"{"method":"equal","attribute":"match_id","values":["171297"]}"#)
        #expect(Query.equal("event", ["FOUR", "SIX"]).json == #"{"method":"equal","attribute":"event","values":["FOUR","SIX"]}"#)
        #expect(Query.lessThan("sort_key", 1793597594960).json == #"{"method":"lessThan","attribute":"sort_key","values":[1793597594960]}"#)
        #expect(Query.greaterThan("sort_key", 5).json == #"{"method":"greaterThan","attribute":"sort_key","values":[5]}"#)
        #expect(Query.orderDesc("sort_key").json == #"{"method":"orderDesc","attribute":"sort_key"}"#)
        #expect(Query.limit(48).json == #"{"method":"limit","values":[48]}"#)
        #expect(Query.select(["a", "b"]).json == #"{"method":"select","values":["a","b"]}"#)
    }

    @Test func escapesStrings() {
        #expect(Query.equal("a", ["x\"y\\z"]).json == #"{"method":"equal","attribute":"a","values":["x\"y\\z"]}"#)
    }

    @Test func queriesAreValidJSON() throws {
        for q in CommentaryQueries.feed(matchId: "1") + CommentaryQueries.events(matchId: "1", events: ["WICKET"], olderThan: 9) {
            #expect(try JSONSerialization.jsonObject(with: Data(q.json.utf8)) is [String: Any])
        }
    }

    @Test func commentaryPaging() {
        let first = CommentaryQueries.feed(matchId: "9").map(\.json)
        #expect(first.contains(#"{"method":"limit","values":[48]}"#))
        #expect(first.contains(#"{"method":"orderDesc","attribute":"sort_key"}"#))
        let older = CommentaryQueries.feed(matchId: "9", olderThan: 100).map(\.json)
        #expect(older.contains(#"{"method":"limit","values":[150]}"#))
        #expect(older.contains(#"{"method":"lessThan","attribute":"sort_key","values":[100]}"#))
        let catchUp = CommentaryQueries.catchUp(matchId: "9", newerThan: 100).map(\.json)
        #expect(catchUp.contains(#"{"method":"greaterThan","attribute":"sort_key","values":[100]}"#))
        let wk = CommentaryQueries.events(matchId: "9", events: CommentaryEventKind.wicketFilter).map(\.json)
        #expect(wk.contains(#"{"method":"equal","attribute":"event","values":["WICKET"]}"#))
        #expect(wk.contains(#"{"method":"limit","values":[60]}"#))
    }

    @Test func urlEscaping() {
        #expect(AppwriteClient.queryEscape(#"{"a":1}"#) == "%7B%22a%22%3A1%7D")
    }
}
