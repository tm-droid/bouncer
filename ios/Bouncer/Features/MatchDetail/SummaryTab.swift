import SwiftUI

struct SummaryTab: View {
    let store: MatchDetailStore
    let hero: HeroModel

    var body: some View {
        VStack(spacing: 0) {
            result
            if let card = store.scorecard, !card.innings.isEmpty {
                ForEach(Array(card.innings.enumerated()), id: \.offset) { _, inn in performers(inn) }
            } else if store.loadState == .loading {
                DetailSkeleton(rows: 4)
            }
        }
        .padding(.top, Spacing.sm)
    }

    @ViewBuilder private var result: some View {
        if !hero.status.isEmpty {
            DetailSection(title: "Result") {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(hero.status).font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary)
                    ForEach(hero.teams) { t in
                        if t.hasScore {
                            HStack {
                                Text(t.name).font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary)
                                Spacer()
                                Text(t.score).font(.numeric).tabularFigures().foregroundStyle(BouncerColor.inkPrimary)
                            }
                        }
                    }
                }
                .padding(Spacing.lg)
                .accessibilityElement(children: .combine)
            }
        }
    }

    private func performers(_ inn: Innings) -> some View {
        let bats = inn.batsmen.filter { Int($0.runs) != nil }.sorted { (Int($0.runs) ?? 0) > (Int($1.runs) ?? 0) }.prefix(2)
        let bowls = inn.bowlers.filter { Int($0.wickets) != nil }.sorted {
            let a = Int($0.wickets) ?? 0, b = Int($1.wickets) ?? 0
            return a != b ? a > b : (Double($0.economy) ?? 99) < (Double($1.economy) ?? 99)
        }.prefix(2)
        return DetailSection(title: "\(inn.teamName) \(inn.scoreSlashed)") {
            VStack(spacing: 0) {
                ForEach(Array(bats.enumerated()), id: \.offset) { i, b in
                    row(b.name, "\(b.runs) (\(b.balls))", "SR \(b.strikeRate)", divider: i < bats.count - 1 || !bowls.isEmpty)
                }
                ForEach(Array(bowls.enumerated()), id: \.offset) { i, b in
                    row(b.name, "\(b.wickets)/\(b.runs)", "\(b.overs) ov · Econ \(b.economy)", divider: i < bowls.count - 1)
                }
            }
        }
    }

    private func row(_ name: String, _ main: String, _ sub: String, divider: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary).lineLimit(2)
                    Text(sub).font(.bodySmall).tabularFigures().foregroundStyle(BouncerColor.inkSecondary)
                }
                Spacer()
                Text(main).font(.scoreMedium).tabularFigures().foregroundStyle(BouncerColor.inkPrimary)
            }
            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
            .accessibilityElement(children: .combine)
            if divider { Divider().overlay(BouncerColor.divider) }
        }
    }
}
