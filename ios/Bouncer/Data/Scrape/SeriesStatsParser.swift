import Foundation

/// The Stats tab: `/stats?statsType=X` is server-rendered per stat type and carries `statsTypes` (the
/// whole menu) and `initialStats.{format}StatsList`, one table per format. Port of Android's
/// `parseSeriesStats`.
enum SeriesStatsParser {
    private static let listSuffix = "StatsList"

    /// Nil when the page carries no stats section.
    static func parse(html: String) -> SeriesStats? {
        var result: SeriesStats?
        CricbuzzFlightParser.forEachPayload(in: html) { payload in
            guard result == nil else { return }
            result = CricbuzzFlightParser.findObject(in: payload) { $0["initialStats"] != nil && $0["statsTypes"] != nil }
                .flatMap(stats)
        }
        return result
    }

    private static func stats(_ holder: [String: Any]) -> SeriesStats? {
        guard let typesObj = holder["statsTypes"] as? [String: Any], let initial = holder["initialStats"] as? [String: Any] else { return nil }
        // Category headings ("Batting") are entries without a `value`; they carry nothing selectable.
        let types: [SeriesStatType] = (typesObj["types"] as? [Any] ?? []).compactMap { entry in
            guard let o = entry as? [String: Any], let value = FlightField.string(o["value"]) else { return nil }
            return SeriesStatType(value: value, header: FlightField.string(o["header"])?.trimmingCharacters(in: .whitespaces) ?? value,
                                  category: FlightField.string(o["category"]) ?? "")
        }
        // Dictionary order is unspecified, so tables are sorted by format for a stable menu.
        let tables = initial.keys.filter { $0.hasSuffix(listSuffix) }.sorted().compactMap { key in
            (initial[key] as? [String: Any]).flatMap { table(format: String(key.dropLast(listSuffix.count)).uppercased(), $0) }
        }
        let selected = ((holder["initialSelectedStatsType"] as? [String: Any]).flatMap { FlightField.string($0["value"]) })
            ?? types.first?.value ?? ""
        return SeriesStats(types: types, selectedType: selected, tables: tables, defaultFormat: defaultFormat(initial, holder, tables))
    }

    /// The format Cricbuzz selects first: `initialSelectedFilters.matchTypeId` resolved through
    /// `initialStats.filter.matchtype` to a description ("T20"), kept only if a table has that format.
    private static func defaultFormat(_ initial: [String: Any], _ holder: [String: Any], _ tables: [SeriesStatsTable]) -> String? {
        guard let id = (holder["initialSelectedFilters"] as? [String: Any]).flatMap({ FlightField.string($0["matchTypeId"]) }),
              let types = (initial["filter"] as? [String: Any])?["matchtype"] as? [Any],
              let desc = types.compactMap({ $0 as? [String: Any] }).first(where: { FlightField.string($0["matchTypeId"]) == id })
                .flatMap({ FlightField.string($0["matchTypeDesc"]) })?.uppercased() else { return nil }
        return tables.contains { $0.format == desc } ? desc : nil
    }

    private static func table(format: String, _ list: [String: Any]) -> SeriesStatsTable? {
        guard let headers = (list["headers"] as? [Any])?.map({ FlightField.string($0) ?? "" }), !headers.isEmpty else { return nil }
        let rows = (list["values"] as? [Any] ?? []).compactMap { row -> SeriesStatsRow? in
            guard let cells = ((row as? [String: Any])?["values"] as? [Any])?.map({ FlightField.string($0) ?? "" }) else { return nil }
            return statsRow(cells, headerCount: headers.count)
        }
        return SeriesStatsTable(format: format, headers: headers, rows: rows)
    }

    /// A row is `[playerId, name, …one cell per header after PLAYER]`: one longer than the headers,
    /// because the id has no column. A row the same length as the headers has no id.
    private static func statsRow(_ cells: [String], headerCount: Int) -> SeriesStatsRow? {
        let row: SeriesStatsRow
        switch cells.count {
        case headerCount + 1: row = SeriesStatsRow(playerId: cells[0], playerName: cells[1], cells: Array(cells.dropFirst(2)))
        case headerCount: row = SeriesStatsRow(playerId: "", playerName: cells[0], cells: Array(cells.dropFirst()))
        default: return nil
        }
        return row.playerName.trimmingCharacters(in: .whitespaces).isEmpty ? nil : row
    }
}
