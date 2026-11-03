import Foundation

/// Clips for one match: a single `listRows` on the public `highlights` table, narrowed to the match
/// day plus or minus one (the matcher's own day gate) and to the columns the app reads. Reads bill
/// per row, so the page is capped and nothing here polls. Results (empty ones too) are kept in
/// memory for the process, so reopening a match does not bill the page again.
struct HighlightsRepository: Sendable {
    static let table = "highlights"
    /// Small because the rows are also narrowed to the match's two teams: a match has a handful of clips.
    static let limit = 20
    static let columns = ["highlight_id", "title", "img_url", "url", "channel", "match_home_team", "match_away_team",
                          "match_league_name", "match_start_date", "fetched_at"]

    /// Clips for a finished match no longer change; a live or upcoming match can gain clips as the day goes on.
    static let finishedTTL: TimeInterval = 30 * 60
    static let openTTL: TimeInterval = 10 * 60

    let api: any AppwriteAPI
    var cache: HighlightCache = .shared

    static func queries(for target: HighlightTarget) -> [Query] {
        let day = HighlightDays.dayNumber(iso: target.day) ?? 0
        let dates = (day - 1...day + 1).map { HighlightDays.string(forDayNumber: $0) + "T00:00:00.000Z" }
        // Without the team filter, a busy day's 50 newest rows can push this match's clips out of the page.
        // Exact names only: the matcher's fuzzy tolerance applies to the rows that come back.
        let teams = [target.team1, target.team2]
        let involved = Query.or([.equal("match_home_team", teams), .equal("match_away_team", teams)])
        return [.equal("match_start_date", dates), involved, .orderDesc("fetched_at"), .limit(limit), .select(columns)]
    }

    func clips(for target: HighlightTarget, finished: Bool = false) async throws -> [Highlight] {
        if let hit = await cache.clips(for: target) { return hit }
        let data = try await api.listRows(table: Self.table, queries: Self.queries(for: target))
        let list: RowList<Highlight>
        do { list = try WireJSON.decode(RowList<Highlight>.self, from: data) }
        catch { throw AppwriteError.decoding(error.localizedDescription) }
        let matched = HighlightMatcher.matching(list.rows, for: target)
        await cache.store(matched, for: target, ttl: finished ? Self.finishedTTL : Self.openTTL)
        return matched
    }
}

/// Process-level clip cache keyed by match target, shared by every `MatchDetailStore`.
actor HighlightCache {
    static let shared = HighlightCache()

    private var entries: [HighlightTarget: (clips: [Highlight], expires: Date)] = [:]
    private let now: @Sendable () -> Date

    init(now: @escaping @Sendable () -> Date = { Date() }) { self.now = now }

    func clips(for target: HighlightTarget) -> [Highlight]? {
        guard let e = entries[target], e.expires > now() else { return nil }
        return e.clips
    }

    func store(_ clips: [Highlight], for target: HighlightTarget, ttl: TimeInterval) {
        entries[target] = (clips, now().addingTimeInterval(ttl))
    }
}
