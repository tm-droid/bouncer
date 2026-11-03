import Foundation

/// Clips for one match: a single `listRows` on the public `highlights` table, narrowed to the match
/// day plus or minus one (the matcher's own day gate) and to the columns the app reads. Reads bill
/// per row, so the page is capped and nothing here polls.
struct HighlightsRepository: Sendable {
    static let table = "highlights"
    static let limit = 50
    static let columns = ["highlight_id", "title", "img_url", "url", "channel", "match_home_team", "match_away_team",
                          "match_league_name", "match_start_date", "fetched_at"]

    let api: any AppwriteAPI

    static func queries(for target: HighlightTarget) -> [Query] {
        let day = HighlightDays.dayNumber(iso: target.day) ?? 0
        let dates = (day - 1...day + 1).map { HighlightDays.string(forDayNumber: $0) + "T00:00:00.000Z" }
        return [.equal("match_start_date", dates), .orderDesc("fetched_at"), .limit(limit), .select(columns)]
    }

    func clips(for target: HighlightTarget) async throws -> [Highlight] {
        let data = try await api.listRows(table: Self.table, queries: Self.queries(for: target))
        let list: RowList<Highlight>
        do { list = try WireJSON.decode(RowList<Highlight>.self, from: data) }
        catch { throw AppwriteError.decoding(error.localizedDescription) }
        return HighlightMatcher.matching(list.rows, for: target)
    }
}
