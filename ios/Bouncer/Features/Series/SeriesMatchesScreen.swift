import SwiftUI

struct SeriesMatchesScreen: View {
    let seriesId: String
    @Environment(AppEnvironment.self) private var env
    @State private var matches: [MatchSummary] = []
    @State private var title = "Series"
    @State private var loading = true

    var body: some View {
        ScrollView {
            if loading && matches.isEmpty {
                VStack(spacing: 0) { ForEach(0..<5, id: \.self) { _ in SkeletonRow(); Divider().overlay(BouncerColor.divider) } }
            } else if matches.isEmpty {
                EmptyState(title: "No matches yet", message: "No live, recent or upcoming matches for this series.",
                           systemImage: "sportscourt")
            } else {
                LazyVStack(spacing: 0) {
                    ForEach(matches) { m in
                        NavigationLink(value: Route.match(id: m.id)) { MatchLine(match: m) }.buttonStyle(.plain)
                        Divider().overlay(BouncerColor.divider)
                    }
                }
            }
        }
        .refreshable { await load(force: true) }
        .background(BouncerColor.bg)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load(force: false) }
    }

    private func load(force: Bool) async {
        async let seriesFile = try? env.series.activeSeries()
        let lists = await env.matches.refreshAll(forceRefresh: force)
        if let name = await seriesFile?.series.first(where: { $0.id == seriesId })?.name { title = name }
        let rows = MatchListKind.allCases.flatMap { lists[$0]?.matches ?? [] }.filter { $0.seriesId == seriesId }
        var seen = Set<String>()
        matches = rows.filter { seen.insert($0.id).inserted }.sorted {
            ($0.startsAt ?? .distantFuture) < ($1.startsAt ?? .distantFuture)
        }
        if title == "Series", let n = matches.first?.seriesName, !n.isEmpty { title = n }
        loading = false
    }
}

private struct MatchLine: View {
    let match: MatchSummary

    private var winner: String? {
        guard match.phase == .finished else { return nil }
        return match.teams.first { match.status.localizedCaseInsensitiveContains($0.name) }?.name
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text([match.matchDesc, match.matchFormat].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
                Spacer()
                StatusBadge(status: badge)
            }
            ForEach(Array(match.teams.prefix(2).enumerated()), id: \.offset) { _, t in
                TeamRow(name: t.name, acronym: t.acronym, score: t.score, overs: t.overs, isBatting: t.isBatting && match.isLive,
                        result: winner == nil ? .undecided : (t.name == winner ? .won : .lost),
                        imageURL: TeamLogoLoader.shared.getLogoUrl(for: t.name).flatMap(URL.init(string:)))
            }
            if !match.status.isEmpty {
                Text(match.status).font(.bodySmall).foregroundStyle(match.isLive ? BouncerColor.live : BouncerColor.inkSecondary)
                    .lineLimit(2)
            }
        }
        .padding(.horizontal, Spacing.rowH).padding(.vertical, Spacing.rowV)
        .contentShape(Rectangle())
    }

    private var badge: MatchStatusType {
        switch match.phase { case .live: .live; case .upcoming: .upcoming; case .finished: .completed }
    }
}
