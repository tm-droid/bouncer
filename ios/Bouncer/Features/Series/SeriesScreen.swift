import SwiftUI

/// Where a series sits in time. Unparseable dates (empty, "TBC") count as running so they stay in Current.
enum SeriesTiming: Equatable {
    case running, upcoming(days: Int), ended(Date), unknown

    static func of(_ s: SeriesSummary, today: Date = Date(), calendar: Calendar = .current) -> SeriesTiming {
        func day(_ raw: String) -> Date? {
            guard let ms = Double(raw.trimmingCharacters(in: .whitespaces)), ms > 0 else { return nil }
            return calendar.startOfDay(for: Date(timeIntervalSince1970: ms / 1000))
        }
        guard let start = day(s.startDate) else { return .unknown }
        let end = day(s.endDate) ?? start
        let now = calendar.startOfDay(for: today)
        if now < start { return .upcoming(days: calendar.dateComponents([.day], from: now, to: start).day ?? 0) }
        if now > end { return .ended(end) }
        return .running
    }

    var isPast: Bool { if case .ended = self { return true } else { return false } }
}

enum SeriesFormat {
    static func range(_ s: SeriesSummary) -> String {
        func date(_ raw: String) -> Date? {
            guard let ms = Double(raw.trimmingCharacters(in: .whitespaces)), ms > 0 else { return nil }
            return Date(timeIntervalSince1970: ms / 1000)
        }
        guard let a = date(s.startDate) else { return "" }
        let f = Date.FormatStyle().day().month(.abbreviated)
        guard let b = date(s.endDate), !Calendar.current.isDate(a, inSameDayAs: b) else { return a.formatted(f) }
        return "\(a.formatted(f)) – \(b.formatted(f))"
    }

    static func state(_ t: SeriesTiming) -> String {
        switch t {
        case .running: return "Live now"
        case .upcoming(let d): return d <= 1 ? "Starts tomorrow" : "Starts in \(d) days"
        case .ended(let d): return "Ended \(d.formatted(Date.FormatStyle().day().month(.abbreviated)))"
        case .unknown: return ""
        }
    }

    static func category(_ s: SeriesSummary) -> String {
        let c = s.category.isEmpty ? "" : s.category.capitalized
        return s.isWomens ? (c.isEmpty ? "Women's" : "\(c) · Women's") : c
    }
}

struct SeriesScreen: View {
    @Environment(AppEnvironment.self) private var env
    @State private var tab = 0
    @State private var all: [SeriesSummary] = []
    @State private var loading = true
    @State private var failed = false

    private var shown: [SeriesSummary] {
        let rows = all.filter { SeriesTiming.of($0).isPast == (tab == 1) }
        return tab == 1 ? rows.reversed() : rows
    }

    var body: some View {
        VStack(spacing: 0) {
            TextTabBar(tabs: ["Current", "Past"], selection: $tab)
            ScrollView {
                content
            }
            .refreshable { await load(force: true) }
        }
        .background(BouncerColor.bg)
        .navigationTitle("Series")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load(force: false) }
    }

    @ViewBuilder private var content: some View {
        if loading && all.isEmpty {
            VStack(spacing: 0) { ForEach(0..<7, id: \.self) { _ in SkeletonRow(); Divider().overlay(BouncerColor.divider) } }
        } else if failed && all.isEmpty {
            EmptyState(title: "Couldn't load series", message: "Check your connection and try again.",
                       systemImage: "wifi.slash", actionTitle: "Retry") { Task { await load(force: true) } }
        } else if shown.isEmpty {
            EmptyState(title: tab == 0 ? "No current series" : "No past series",
                       message: "Pull down to refresh.", systemImage: "trophy")
        } else {
            LazyVStack(spacing: 0) {
                ForEach(shown) { s in
                    NavigationLink(value: Route.series(id: s.id)) { SeriesRow(series: s) }.buttonStyle(.plain)
                    Divider().overlay(BouncerColor.divider)
                }
            }
        }
    }

    private func load(force: Bool) async {
        if all.isEmpty { loading = true }
        do {
            all = try await env.series.activeSeries(forceRefresh: force).series
            failed = false
        } catch is CancellationError {
        } catch {
            failed = true
        }
        loading = false
    }
}

private struct SeriesRow: View {
    let series: SeriesSummary

    var body: some View {
        let timing = SeriesTiming.of(series)
        let range = SeriesFormat.range(series), state = SeriesFormat.state(timing)
        let line = [range, state].filter { !$0.isEmpty }.joined(separator: "  ·  ")
        HStack(spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(series.name).font(.titleMedium).foregroundStyle(BouncerColor.inkPrimary)
                    .lineLimit(2).multilineTextAlignment(.leading)
                if !line.isEmpty {
                    Text(line).font(.bodySmall)
                        .foregroundStyle(timing == .running ? BouncerColor.inkPrimary : BouncerColor.inkSecondary)
                        .fontWeight(timing == .running ? .semibold : .regular)
                }
                let cat = SeriesFormat.category(series)
                if !cat.isEmpty { Text(cat).font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                .foregroundStyle(BouncerColor.inkSecondary).accessibilityHidden(true)
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .frame(minHeight: 56).contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}
