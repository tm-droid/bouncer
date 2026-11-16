import SwiftUI

struct SearchScreen: View {
    @Environment(AppEnvironment.self) private var env
    @AppStorage("search.recent") private var recentRaw = ""
    @FocusState private var focused: Bool
    @State private var query = ""
    @State private var matches: [MatchSummary] = []
    @State private var series: [SeriesSummary] = []
    @State private var results: SearchResults?
    @State private var players: [PlayerSearchResult] = []
    @State private var lookup = PlayerLookup.idle

    private enum PlayerLookup { case idle, searching, unavailable }

    private static let localDebounce = Duration.milliseconds(300)
    /// The remote leg waits for the user to stop typing; local sources answer at `localDebounce`.
    private static let playerDebounce = Duration.milliseconds(500)

    private var recents: [String] { SearchRecents.decode(recentRaw) }
    private var typed: String { SearchText.normalize(query) }

    var body: some View {
        VStack(spacing: 0) {
            field
            ScrollView {
                LazyVStack(spacing: 0, pinnedViews: []) { content }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(BouncerColor.bg)
        .navigationTitle("Search")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { focused = true }
        .task { await loadSources() }
        .task(id: query) { await run() }
    }

    private var field: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "magnifyingglass").foregroundStyle(BouncerColor.inkSecondary).accessibilityHidden(true)
            TextField("Search matches, players, teams, tournaments", text: $query)
                .font(.bodyLarge).foregroundStyle(BouncerColor.inkPrimary)
                .focused($focused).submitLabel(.search)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .onSubmit { remember(query) }
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(BouncerColor.inkSecondary).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.leading, Spacing.md)
        .frame(minHeight: 44)
        .background(BouncerColor.surfaceVariant, in: RoundedRectangle(cornerRadius: Radius.md))
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
    }

    @ViewBuilder private var content: some View {
        if typed.count < SearchRepository.minQueryLength {
            idle
        } else if let results {
            if results.isEmpty && players.isEmpty && lookup == .idle {
                EmptyState(title: "No results", message: "Nothing matches \u{201C}\(query.trimmingCharacters(in: .whitespaces))\u{201D}.")
            } else {
                ForEach(sections(results)) { $0.view }
            }
        }
    }

    // MARK: Idle

    @ViewBuilder private var idle: some View {
        if !recents.isEmpty {
            SectionHeader(title: "Recent searches", action: "Clear") { recentRaw = "" }
            ForEach(recents, id: \.self) { q in
                HStack(spacing: 0) {
                    Button { query = q } label: {
                        Text(q).font(.bodyLarge).foregroundStyle(BouncerColor.inkPrimary)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                    }
                    Button { recentRaw = SearchRecents.encode(recents.filter { $0 != q }) } label: {
                        Image(systemName: "xmark").font(.system(size: 13)).foregroundStyle(BouncerColor.inkSecondary).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Remove \(q)")
                }
                .padding(.leading, Spacing.lg)
            }
        }
        SectionHeader(title: "Trending")
        ForEach(SearchRepository.trending, id: \.self) { q in
            Button { query = q } label: {
                Text(q).font(.bodyLarge).foregroundStyle(BouncerColor.inkPrimary)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
            }
            .padding(.horizontal, Spacing.lg)
        }
    }

    // MARK: Sections

    private struct Section: Identifiable {
        let id: String
        let score: Double
        let view: AnyView
    }

    /// Sections ordered by their strongest member, as Android does; empty ones are dropped.
    private func sections(_ r: SearchResults) -> [Section] {
        var out: [Section] = []
        if !r.matches.isEmpty {
            out.append(Section(id: "m", score: r.matches.map(\.score).max() ?? 0, view: AnyView(group("Matches", r.matches.count) {
                ForEach(r.matches, id: \.item.id) { MatchRow(match: $0.item, isFavourite: env.favourites.matches($0.item)) }
            })))
        }
        if !r.teams.isEmpty {
            out.append(Section(id: "t", score: r.teams.map(\.score).max() ?? 0, view: AnyView(group("Teams", r.teams.count) {
                ForEach(r.teams, id: \.item.name) { teamRow($0.item) }
            })))
        }
        if !r.series.isEmpty {
            out.append(Section(id: "s", score: r.series.map(\.score).max() ?? 0, view: AnyView(group("Tournaments", r.series.count) {
                ForEach(r.series, id: \.item.id) { seriesRow($0.item) }
            })))
        }
        if !players.isEmpty || lookup != .idle {
            let best = players.map { SearchRepository.playerScore(query, $0.name) }.max() ?? 0
            out.append(Section(id: "p", score: best, view: AnyView(playersGroup)))
        }
        return out.enumerated().sorted { $0.element.score != $1.element.score ? $0.element.score > $1.element.score : $0.offset < $1.offset }.map(\.element)
    }

    private func group<Rows: View>(_ title: String, _ count: Int, @ViewBuilder rows: () -> Rows) -> some View {
        VStack(spacing: 0) {
            SectionHeader(title: "\(title) (\(count))")
            rows()
        }
    }

    private var playersGroup: some View {
        VStack(spacing: 0) {
            SectionHeader(title: players.isEmpty ? "Players" : "Players (\(players.count))")
            ForEach(players) { playerRow($0) }
            if lookup != .idle {
                Text(lookup == .searching ? "Searching all players\u{2026}" : "Online player search is unavailable right now")
                    .font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
            }
        }
    }

    // MARK: Rows

    private func row<Lead: View>(_ route: Route, title: String, subtitle: String, @ViewBuilder lead: () -> Lead) -> some View {
        VStack(spacing: 0) {
            NavigationLink(value: route) {
                HStack(spacing: Spacing.md) {
                    lead()
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.bodyLarge).foregroundStyle(BouncerColor.inkPrimary).lineLimit(2)
                        if !subtitle.isEmpty { Text(subtitle).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1) }
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, Spacing.rowH).padding(.vertical, Spacing.md)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(BouncerColor.surface)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            // Opening a result counts as using the query, not just pressing Search.
            .simultaneousGesture(TapGesture().onEnded { remember(query) })
            Divider().overlay(BouncerColor.divider)
        }
    }

    private func teamRow(_ t: SearchTeam) -> some View {
        let url = TeamLogoLoader.shared.getLogoUrl(for: t.name).flatMap(URL.init(string:))
        return row(.team(name: t.name), title: t.name, subtitle: t.fixtures == 0 ? "" : t.fixtures == 1 ? "1 fixture" : "\(t.fixtures) fixtures") {
            TeamCrest(name: t.name, acronym: t.acronym, imageURL: url, size: 36)
        }
    }

    private func seriesRow(_ s: SeriesSummary) -> some View {
        row(.series(id: s.id), title: s.name, subtitle: s.category) {
            Image(systemName: "trophy").foregroundStyle(BouncerColor.inkSecondary)
                .frame(width: 36, height: 36).background(BouncerColor.surfaceVariant, in: Circle())
                .accessibilityHidden(true)
        }
    }

    private func playerRow(_ p: PlayerSearchResult) -> some View {
        row(.player(id: p.id, name: p.name), title: p.name, subtitle: p.country) {
            PlayerAvatar(name: p.name, id: p.id, imageURL: p.imageURL, size: 36)
        }
    }

    // MARK: Work

    private func remember(_ q: String) { recentRaw = SearchRecents.encode(SearchRecents.adding(q, to: recents)) }

    /// Cache-first, so reopening search inside the TTL costs no download.
    private func loadSources() async {
        let lists = await env.matches.refreshAll()
        matches = MatchListKind.allCases.flatMap { lists[$0]?.matches ?? [] }
        series = (try? await env.series.activeSeries())?.series ?? []
        if typed.count >= SearchRepository.minQueryLength { results = localSearch() }
    }

    private func localSearch() -> SearchResults {
        SearchRepository.search(query, matches: matches, series: series, teamNames: Array(TeamLogoLoader.shared.teamLogos.keys).sorted(),
                                isFavourite: env.favourites.isFavourite)
    }

    /// Debounced local search, then the remote player lookup; `.task(id:)` cancels it on the next keystroke.
    private func run() async {
        guard typed.count >= SearchRepository.minQueryLength else {
            results = nil; players = []; lookup = .idle
            return
        }
        guard (try? await Task.sleep(for: Self.localDebounce)) != nil else { return }
        results = localSearch()
        guard typed.count >= SearchRepository.minPlayerQueryLength else { players = []; lookup = .idle; return }
        players = []; lookup = .searching
        guard (try? await Task.sleep(for: Self.playerDebounce)) != nil else { return }
        do {
            let hits = try await PlayerRepository.shared.search(query)
            guard !Task.isCancelled else { return }
            for h in hits { PlayerImages.shared.register(id: h.id, name: h.name, url: h.imageURL) }
            players = hits; lookup = .idle
        } catch {
            guard !Task.isCancelled else { return }
            players = []; lookup = .unavailable
        }
    }
}

#Preview("Light") {
    NavigationStack { SearchScreen() }.environment(AppEnvironment())
}

#Preview("Dark") {
    NavigationStack { SearchScreen() }.environment(AppEnvironment()).preferredColorScheme(.dark)
}
