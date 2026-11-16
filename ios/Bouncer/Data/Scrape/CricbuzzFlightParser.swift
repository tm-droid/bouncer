import Foundation

/// Extracts Next.js App Router "flight" payloads (`self.__next_f.push([1,"…"])`) from Cricbuzz HTML.
/// Port of `forEachNextFlightPayload` / `normalizeNextFlightPayload` in Android's CricBuzzScraper.kt.
enum CricbuzzFlightParser {
    private static let marker = Array("self.__next_f.push(".utf8)

    /// Every JSON value found in the page's flight stream, in order.
    static func payloads(in html: String) -> [Any] {
        var out: [Any] = []
        forEachPayload(in: html) { out.append($0) }
        return out
    }

    static func forEachPayload(in html: String, _ body: (Any) -> Void) {
        let bytes = Array(html.utf8)
        var cursor = 0
        while let start = find(marker, in: bytes, from: cursor) {
            let open = start + marker.count - 1
            guard let close = matchingParen(bytes, open: open) else {
                cursor = start + 1
                continue
            }
            let argument = String(decoding: bytes[(open + 1)..<close], as: UTF8.self)
            for value in unwrap(argument) { body(value) }
            cursor = close + 1
        }
    }

    /// Argument is `[1,"20:[...]\n"]`: take element 1, split into rows (a push can carry several
    /// `id:json` rows), strip each row's hex id prefix and parse it.
    static func unwrap(_ argument: String) -> [Any] {
        guard let array = parseJSON(argument) as? [Any], array.count > 1, let text = array[1] as? String else { return [] }
        return text.split(separator: "\n", omittingEmptySubsequences: true).compactMap { row in
            normalize(String(row)).flatMap(parseJSON)
        }
    }

    /// Drops the `20:` row id and an optional `I` import marker; only objects and arrays survive.
    static func normalize(_ raw: String) -> String? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if let colon = s.firstIndex(of: ":") {
            let distance = s.distance(from: s.startIndex, to: colon)
            let prefix = s[s.startIndex..<colon]
            if (1...9).contains(distance), prefix.allSatisfy({ $0.isHexDigit }) {
                s = String(s[s.index(after: colon)...])
            }
        }
        if s.hasPrefix("I") { s.removeFirst() }
        s = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return (s.hasPrefix("{") || s.hasPrefix("[")) ? s : nil
    }

    static func parseJSON(_ s: String) -> Any? {
        try? JSONSerialization.jsonObject(with: Data(s.utf8), options: [.fragmentsAllowed])
    }

    /// Depth-first search for the first dictionary satisfying `predicate`.
    static func findObject(in value: Any, where predicate: ([String: Any]) -> Bool) -> [String: Any]? {
        if let dict = value as? [String: Any] {
            if predicate(dict) { return dict }
            for child in dict.values { if let hit = findObject(in: child, where: predicate) { return hit } }
        } else if let array = value as? [Any] {
            for child in array { if let hit = findObject(in: child, where: predicate) { return hit } }
        }
        return nil
    }

    // MARK: byte scanning

    private static func find(_ needle: [UInt8], in hay: [UInt8], from: Int) -> Int? {
        guard needle.count > 0, hay.count >= needle.count, from <= hay.count - needle.count else { return nil }
        let first = needle[0]
        var i = from
        let last = hay.count - needle.count
        while i <= last {
            if hay[i] == first {
                var j = 1
                while j < needle.count, hay[i + j] == needle[j] { j += 1 }
                if j == needle.count { return i }
            }
            i += 1
        }
        return nil
    }

    /// Index of the `)` closing the `(` at `open`, ignoring parens inside JSON strings.
    private static func matchingParen(_ b: [UInt8], open: Int) -> Int? {
        var depth = 0
        var inString = false
        var escape = false
        var i = open
        while i < b.count {
            let c = b[i]
            if escape { escape = false }
            else if c == 0x5C { escape = true }
            else if c == 0x22 { inString.toggle() }
            else if !inString {
                if c == 0x28 { depth += 1 }
                else if c == 0x29 { depth -= 1; if depth == 0 { return i } }
            }
            i += 1
        }
        return nil
    }
}

/// `/cricket-match-squads/{id}` team sheets. Port of Android's `MatchSquadsRsc`.
/// The flight stream carries `{team1:{team,players}, team2:{team,players}}`; `players` is keyed by
/// group: "playing XI" and "bench" after the toss, "Squad" before it, plus "support staff".
enum SquadsParser {
    static let groupXI = "playing XI"
    static let groupBench = "bench"
    static let groupSquad = "Squad"

    static func parse(html: String) -> Squads? {
        var holder: [String: Any]?
        CricbuzzFlightParser.forEachPayload(in: html) { payload in
            guard holder == nil else { return }
            holder = CricbuzzFlightParser.findObject(in: payload) { isSide($0["team1"]) && isSide($0["team2"]) }
        }
        guard let holder,
              let t1 = side(holder["team1"] as? [String: Any]),
              let t2 = side(holder["team2"] as? [String: Any]) else { return nil }
        return Squads(team1: t1, team2: t2)
    }

    private static func isSide(_ any: Any?) -> Bool {
        guard let d = any as? [String: Any] else { return false }
        return d["team"] is [String: Any] && d["players"] is [String: Any]
    }

    private static func side(_ obj: [String: Any]?) -> TeamSquad? {
        guard let obj, let team = obj["team"] as? [String: Any], let players = obj["players"] as? [String: Any] else { return nil }
        return TeamSquad(
            teamId: string(team["teamId"]) ?? "",
            name: string(team["teamName"]) ?? "",
            shortName: string(team["teamSName"]) ?? "",
            playingXI: group(players[groupXI]),
            bench: group(players[groupBench]),
            squad: group(players[groupSquad]))
    }

    private static func group(_ any: Any?) -> [SquadPlayer] {
        guard let arr = any as? [Any] else { return [] }
        return arr.compactMap { el in
            guard let p = el as? [String: Any], let name = string(p["name"]) else { return nil }
            return SquadPlayer(id: string(p["id"]) ?? "", name: name, role: string(p["role"]) ?? "",
                               captain: bool(p["captain"]), keeper: bool(p["keeper"]), imageURL: headshot(p))
        }
    }

    /// Cricbuzz serves one stock silhouette under this id for players without a photo.
    static let placeholderImageId = "182026"

    /// The head-shot for a squad player (`imageDetails.imageId`, else `faceImageId`); nil for the stock silhouette.
    private static func headshot(_ p: [String: Any]) -> URL? {
        let id = string((p["imageDetails"] as? [String: Any])?["imageId"]) ?? string(p["faceImageId"])
        guard let id, id != placeholderImageId else { return nil }
        return PlayerProfile.imageURL(faceImageId: id)
    }

    /// Scalar to string; blank and React `$undefined`-style markers are treated as absent.
    private static func string(_ any: Any?) -> String? {
        let s: String?
        if let v = any as? String { s = v }
        else if let n = any as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() { s = n.stringValue }
        else { s = nil }
        guard let s, !s.trimmingCharacters(in: .whitespaces).isEmpty, !s.hasPrefix("$") else { return nil }
        return s
    }

    private static func bool(_ any: Any?) -> Bool {
        if let b = any as? Bool { return b }
        if let s = any as? String { return s == "true" }
        return false
    }
}
