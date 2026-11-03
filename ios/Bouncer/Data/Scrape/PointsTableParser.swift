import Foundation

/// The Points table tab: `pointsTableData.pointsTable[]` is one `{groupName, pointsTableInfo[]}` per
/// pool or stage. Port of Android's `parsePointsTableFlight`, which flattened the groups.
enum PointsTableParser {
    /// Groups in page order; empty when the page carries no standings (the unslugged URL answers so).
    static func parse(html: String) -> [PointsGroup] {
        var groups: [PointsGroup] = []
        CricbuzzFlightParser.forEachPayload(in: html) { payload in
            if groups.isEmpty { collect(payload, into: &groups) }
        }
        return groups
    }

    private static func collect(_ value: Any, into groups: inout [PointsGroup]) {
        if let dict = value as? [String: Any] {
            if let info = dict["pointsTableInfo"] as? [Any] {
                let rows = info.compactMap { row($0 as? [String: Any]) }
                if !rows.isEmpty { groups.append(PointsGroup(name: FlightField.string(dict["groupName"]) ?? "", rows: rows)) }
                return
            }
            for child in dict.values { collect(child, into: &groups) }
        } else if let array = value as? [Any] {
            for child in array { collect(child, into: &groups) }
        }
    }

    /// `teamName` on a row is the short code ("GAW"); the full name is `teamFullName`.
    private static func row(_ r: [String: Any]?) -> PointsRow? {
        guard let r, let name = FlightField.string(r["teamFullName"]) else { return nil }
        return PointsRow(
            teamId: FlightField.string(r["teamId"]) ?? "", name: name,
            shortName: FlightField.string(r["teamName"]) ?? TeamAcronym.make(name),
            imageId: FlightField.string(r["teamImageId"]) ?? "",
            played: FlightField.int(r["matchesPlayed"]) ?? 0, won: FlightField.int(r["matchesWon"]) ?? 0,
            lost: FlightField.int(r["matchesLost"]) ?? 0, tied: FlightField.int(r["matchesTied"]) ?? 0,
            noResult: FlightField.int(r["noRes"]) ?? 0,
            // Cricbuzz writes the rate signed, "+1.029"; Double() accepts the sign.
            nrr: FlightField.string(r["nrr"]).flatMap { Double($0) } ?? 0,
            points: FlightField.int(r["points"]) ?? 0,
            form: (r["form"] as? [Any] ?? []).compactMap { FlightField.string($0) },
            qualified: FlightField.string(r["teamQualifyStatus"])?.uppercased() == "Q")
    }
}
