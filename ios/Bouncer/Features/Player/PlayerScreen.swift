import SwiftUI

/// Player profile. `playerId` is Cricbuzz's profile id when known (squads); scorecard taps only have a name.
struct PlayerScreen: View {
    let playerId: String?
    let name: String

    private enum Phase { case loading, loaded(PlayerProfile), failed }

    @State private var phase: Phase = .loading
    @State private var format: String?
    @State private var attempt = 0

    var body: some View {
        ScrollView {
            switch phase {
            case .loading:
                PlayerSkeleton()
            case .failed:
                EmptyState(title: "Couldn't load \(name)", message: "The profile isn't available right now. Check your connection and try again.",
                           systemImage: "person.crop.circle.badge.exclamationmark", actionTitle: "Retry") { attempt += 1 }
            case .loaded(let profile):
                PlayerProfileContent(profile: profile, format: $format)
            }
        }
        .background(BouncerColor.bg)
        .navigationTitle(playerNameWithoutRoleTags(name))
        .navigationBarTitleDisplayMode(.inline)
        .task(id: attempt) { await load() }
    }

    private func load() async {
        phase = .loading
        do {
            let profile = try await PlayerRepository.shared.profile(id: playerId, name: name)
            PlayerImages.shared.register(id: profile.id, name: profile.name, url: profile.imageURL)
            // Open on the first format that has stats.
            format = profile.formatsWithStats.first
            phase = .loaded(profile)
        } catch is CancellationError {
        } catch {
            phase = .failed
        }
    }
}

private struct PlayerProfileContent: View {
    let profile: PlayerProfile
    @Binding var format: String?

    var body: some View {
        let formats = profile.formatsWithStats
        let selected = format.flatMap { formats.contains($0) ? $0 : nil } ?? formats.first
        VStack(alignment: .leading, spacing: 0) {
            PlayerHeader(profile: profile).padding(Spacing.lg)
            PlayerFacts(profile: profile)
            // Only formats with figures: a tab that leads to "no stats" is a dead end.
            if formats.count > 1 {
                TextTabBar(tabs: formats, selection: Binding(
                    get: { selected.flatMap(formats.firstIndex(of:)) ?? 0 },
                    set: { format = formats[$0] }))
            }
            if let selected, let stats = profile.stats[selected] {
                PlayerFormatStats(stats: stats, format: selected)
            } else {
                Text("No figures for this player yet").font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary)
                    .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.xl)
            }
            PlayerRecentForm(profile: profile)
            PlayerBio(profile: profile)
        }
        .padding(.bottom, Spacing.xl)
    }
}

private struct PlayerSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            HStack(spacing: Spacing.lg) {
                ShimmerBar(width: 72, height: 72).clipShape(Circle())
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    ShimmerBar(width: 160, height: 22)
                    ShimmerBar(width: 110, height: 14)
                }
            }
            ShimmerBar(height: 120)
            ShimmerBar(height: 180)
        }
        .padding(Spacing.lg)
        .accessibilityElement().accessibilityLabel("Loading")
    }
}
