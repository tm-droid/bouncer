import SwiftUI

private enum SeriesTab: String {
    case schedule = "Schedule", points = "Points table", stats = "Stats"
}

/// A series: its schedule and results, its points table and its player stats, all read from the
/// series' own Cricbuzz pages.
struct SeriesDetailScreen: View {
    @Environment(AppEnvironment.self) private var env
    @State private var store: SeriesDetailStore
    @State private var selected = SeriesTab.schedule

    init(seriesId: String) {
        _store = State(initialValue: SeriesDetailStore(seriesId: seriesId))
    }

    /// The Points table tab is hidden for a series that has none (a bilateral tour, say).
    private var tabs: [SeriesTab] {
        if case .empty = store.points { return [.schedule, .stats] }
        return [.schedule, .points, .stats]
    }

    private var tab: SeriesTab { tabs.contains(selected) ? selected : .schedule }

    var body: some View {
        VStack(spacing: 0) {
            TextTabBar(tabs: tabs.map(\.rawValue),
                       selection: Binding(get: { tabs.firstIndex(of: tab) ?? 0 }, set: { selected = tabs[$0] }))
            switch tab {
            case .schedule: SeriesScheduleTab(store: store)
            case .points: PointsTableTab(store: store)
            case .stats: SeriesStatsTab(store: store)
            }
        }
        .background(BouncerColor.bg)
        .navigationTitle(store.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await store.load(series: env.series) }
    }
}

/// Skeleton rows while a tab loads, shared by the three tabs.
struct SeriesSkeleton: View {
    var body: some View {
        VStack(spacing: 0) { ForEach(0..<6, id: \.self) { _ in SkeletonRow(); Divider().overlay(BouncerColor.divider) } }
    }
}
