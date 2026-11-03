import SwiftUI

/// Where the schedule opens: the first date group with a match live or yet to be played. Port of
/// Android's `scheduleAnchorIndex`. A match the phase cannot place as finished is only "next" if it
/// has not started more than a day ago, or every series with one washed-out game early on would
/// open on that game.
enum SeriesSchedule {
    static let staleFixture: TimeInterval = 24 * 3600

    static func anchorIndex(_ days: [SeriesMatchDay], now: Date) -> Int {
        days.firstIndex { day in
            day.matches.contains { m in
                switch m.phase {
                case .live: true
                case .finished: false
                case .upcoming: (m.startsAt ?? .distantFuture) >= now.addingTimeInterval(-staleFixture)
                }
            }
        } ?? 0
    }

    /// "Today" / "Sat 18 Oct" from the first timed match, else Cricbuzz's own heading, then the count.
    static func heading(_ day: SeriesMatchDay, now: Date = .now) -> String {
        let label = day.matches.lazy.compactMap(\.startsAt).first.map { MatchRowText.day($0, now: now) } ?? day.label
        return "\(label) · \(day.matches.count) \(day.matches.count == 1 ? "match" : "matches")"
    }
}

struct SeriesScheduleTab: View {
    let store: SeriesDetailStore

    var body: some View {
        switch store.schedule {
        case .loading:
            ScrollView { SeriesSkeleton() }
        case .failed:
            EmptyState(title: "Couldn't load the schedule", message: "Check your connection and try again.",
                       systemImage: "wifi.slash", actionTitle: "Retry") { Task { await store.loadSchedule(force: true) } }
        case .empty:
            ScrollView { EmptyState(title: "No matches listed", message: "This series has no fixtures yet.", systemImage: "calendar") }
                .refreshable { await store.loadSchedule(force: true) }
        case .loaded(let days):
            ScheduleList(days: days) { await store.loadSchedule(force: true) }
        }
    }
}

private struct ScheduleList: View {
    let days: [SeriesMatchDay]
    let refresh: () async -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0, pinnedViews: []) {
                    ForEach(Array(days.enumerated()), id: \.offset) { i, day in
                        SectionHeader(title: SeriesSchedule.heading(day)).id(i)
                        ForEach(day.matches) { MatchRow(match: $0) }
                    }
                }
            }
            .refreshable { await refresh() }
            // Opens on the next match, results above it a scroll away.
            .task(id: days) { proxy.scrollTo(SeriesSchedule.anchorIndex(days, now: .now), anchor: .top) }
        }
    }
}
