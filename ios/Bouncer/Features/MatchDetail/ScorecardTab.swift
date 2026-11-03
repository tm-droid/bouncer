import SwiftUI

struct ScorecardTab: View {
    let store: MatchDetailStore
    @State private var selected: Int?
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        if let card = store.scorecard, !card.innings.isEmpty {
            let idx = min(selected ?? card.innings.count - 1, card.innings.count - 1)
            VStack(spacing: 0) {
                picker(card, idx)
                inningsView(card.innings[idx])
            }
            .padding(.top, Spacing.sm)
        } else if store.loadState == .loading || store.loadState == .idle {
            DetailSkeleton(rows: 6)
        } else {
            DetailMessage(title: "No scorecard yet", message: "The scorecard appears once the first innings is under way.",
                          systemImage: "list.number", actionTitle: "Retry") { Task { await store.refreshScorecard() } }
        }
    }

    private func picker(_ card: Scorecard, _ idx: Int) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(Array(card.innings.enumerated()), id: \.offset) { i, inn in
                    let on = i == idx
                    Button { selected = i } label: {
                        Text("\(inn.teamName) \(inn.scoreSlashed)").font(.labelLarge).tabularFigures()
                            .foregroundStyle(on ? BouncerColor.onGold : BouncerColor.inkPrimary)
                            .padding(.horizontal, Spacing.md).frame(minHeight: 36)
                            .background(on ? BouncerColor.goldFill : BouncerColor.surfaceVariant, in: Capsule())
                    }
                    .buttonStyle(.plain).frame(minHeight: 44)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .padding(.horizontal, Spacing.lg)
        }
    }

    private var compact: Bool { typeSize.isAccessibilitySize }

    private func inningsView(_ inn: Innings) -> some View {
        let batted = inn.batsmen.filter { !isYetToBat($0) }
        let yet = inn.batsmen.filter(isYetToBat)
        return VStack(spacing: 0) {
            DetailSection(title: "Batting") {
                VStack(spacing: 0) {
                    colHeader(["R", "B", "4s", "6s", "SR"], widths: [34, 34, 28, 28, 48], collapsible: true)
                    ForEach(Array(batted.enumerated()), id: \.offset) { i, b in
                        batRow(b)
                        Divider().overlay(BouncerColor.divider)
                    }
                    if !inn.extras.isEmpty { summaryRow("Extras", inn.extras) }
                    if !inn.total.isEmpty { summaryRow("Total", inn.total.isEmpty ? inn.scoreSlashed : inn.total, bold: true) }
                    if !yet.isEmpty {
                        Divider().overlay(BouncerColor.divider)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Yet to bat").font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary)
                            Text(yet.map(\.name).joined(separator: ", ")).font(.bodyMedium).foregroundStyle(BouncerColor.inkPrimary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
                    }
                }
            }
            if !inn.fallOfWickets.isEmpty {
                DetailSection(title: "Fall of wickets") {
                    VStack(spacing: 0) {
                        ForEach(Array(inn.fallOfWickets.enumerated()), id: \.offset) { i, f in
                            HStack(alignment: .firstTextBaseline) {
                                Text("\(f.number > 0 ? f.number : i + 1)").font(.numeric).foregroundStyle(BouncerColor.inkSecondary)
                                    .frame(width: 24, alignment: .leading)
                                Text(f.batsman).font(.bodyMedium).foregroundStyle(BouncerColor.inkPrimary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                Text("\(f.score)\(f.overs.isEmpty ? "" : " (\(f.overs) ov)")").font(.numeric).tabularFigures()
                                    .foregroundStyle(BouncerColor.inkPrimary)
                            }
                            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
                            .accessibilityElement(children: .combine)
                            if i < inn.fallOfWickets.count - 1 { Divider().overlay(BouncerColor.divider) }
                        }
                    }
                }
            }
            if !inn.partnerships.isEmpty {
                DetailSection(title: "Partnerships") {
                    VStack(spacing: 0) {
                        ForEach(Array(inn.partnerships.enumerated()), id: \.offset) { i, p in
                            HStack(alignment: .firstTextBaseline) {
                                Text("\(p.player1) & \(p.player2)").font(.bodyMedium).foregroundStyle(BouncerColor.inkPrimary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                Text("\(p.runs) (\(p.balls))").font(.numeric).tabularFigures().foregroundStyle(BouncerColor.inkPrimary)
                            }
                            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
                            .accessibilityElement(children: .combine)
                            if i < inn.partnerships.count - 1 { Divider().overlay(BouncerColor.divider) }
                        }
                    }
                }
            }
            if !inn.bowlers.isEmpty {
                let best = bestBowler(inn.bowlers)
                DetailSection(title: "Bowling") {
                    VStack(spacing: 0) {
                        colHeader(["O", "M", "R", "W", "Econ"], widths: [34, 28, 34, 28, 48])
                        ForEach(Array(inn.bowlers.enumerated()), id: \.offset) { i, b in
                            let isBest = i == best
                            HStack(spacing: Spacing.xs) {
                                NavigationLink(value: Route.player(id: nil, name: b.name)) {
                                    Text(b.name).font(isBest ? .bouncer(14, .semibold, relativeTo: .subheadline) : .titleSmall)
                                        .foregroundStyle(BouncerColor.inkPrimary).lineLimit(2)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("\(b.name)\(isBest ? ", best bowler" : "")")
                                HStack(spacing: Spacing.xs) {
                                    StatCell(text: b.overs, width: 34)
                                    StatCell(text: b.maidens, width: 28)
                                    StatCell(text: b.runs, width: 34)
                                    StatCell(text: b.wickets, width: 28, emphasised: true)
                                    StatCell(text: b.economy, width: 48)
                                }
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel("\(b.overs) overs, \(b.maidens) maidens, \(b.runs) runs, \(b.wickets) wickets, economy \(b.economy)")
                            }
                            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
                            .background(isBest ? BouncerColor.surfaceVariant : .clear)
                            if i < inn.bowlers.count - 1 { Divider().overlay(BouncerColor.divider) }
                        }
                    }
                }
            }
        }
    }

    private func isYetToBat(_ b: BatsmanStats) -> Bool {
        b.runs.isEmpty && b.balls.isEmpty && (b.dismissal.isEmpty || b.dismissal.lowercased().contains("yet to bat"))
    }

    private func bestBowler(_ bowlers: [BowlerStats]) -> Int? {
        let ranked = bowlers.enumerated().filter { Int($0.element.wickets) != nil }.sorted {
            let a = Int($0.element.wickets) ?? 0, b = Int($1.element.wickets) ?? 0
            return a != b ? a > b : (Double($0.element.economy) ?? 99) < (Double($1.element.economy) ?? 99)
        }
        guard let top = ranked.first, (Int(top.element.wickets) ?? 0) > 0 else { return nil }
        return top.offset
    }

    private func colHeader(_ labels: [String], widths: [CGFloat], collapsible: Bool = false) -> some View {
        let shown = compact && collapsible ? Array(zip(labels, widths).enumerated().filter { [0, 1].contains($0.offset) || $0.offset == labels.count - 1 }.map(\.element)) : Array(zip(labels, widths))
        return HStack(spacing: Spacing.xs) {
            Text("").frame(maxWidth: .infinity)
            ForEach(Array(shown.enumerated()), id: \.offset) { _, c in
                StatHeaderCell(text: c.0, width: c.1)
            }
        }
        .padding(.horizontal, Spacing.lg).padding(.top, Spacing.sm).accessibilityHidden(true)
    }

    private func batRow(_ b: BatsmanStats) -> some View {
        HStack(alignment: .top, spacing: Spacing.xs) {
            NavigationLink(value: Route.player(id: nil, name: b.name)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(b.name).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary).lineLimit(2)
                    if !b.dismissal.isEmpty {
                        Text(b.dismissal).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary).lineLimit(3)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(b.name), \(b.dismissal)")
            HStack(spacing: Spacing.xs) {
                StatCell(text: b.runs, width: 34, emphasised: true)
                StatCell(text: b.balls, width: 34)
                if !compact {
                    StatCell(text: b.fours, width: 28)
                    StatCell(text: b.sixes, width: 28)
                }
                StatCell(text: b.strikeRate, width: 48)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(b.runs) runs from \(b.balls) balls, \(b.fours) fours, \(b.sixes) sixes, strike rate \(b.strikeRate)")
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
    }

    private func summaryRow(_ label: String, _ value: String, bold: Bool = false) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(label).font(bold ? .titleSmall : .bodyMedium).foregroundStyle(bold ? BouncerColor.inkPrimary : BouncerColor.inkSecondary)
                Spacer()
                Text(value).font(bold ? .scoreMedium : .numeric).tabularFigures().foregroundStyle(BouncerColor.inkPrimary)
                    .multilineTextAlignment(.trailing)
            }
            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
            .accessibilityElement(children: .combine)
            Divider().overlay(BouncerColor.divider)
        }
    }
}

private struct StatHeaderCell: View {
    let text: String
    let width: CGFloat
    @ScaledMetric(relativeTo: .caption) private var scale: CGFloat = 1
    var body: some View {
        Text(text).font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
            .frame(width: width * scale, alignment: .trailing)
    }
}
