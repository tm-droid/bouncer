import SwiftUI

struct SeriesStatsTab: View {
    let store: SeriesDetailStore
    @ScaledMetric private var rowHeight: CGFloat = 48

    var body: some View {
        Group {
            switch store.stats {
            case .loading:
                ScrollView { SeriesSkeleton() }
            case .failed:
                EmptyState(title: "Couldn't load stats", message: "Check your connection and try again.",
                           systemImage: "wifi.slash", actionTitle: "Retry") { Task { await store.loadStats(type: store.statType, force: true) } }
            case .empty:
                ScrollView {
                    EmptyState(title: "No stats yet", message: "Stats appear once matches in this series have been played.",
                               systemImage: "chart.bar")
                }
            case .loaded(let stats):
                loaded(stats)
            }
        }
        .task { await store.ensureStats() }
    }

    private func loaded(_ stats: SeriesStats) -> some View {
        let type = stats.types.first { $0.value == (store.statType ?? stats.selectedType) }
        let table = stats.tables.first { $0.format == store.statFormat } ?? stats.tables.first
        return VStack(spacing: 0) {
            HStack(spacing: Spacing.sm) {
                if !stats.types.isEmpty { typeMenu(stats.types, current: type) }
                Spacer(minLength: 0)
                // Only a multi-format series has a choice to offer.
                if stats.tables.count > 1, let table { formatMenu(stats.tables.map(\.format), current: table.format) }
            }
            .padding(.horizontal, Spacing.sm)
            Divider().overlay(BouncerColor.divider)
            if let table, !table.rows.isEmpty {
                ScrollView { StatsTable(table: table, rowHeight: rowHeight) }
                    .refreshable { await store.loadStats(type: store.statType, force: true) }
            } else {
                ScrollView {
                    EmptyState(title: type?.header ?? "No stats yet", message: "No players qualify for this stat yet.", systemImage: "chart.bar")
                }
            }
        }
    }

    private func typeMenu(_ types: [SeriesStatType], current: SeriesStatType?) -> some View {
        let categories = types.reduce(into: [String]()) { if !$1.category.isEmpty && !$0.contains($1.category) { $0.append($1.category) } }
        return Menu {
            ForEach(categories, id: \.self) { category in
                Section(category) { ForEach(types.filter { $0.category == category }, id: \.value) { typeButton($0, current) } }
            }
            ForEach(types.filter { $0.category.isEmpty }, id: \.value) { typeButton($0, current) }
        } label: {
            menuLabel(current?.header ?? store.statType ?? "Stat")
        }
    }

    private func typeButton(_ type: SeriesStatType, _ current: SeriesStatType?) -> some View {
        Button { Task { await store.loadStats(type: type.value) } } label: {
            if type.value == current?.value { Label(type.header, systemImage: "checkmark") } else { Text(type.header) }
        }
    }

    private func formatMenu(_ formats: [String], current: String) -> some View {
        Menu {
            ForEach(formats, id: \.self) { f in
                Button { store.selectFormat(f) } label: {
                    if f == current { Label(f, systemImage: "checkmark") } else { Text(f) }
                }
            }
        } label: {
            menuLabel(current)
        }
    }

    private func menuLabel(_ text: String) -> some View {
        HStack(spacing: 4) {
            Text(text).font(.labelLarge).lineLimit(1)
            Image(systemName: "chevron.down").font(.system(size: 11, weight: .semibold)).accessibilityHidden(true)
        }
        .foregroundStyle(BouncerColor.goldInk).padding(.horizontal, Spacing.sm).frame(minHeight: 44)
    }
}

/// Name column fixed, figures scrolling sideways together: a Most Wickets table is nine figures
/// wide, which does not fit a phone. The two columns share row heights, so rows stay aligned.
private struct StatsTable: View {
    let table: SeriesStatsTable
    let rowHeight: CGFloat
    private let rankWidth: CGFloat = 28, nameWidth: CGFloat = 176, cellWidth: CGFloat = 60

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(spacing: 0) {
                headerCell(leading: Spacing.lg) { HStack(spacing: 0) {
                    Text("#").frame(width: rankWidth, alignment: .leading)
                    Text(table.headers.first ?? "").frame(width: nameWidth, alignment: .leading)
                } }
                ForEach(Array(table.rows.enumerated()), id: \.offset) { i, row in
                    NavigationLink(value: Route.player(id: row.playerId.isEmpty ? nil : row.playerId, name: row.playerName)) {
                        HStack(spacing: 0) {
                            Text("\(i + 1)").font(.numeric).foregroundStyle(BouncerColor.inkSecondary)
                                .frame(width: rankWidth, alignment: .leading)
                            HStack(spacing: Spacing.sm) {
                                PlayerAvatar(name: row.playerName, id: row.playerId, size: 26)
                                Text(row.playerName).font(.bodyMedium.weight(.semibold)).foregroundStyle(BouncerColor.inkPrimary)
                                    .lineLimit(1)
                            }
                            .frame(width: nameWidth, alignment: .leading)
                        }
                        .padding(.leading, Spacing.lg).frame(height: rowHeight)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Divider().overlay(BouncerColor.divider)
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                VStack(spacing: 0) {
                    headerCell { HStack(spacing: 0) { ForEach(Array(table.headers.dropFirst().enumerated()), id: \.offset) { _, h in
                        Text(h).lineLimit(1).frame(width: cellWidth, alignment: .trailing)
                    } } }
                    ForEach(Array(table.rows.enumerated()), id: \.offset) { _, row in
                        HStack(spacing: 0) {
                            ForEach(Array(row.cells.enumerated()), id: \.offset) { j, cell in
                                Text(cell).font(.numeric).fontWeight(j == 0 ? .bold : .regular)
                                    .foregroundStyle(j == 0 ? BouncerColor.inkPrimary : BouncerColor.inkSecondary)
                                    .lineLimit(1).frame(width: cellWidth, alignment: .trailing)
                            }
                        }
                        .frame(height: rowHeight)
                        Divider().overlay(BouncerColor.divider)
                    }
                }
                .tabularFigures().padding(.trailing, Spacing.lg)
            }
        }
    }

    private func headerCell<C: View>(leading: CGFloat = 0, @ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: 0) {
            content().font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary)
                .padding(.leading, leading).frame(height: 32)
            Divider().overlay(BouncerColor.divider)
        }
        .accessibilityHidden(true)
    }
}
