import SwiftUI

struct HomeScreen: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = HomeModel()
    @State private var tab = 0
    @State private var visible = false
    @AppStorage("home.competition") private var competition = HomeFilters.all
    @AppStorage("home.gender") private var gender = HomeFilters.all
    @AppStorage("home.format") private var format = HomeFilters.all
    @AppStorage("home.myTeams") private var myTeams = false

    private static let tabs = ["All", "Live", "Upcoming", "Recent"]
    private static let heroWindow: TimeInterval = 24 * 3600

    private var filters: HomeFilters { HomeFilters(competition: competition, gender: gender, format: format, myTeams: myTeams) }

    private var kinds: [MatchListKind] {
        switch tab {
        case 1: [.live, .upcoming]
        case 2: [.upcoming]
        case 3: [.recent]
        default: [.live, .upcoming, .recent]
        }
    }

    private var shownKinds: [MatchListKind] {
        switch tab {
        case 1: [.live]
        case 2: [.upcoming]
        case 3: [.recent]
        default: [.live, .upcoming, .recent]
        }
    }

    /// Filtered, deduplicated matches for the tab, in tab order (live, then soonest upcoming, then newest recent).
    private var matches: [MatchSummary] {
        var seen = Set<String>()
        return shownKinds.flatMap { kind -> [MatchSummary] in
            let list = model.lists[kind]?.matches ?? []
            switch kind {
            case .live: return list
            case .upcoming: return list.sorted { ($0.startsAt ?? .distantFuture) < ($1.startsAt ?? .distantFuture) }
            case .recent: return list.sorted { ($0.startsAt ?? .distantPast) > ($1.startsAt ?? .distantPast) }
            }
        }
        .filter { seen.insert($0.id).inserted && filters.accepts($0, favourites: env.favourites) }
    }

    private var isStale: Bool { shownKinds.contains { model.lists[$0]?.isStale == true } }
    private var hasData: Bool { shownKinds.contains { model.lists[$0] != nil } }

    private var nextUp: MatchSummary? {
        model.lists[.upcoming]?.matches
            .filter { ($0.startsAt ?? .distantPast) > .now }
            .min { ($0.startsAt ?? .distantFuture) < ($1.startsAt ?? .distantFuture) }
    }

    var body: some View {
        VStack(spacing: 0) {
            tabBar
            if isStale && hasData { StaleBanner { Task { await refresh(force: true) } } }
            content
        }
        .background(BouncerColor.bg)
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { BouncerWordmark() }
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: Route.search) { Image(systemName: "magnifyingglass") }
                    .tint(BouncerColor.inkPrimary).accessibilityLabel("Search")
            }
        }
        .onAppear { visible = true }
        .onDisappear { visible = false }
        .task(id: RefreshKey(tab: tab, active: scenePhase == .active, visible: visible)) {
            guard scenePhase == .active, visible else { return }
            await refresh()
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(tab == 1 ? 6 : 60))
                if Task.isCancelled { break }
                await refresh()
            }
        }
    }

    private struct RefreshKey: Hashable { let tab: Int; let active: Bool; let visible: Bool }

    private func refresh(force: Bool = false) async {
        await model.load(kinds, repo: env.matches, force: force)
    }

    // MARK: - Chrome

    private var tabBar: some View {
        ZStack(alignment: .topTrailing) {
            TextTabBar(tabs: Self.tabs, selection: $tab)
            filterMenu
        }
    }

    private var filterMenu: some View {
        Menu {
            Section("Competition") {
                Picker("Competition", selection: $competition) { ForEach(HomeFilters.competitions, id: \.self, content: Text.init) }
                    .pickerStyle(.inline)
            }
            Section("Teams") {
                Picker("Gender", selection: $gender) { ForEach(HomeFilters.genders, id: \.self, content: Text.init) }
                    .pickerStyle(.inline)
            }
            Section("Format") {
                Picker("Format", selection: $format) { ForEach(HomeFilters.formats, id: \.self, content: Text.init) }
                    .pickerStyle(.inline)
            }
            Toggle("My teams", isOn: $myTeams)
        } label: {
            Image(systemName: filters.isActive ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                .font(.system(size: 20))
                .foregroundStyle(filters.isActive ? BouncerColor.goldInk : BouncerColor.inkSecondary)
                .frame(width: 48, height: 48)
                .background(BouncerColor.topBar)
        }
        .accessibilityLabel("Filters")
        .accessibilityValue(filters.isActive ? "On" : "Off")
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        let shown = matches
        ScrollView {
            LazyVStack(spacing: 0) {
                if !hasData {
                    if model.loaded && model.failed {
                        EmptyState(title: "Couldn't load matches", message: "Check your connection and try again.",
                                   systemImage: "wifi.exclamationmark", actionTitle: "Retry") { Task { await refresh(force: true) } }
                    } else {
                        ForEach(0..<3, id: \.self) { _ in SkeletonRow(); Divider().overlay(BouncerColor.divider) }
                    }
                } else if shown.isEmpty {
                    emptyState
                } else {
                    rows(shown)
                }
            }
            .padding(.bottom, Spacing.xl)
        }
        .refreshable { await refresh(force: true) }
    }

    @ViewBuilder private func rows(_ shown: [MatchSummary]) -> some View {
        let hero = featured(in: shown)
        if let hero {
            HeroCard(match: hero.match, label: hero.live ? nil : "NEXT UP", isFavourite: env.favourites.matches(hero.match))
        }
        let rest = ordered(shown.filter { $0.id != hero?.match.id })
        if tab == 2 || tab == 3 {
            ForEach(dayGroups(rest, newestFirst: tab == 3), id: \.title) { group in
                SectionHeader(title: group.title).background(BouncerColor.bg)
                ForEach(group.matches) { MatchRow(match: $0, isFavourite: env.favourites.matches($0)) }
            }
        } else {
            ForEach(rest) { MatchRow(match: $0, isFavourite: env.favourites.matches($0)) }
        }
    }

    @ViewBuilder private var emptyState: some View {
        if filters.isActive {
            EmptyState(title: "No \(filters.label) matches", message: "Nothing matches your filters here.",
                       systemImage: "sportscourt", actionTitle: "Show all") {
                competition = HomeFilters.all; gender = HomeFilters.all; format = HomeFilters.all; myTeams = false
            }
        } else if tab == 1 {
            EmptyState(title: "No live matches", message: nextUpText, systemImage: "sportscourt",
                       actionTitle: "See upcoming") { withAnimation { tab = 2 } }
        } else {
            EmptyState(title: "No matches", message: "Nothing to show right now. Pull down to refresh.", systemImage: "sportscourt")
        }
    }

    private var nextUpText: String {
        guard let m = nextUp, let s = m.startsAt else { return "Check back when the next match starts." }
        let teams = m.teams.prefix(2).map(\.name).joined(separator: " v ")
        return "Next: \(teams), \(MatchRowText.day(s)) \(s.formatted(date: .omitted, time: .shortened))"
    }

    // MARK: - Ordering

    /// Hero: followed team's live match, else the soonest followed match within 24h, else any live match.
    private func featured(in list: [MatchSummary]) -> (match: MatchSummary, live: Bool)? {
        let favs = env.favourites.teams.map { $0.lowercased() }
        func rank(_ m: MatchSummary) -> Int {
            m.teams.compactMap { favs.firstIndex(of: $0.name.lowercased()) ?? favs.firstIndex(of: $0.acronym.lowercased()) }.min() ?? .max
        }
        let followed = list.filter { env.favourites.matches($0) }
        if let m = followed.filter(\.isLive).min(by: { rank($0) < rank($1) }) { return (m, true) }
        let now = Date.now
        if let m = followed.filter({ $0.phase == .upcoming && ($0.startsAt ?? .distantFuture) <= now.addingTimeInterval(Self.heroWindow) })
            .min(by: { ($0.startsAt ?? .distantFuture) < ($1.startsAt ?? .distantFuture) }) { return (m, false) }
        return list.first(where: \.isLive).map { ($0, true) }
    }

    /// Favourites first, otherwise the incoming order.
    private func ordered(_ list: [MatchSummary]) -> [MatchSummary] {
        let fav = list.filter { env.favourites.matches($0) }
        return fav + list.filter { !env.favourites.matches($0) }
    }

    private struct DayGroup { let title: String; let matches: [MatchSummary] }

    private func dayGroups(_ list: [MatchSummary], newestFirst: Bool) -> [DayGroup] {
        let cal = Calendar.current
        let dated = list.filter { $0.startsAt != nil }
        var days = Dictionary(grouping: dated) { cal.startOfDay(for: $0.startsAt!) }.map { ($0.key, $0.value) }
        days.sort { newestFirst ? $0.0 > $1.0 : $0.0 < $1.0 }
        var groups = days.map { DayGroup(title: MatchRowText.day($0.0), matches: ordered($0.1)) }
        let undated = list.filter { $0.startsAt == nil }
        if !undated.isEmpty { groups.append(DayGroup(title: "Date to be announced", matches: undated)) }
        return groups
    }
}

/// Featured match: a flat card above the list.
private struct HeroCard: View {
    let match: MatchSummary
    var label: String?
    var isFavourite: Bool

    var body: some View {
        NavigationLink(value: Route.match(id: match.id)) {
            BouncerCard {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    if let label { Text(label).font(.overline).foregroundStyle(BouncerColor.goldInk) }
                    MatchContent(match: match, isFavourite: isFavourite)
                }
                .padding(.horizontal, Spacing.rowH).padding(.vertical, Spacing.rowV)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(Spacing.lg)
    }
}
