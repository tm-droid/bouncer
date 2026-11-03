import Foundation
import Observation

/// One tab's load state. Empty is a real answer: a series with no fixtures or no table.
enum TabState<T> {
    case loading, loaded(T), empty, failed
}

/// A series' Schedule, Points table and Stats. Schedule and Points table load with the screen; Stats
/// loads the first time its tab is opened, and the scraper keeps each stat type once fetched, so
/// flicking between Most Runs and Most Wickets fetches each once.
@Observable @MainActor
final class SeriesDetailStore {
    let seriesId: String
    private let scraper: SeriesDetailScraper
    private(set) var title = "Series"
    private(set) var schedule: TabState<[SeriesMatchDay]> = .loading
    private(set) var points: TabState<[PointsGroup]> = .loading
    private(set) var stats: TabState<SeriesStats> = .loading
    /// The stat type on screen; nil until the first stats page names its default.
    private(set) var statType: String?
    private(set) var statFormat: String?
    private var statsRequested = false

    init(seriesId: String, scraper: SeriesDetailScraper = .shared) {
        self.seriesId = seriesId
        self.scraper = scraper
    }

    func load(series: SeriesRepository, force: Bool = false) async {
        async let name = try? series.activeSeries().series.first(where: { $0.id == seriesId })?.name
        async let days: Void = loadSchedule(force: force)
        async let table: Void = loadPoints(force: force)
        _ = await (days, table)
        if let name = await name, !name.isEmpty { title = name }
        if title == "Series", case .loaded(let days) = schedule,
           let n = days.lazy.compactMap({ $0.matches.first?.seriesName }).first(where: { !$0.isEmpty }) { title = n }
    }

    func loadSchedule(force: Bool = false) async {
        await fill(\.schedule) { days in days.isEmpty ? .empty : .loaded(days) } fetch: {
            try await self.scraper.schedule(seriesId: self.seriesId, forceRefresh: force)
        }
    }

    func loadPoints(force: Bool = false) async {
        await fill(\.points) { $0.isEmpty ? .empty : .loaded($0) } fetch: {
            try await self.scraper.pointsTable(seriesId: self.seriesId, forceRefresh: force)
        }
    }

    /// Called when the Stats tab is shown; fetches the default stat type once.
    func ensureStats() async {
        guard !statsRequested else { return }
        statsRequested = true
        await loadStats(type: statType)
        if Task.isCancelled { statsRequested = false }
    }

    func loadStats(type: String?, force: Bool = false) async {
        if let type { statType = type }
        await fill(\.stats, reset: true) { s in
            // A stat type with no qualifying players ("Most Nineties") still has a menu to pick another
            // from, so only a page with neither a menu nor a row is empty.
            if s.types.isEmpty && s.tables.allSatisfy({ $0.rows.isEmpty }) { return .empty }
            if self.statType == nil, !s.selectedType.isEmpty { self.statType = s.selectedType }
            // Keep the format the reader picked if this stat type has it too.
            let formats = s.tables.map(\.format)
            if self.statFormat.map({ !formats.contains($0) }) ?? true { self.statFormat = s.defaultFormat ?? formats.first }
            return .loaded(s)
        } fetch: {
            try await self.scraper.stats(seriesId: self.seriesId, statsType: type ?? self.statType, forceRefresh: force)
        }
    }

    func selectFormat(_ format: String) { statFormat = format }

    /// Shows the skeleton only when there is nothing on screen yet; a failed refresh keeps old rows.
    private func fill<T>(_ tab: ReferenceWritableKeyPath<SeriesDetailStore, TabState<T>>, reset: Bool = false,
                         _ map: (T) -> TabState<T>, fetch: () async throws -> T) async {
        if reset || !self[keyPath: tab].isLoaded { self[keyPath: tab] = .loading }
        do { self[keyPath: tab] = map(try await fetch()) }
        catch is CancellationError { }
        catch { if !self[keyPath: tab].isLoaded { self[keyPath: tab] = .failed } }
    }
}

private extension TabState {
    var isLoaded: Bool { if case .loaded = self { return true } else { return false } }
}
