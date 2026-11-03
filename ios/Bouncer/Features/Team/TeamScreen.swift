import SwiftUI

/// Team page, keyed by the team's display name (as used by favourites and TeamLogos.json).
struct TeamScreen: View {
    let name: String
    @Environment(AppEnvironment.self) private var env
    @State private var model: TeamModel
    @State private var favouritesFull = false

    init(name: String) {
        self.name = name
        _model = State(initialValue: TeamModel(team: name))
    }

    private var shown: String { model.name }
    private var acronym: String { TeamAcronym.make(shown) }
    private var isFavourite: Bool { env.favourites.isFavourite(name) }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                header
                matches
                records
                lastEleven
            }
            .padding(.bottom, Spacing.xl)
        }
        .background(BouncerColor.bg)
        .navigationTitle(shown)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: toggleFavourite) {
                    Image(systemName: isFavourite ? "star.fill" : "star")
                        .foregroundStyle(isFavourite ? BouncerColor.goldInk : BouncerColor.inkPrimary)
                }
                .accessibilityLabel(isFavourite ? "Remove from favourites" : "Add to favourites")
            }
        }
        .alert("Favourites are full", isPresented: $favouritesFull) {
            Button("OK", role: .cancel) {}
        } message: { Text("Remove a team first.") }
        .task(id: name) { await model.loadMatches(repo: env.matches) }
        .task(id: name) { await model.loadRecords() }
        .refreshable { await model.loadMatches(repo: env.matches, force: true) }
    }

    private func toggleFavourite() {
        if !isFavourite && env.favourites.teams.count >= FavouritesStore.maxTeams { favouritesFull = true; return }
        env.favourites.toggle(name)
    }

    // MARK: - Sections

    private var header: some View {
        VStack(spacing: 0) {
            HStack(spacing: Spacing.lg) {
                TeamCrest(name: shown, acronym: acronym, imageURL: TeamLogoLoader.shared.getLogoUrl(for: shown).flatMap(URL.init(string:)),
                          size: 56)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(shown).font(.headlineMedium).foregroundStyle(BouncerColor.inkPrimary)
                    if acronym.caseInsensitiveCompare(shown) != .orderedSame {
                        Text(acronym).font(.labelLarge).foregroundStyle(BouncerColor.inkSecondary)
                    }
                    if !model.form.isEmpty { FormStrip(results: model.form).padding(.top, Spacing.xs) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(Spacing.lg)
            Divider().overlay(BouncerColor.divider)
        }
        .background(BouncerColor.surface)
    }

    @ViewBuilder private var matches: some View {
        if !model.loaded && model.current.isEmpty && model.results.isEmpty {
            group(nil) { SkeletonRow(); Divider(); SkeletonRow(); Divider(); SkeletonRow() }
        } else if model.current.isEmpty && model.results.isEmpty {
            EmptyState(title: "No current matches", message: "Live, upcoming and recent \(shown) matches will appear here",
                       systemImage: "sportscourt")
        } else {
            if !model.current.isEmpty {
                group("Live and upcoming") { matchRows(model.current) }
            }
            if !model.results.isEmpty {
                group("Results") { matchRows(model.results) }
            }
        }
    }

    private func matchRows(_ list: [MatchSummary]) -> some View {
        ForEach(list) { MatchRow(match: $0, isFavourite: env.favourites.matches($0)) }
    }

    @ViewBuilder private var records: some View {
        if !model.records.isEmpty {
            SectionHeader(title: "Records").padding(.top, Spacing.md)
            if model.records.contains(where: \.fromArchive) {
                Text("Counted from Bouncer's own archive of scorecards, not Cricsheet.")
                    .font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary)
                    .padding(.horizontal, Spacing.lg).padding(.bottom, Spacing.sm)
            }
            BouncerCard {
                VStack(spacing: 0) {
                    ForEach(Array(model.records.enumerated()), id: \.offset) { i, r in
                        StatRow(label: r.format, value: r.summary, showDivider: i < model.records.count - 1)
                    }
                }
            }
            .padding(.horizontal, Spacing.lg)
        }
    }

    @ViewBuilder private var lastEleven: some View {
        if let last = model.results.first, !model.lastEleven.isEmpty {
            let opponent = last.teams.first { !TeamPage.isTeam($0.name, name) }
            group("Last XI, against \(opponent?.acronym ?? "")") {
                ForEach(model.lastEleven) { p in
                    NavigationLink(value: Route.player(id: p.id.isEmpty ? nil : p.id, name: p.name)) { elevenRow(p) }
                        .buttonStyle(.plain)
                    Divider().overlay(BouncerColor.divider)
                }
            }
        }
    }

    private func elevenRow(_ p: SquadPlayer) -> some View {
        let marks = [p.captain ? "c" : nil, p.keeper ? "wk" : nil].compactMap { $0 }
        return VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(marks.isEmpty ? p.name : "\(p.name) (\(marks.joined(separator: ", ")))")
                .font(.bodyMedium).foregroundStyle(BouncerColor.inkPrimary).lineLimit(1)
            if !p.role.isEmpty { Text(p.role).font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1) }
        }
        .padding(.horizontal, Spacing.rowH).padding(.vertical, Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(BouncerColor.surface)
        .contentShape(Rectangle())
    }

    /// Optional header over a full-bleed surface group with a top hairline.
    private func group<C: View>(_ title: String?, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title { SectionHeader(title: title).padding(.top, Spacing.md) }
            VStack(spacing: 0) { content() }
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(BouncerColor.surface)
                .overlay(alignment: .top) { Divider().overlay(BouncerColor.divider) }
        }
    }
}
