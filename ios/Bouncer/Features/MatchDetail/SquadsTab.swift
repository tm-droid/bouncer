import SwiftUI

struct SquadsTab: View {
    let squads: Squads?
    let isLoading: Bool
    let context: MatchContext?
    var retry: () -> Void
    @State private var showBench = false

    var body: some View {
        VStack(spacing: 0) {
            if let squads {
                content(squads)
            } else if isLoading {
                DetailSkeleton(rows: 8)
            } else if let teams = fallbackTeams {
                ForEach(Array(teams.enumerated()), id: \.offset) { _, t in
                    DetailSection(title: t.name) { list(t.members.map { SquadPlayer(id: $0.cbId, name: $0.name, role: "", captain: false, keeper: false) }) }
                }
            } else {
                DetailMessage(title: "Squads unavailable", message: "Team sheets could not be loaded.",
                              systemImage: "person.3", actionTitle: "Retry", action: retry)
            }
        }
        .padding(.top, Spacing.sm)
    }

    private var fallbackTeams: [ContextTeam]? {
        let t = context?.teams.filter { !$0.members.isEmpty } ?? []
        return t.isEmpty ? nil : t
    }

    @ViewBuilder private func content(_ s: Squads) -> some View {
        let hasBench = !(s.team1.bench.isEmpty && s.team2.bench.isEmpty)
        ForEach([s.team1, s.team2], id: \.teamId) { t in
            let main = t.hasPlayingXI ? t.playingXI : t.squad
            DetailSection(title: "\(t.name) · \(t.hasPlayingXI ? "Playing XI" : "Squad")") {
                if main.isEmpty { Text("Not announced yet").font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary).padding(Spacing.lg) }
                else { list(main) }
            }
            if showBench, !t.bench.isEmpty {
                DetailSection(title: "\(t.shortName.isEmpty ? t.name : t.shortName) bench") { list(t.bench) }
            }
        }
        if hasBench {
            Button(showBench ? "Hide bench" : "Show bench") { withAnimation { showBench.toggle() } }
                .font(.labelLarge).foregroundStyle(BouncerColor.goldInk).frame(maxWidth: .infinity, minHeight: 44)
        }
    }

    private func list(_ players: [SquadPlayer]) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(players.enumerated()), id: \.offset) { i, p in
                NavigationLink(value: Route.player(id: p.id.isEmpty ? nil : p.id, name: p.name)) { playerRow(p) }
                    .buttonStyle(.plain)
                if i < players.count - 1 { Divider().overlay(BouncerColor.divider) }
            }
        }
    }

    private func playerRow(_ p: SquadPlayer) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
            Text(p.name).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary)
            if p.captain || p.keeper {
                Text([p.captain ? "(c)" : nil, p.keeper ? "(wk)" : nil].compactMap { $0 }.joined(separator: " "))
                    .font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary)
            }
            Spacer(minLength: Spacing.sm)
            if !p.role.isEmpty { Text(p.role).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary).multilineTextAlignment(.trailing) }
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(p.name)\(p.captain ? ", captain" : "")\(p.keeper ? ", wicketkeeper" : "")\(p.role.isEmpty ? "" : ", \(p.role)")")
    }
}
