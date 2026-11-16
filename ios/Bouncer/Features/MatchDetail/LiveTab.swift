import SwiftUI

struct LiveTab: View {
    let store: MatchDetailStore
    var showCommentary: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            if let live = store.live, !live.batsmen.isEmpty || live.bowler != nil {
                if !live.batsmen.isEmpty { crease(live) }
                if let b = live.bowler, !b.name.isEmpty { bowler(b) }
            } else {
                DetailMessage(title: "Waiting for play", message: "Batters and bowler show up once the next ball is bowled.",
                              systemImage: "figure.cricket")
            }
            recent
        }
        .padding(.top, Spacing.sm)
    }

    private func crease(_ live: LiveDetailPayload) -> some View {
        DetailSection(title: "At the crease") {
            VStack(spacing: 0) {
                header(["R (B)", "4s", "6s", "SR"], widths: [64, 30, 30, 48])
                ForEach(Array(live.batsmen.enumerated()), id: \.offset) { i, b in
                    NavigationLink(value: Route.player(named: b.name)) {
                        HStack(spacing: Spacing.xs) {
                            HStack(spacing: 6) {
                                Image(systemName: "circle.fill").font(.system(size: 6))
                                    .foregroundStyle(b.isStriker ? BouncerColor.inkPrimary : .clear).accessibilityHidden(true)
                                PlayerAvatar(name: b.name, size: 32)
                                Text(b.name).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary).lineLimit(2)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            StatCell(text: "\(b.runs) (\(b.balls))", width: 64, emphasised: true)
                            StatCell(text: b.fours, width: 30)
                            StatCell(text: b.sixes, width: 30)
                            StatCell(text: b.strikeRate, width: 48)
                        }
                        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
                        .contentShape(Rectangle())
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("\(b.name)\(b.isStriker ? ", on strike" : ""), \(b.runs) runs from \(b.balls) balls, \(b.fours) fours, \(b.sixes) sixes, strike rate \(b.strikeRate)")
                    }
                    .buttonStyle(.plain)
                    if i < live.batsmen.count - 1 { Divider().overlay(BouncerColor.divider) }
                }
                if let p = partnership(live) {
                    Divider().overlay(BouncerColor.divider)
                    HStack {
                        Text("Partnership").font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary)
                        Spacer()
                        Text("\(p.runs) (\(p.balls))").font(.numeric).tabularFigures().foregroundStyle(BouncerColor.inkPrimary)
                    }
                    .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }

    /// Current stand: the scorecard's newest partnership, only when it is between the two batters at the crease.
    private func partnership(_ live: LiveDetailPayload) -> Partnership? {
        guard live.batsmen.count == 2, let p = store.scorecard?.innings.last?.partnerships.last, !p.runs.isEmpty else { return nil }
        func norm(_ s: String) -> String { s.lowercased().filter(\.isLetter) }
        let at = Set(live.batsmen.map { norm($0.name) })
        return at.contains(norm(p.player1)) && at.contains(norm(p.player2)) ? p : nil
    }

    private func bowler(_ b: Bowler) -> some View {
        DetailSection(title: "Bowling") {
            VStack(spacing: 0) {
                header(["O-M-R-W", "Econ"], widths: [84, 48])
                NavigationLink(value: Route.player(named: b.name)) {
                    HStack(spacing: Spacing.xs) {
                        HStack(spacing: Spacing.sm) {
                            PlayerAvatar(name: b.name, size: 32)
                            Text(b.name).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary).lineLimit(2)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        StatCell(text: "\(b.overs)-\(b.maidens)-\(b.runs)-\(b.wickets)", width: 84, emphasised: true)
                        StatCell(text: b.economy, width: 48)
                    }
                    .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(b.name), \(b.overs) overs, \(b.maidens) maidens, \(b.runs) runs, \(b.wickets) wickets, economy \(b.economy)")
            }
        }
    }

    private func header(_ labels: [String], widths: [CGFloat]) -> some View {
        HStack(spacing: Spacing.xs) {
            Spacer()
            ForEach(Array(labels.enumerated()), id: \.offset) { i, l in
                Text(l).font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
                    .frame(width: widths[i], alignment: .trailing)
            }
        }
        .padding(.horizontal, Spacing.lg).padding(.top, Spacing.sm)
        .accessibilityHidden(true)
    }

    @ViewBuilder private var recent: some View {
        SectionHeader(title: "Recent commentary", action: "All commentary", onAction: showCommentary)
        if store.commentary.isEmpty {
            Text(store.loadState == .loading ? "Loading…" : "Commentary will appear here.")
                .font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        } else {
            VStack(spacing: 0) {
                ForEach(store.commentary.prefix(5), id: \.sortKey) { e in
                    CommentaryLine(entry: e)
                    Divider().overlay(BouncerColor.divider)
                }
            }
            .background(BouncerColor.surface)
        }
    }
}
