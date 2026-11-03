import SwiftUI

enum PointsFormat {
    /// "+0.412", "-1.050", "0.000": always signed, always three places, so the column aligns.
    static func nrr(_ value: Double) -> String {
        let text = String(format: "%.3f", value)
        return value > 0 ? "+\(text)" : text
    }

    /// The row under which the qualification line falls: the last qualified side, if only some qualified.
    static func cutoff(_ rows: [PointsRow]) -> Int? {
        rows.allSatisfy(\.qualified) || !rows.contains(where: \.qualified) ? nil : rows.lastIndex(where: \.qualified)
    }
}

struct PointsTableTab: View {
    let store: SeriesDetailStore

    var body: some View {
        switch store.points {
        case .loading:
            ScrollView { SeriesSkeleton() }
        case .failed:
            EmptyState(title: "Couldn't load the table", message: "Check your connection and try again.",
                       systemImage: "wifi.slash", actionTitle: "Retry") { Task { await store.loadPoints(force: true) } }
        case .empty:
            ScrollView { EmptyState(title: "No standings available", message: "This series has no points table.", systemImage: "tablecells") }
        case .loaded(let groups):
            ScrollView { PointsTable(groups: groups) }
                .refreshable { await store.loadPoints(force: true) }
        }
    }
}

private struct PointsTable: View {
    let groups: [PointsGroup]
    /// Tied and no-result column only where a side has one: most tables would carry a column of zeros.
    private var showTNR: Bool { groups.contains { $0.rows.contains { $0.tiedOrNoResult > 0 } } }
    private var hasLine: Bool { groups.contains { PointsFormat.cutoff($0.rows) != nil } }

    var body: some View {
        LazyVStack(spacing: 0) {
            ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
                if groups.count > 1 { SectionHeader(title: group.name).padding(.top, Spacing.sm) }
                header
                let cutoff = PointsFormat.cutoff(group.rows)
                ForEach(Array(group.rows.enumerated()), id: \.offset) { i, row in
                    NavigationLink(value: Route.team(name: row.name)) { PointsRowView(row: row, rank: i + 1, showTNR: showTNR) }
                        .buttonStyle(.plain)
                    Divider().overlay(i == cutoff ? BouncerColor.inkPrimary : BouncerColor.divider)
                }
            }
            if hasLine {
                Text("Teams above the line have qualified.").font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
            }
        }
    }

    private var header: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Text("#").frame(width: PointsColumn.rank, alignment: .leading)
                Text("Team").frame(maxWidth: .infinity, alignment: .leading)
                figure("P", PointsColumn.small); figure("W", PointsColumn.small); figure("L", PointsColumn.small)
                if showTNR { figure("T/NR", PointsColumn.tnr) }
                figure("NRR", PointsColumn.nrr); figure("Pts", PointsColumn.points)
            }
            .font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary)
            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
            Divider().overlay(BouncerColor.divider)
        }
        .accessibilityHidden(true)
    }

    private func figure(_ text: String, _ width: CGFloat) -> some View {
        Text(text).lineLimit(1).frame(width: width, alignment: .trailing)
    }
}

private enum PointsColumn {
    static let rank: CGFloat = 24, small: CGFloat = 28, tnr: CGFloat = 40, nrr: CGFloat = 56, points: CGFloat = 40
}

private struct PointsRowView: View {
    let row: PointsRow
    let rank: Int
    let showTNR: Bool

    private var form: [String] { Array(row.form.suffix(5)) }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 0) {
                Text("\(rank)").font(.numeric).foregroundStyle(BouncerColor.inkSecondary)
                    .frame(width: PointsColumn.rank, alignment: .leading)
                HStack(spacing: Spacing.sm) {
                    TeamCrest(name: row.name, acronym: row.shortName, imageURL: row.logoURL, size: 24)
                    Text(row.name).font(.titleSmall.weight(.semibold)).foregroundStyle(BouncerColor.inkPrimary)
                        .lineLimit(1).minimumScaleFactor(0.8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                figure(row.played, PointsColumn.small, BouncerColor.inkSecondary)
                figure(row.won, PointsColumn.small, BouncerColor.inkPrimary)
                figure(row.lost, PointsColumn.small, BouncerColor.inkSecondary)
                if showTNR { figure(row.tiedOrNoResult, PointsColumn.tnr, BouncerColor.inkSecondary) }
                Text(PointsFormat.nrr(row.nrr)).font(.numeric).foregroundStyle(BouncerColor.inkSecondary)
                    .lineLimit(1).frame(width: PointsColumn.nrr, alignment: .trailing)
                Text("\(row.points)").font(.scoreMedium).foregroundStyle(BouncerColor.inkPrimary)
                    .lineLimit(1).frame(width: PointsColumn.points, alignment: .trailing)
            }
            .tabularFigures()
            if !form.isEmpty {
                HStack(spacing: 6) {
                    ForEach(Array(form.enumerated()), id: \.offset) { _, letter in
                        Text(letter.uppercased()).font(.bouncer(11, .bold, relativeTo: .caption2)).foregroundStyle(tint(letter))
                    }
                }
                .padding(.leading, PointsColumn.rank + 24 + Spacing.sm)
            }
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spoken)
    }

    private func figure(_ value: Int, _ width: CGFloat, _ color: Color) -> some View {
        Text("\(value)").font(.numeric).foregroundStyle(color).lineLimit(1).frame(width: width, alignment: .trailing)
    }

    private func tint(_ letter: String) -> Color {
        switch letter.uppercased() {
        case "W": BouncerColor.win
        case "L": BouncerColor.live
        default: BouncerColor.inkSecondary
        }
    }

    private var spoken: String {
        var s = "\(rank). \(row.name). Played \(row.played), won \(row.won), lost \(row.lost)."
        if showTNR { s += " Tied or no result \(row.tiedOrNoResult)." }
        s += " Net run rate \(PointsFormat.nrr(row.nrr)). \(row.points) points."
        return s
    }
}
