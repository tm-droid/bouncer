import SwiftUI

struct MatchDetailScreen: View {
    let matchId: String
    @Environment(AppEnvironment.self) private var env
    @Environment(\.scenePhase) private var scenePhase
    @State private var store: MatchDetailStore?
    @State private var selectedTab: DetailTab?
    @State private var collapsed = false
    @State private var heroHeight: CGFloat = 260
    @State private var squads: Squads?
    @State private var squadsLoading = false
    @State private var following = false

    private var shareURL: URL? { URL(string: "https://www.cricbuzz.com/live-cricket-scores/\(matchId)") }

    var body: some View {
        Group {
            if let store { content(store) } else { loading }
        }
        .background(BouncerColor.bg)
        .navigationTitle(store.map { HeroModel(store: $0).navTitle } ?? "Match")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let store, store.phase == .live, let summary = store.summary {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { toggleFollow(store, summary) } label: { Image(systemName: following ? "bell.fill" : "bell") }
                        .accessibilityLabel(following ? "Stop following on Lock Screen" : "Follow on Lock Screen")
                }
            }
            if let shareURL {
                ToolbarItem(placement: .topBarTrailing) { ShareLink(item: shareURL) }
            }
        }
        .task(id: matchId) {
            following = LiveActivityController.shared.isActive(matchId: matchId)
            if store == nil {
                let summary = await env.matches.cachedMatch(id: matchId)
                store = MatchDetailStore(matchId: matchId, summary: summary)
                Task { await loadSquads() }
            }
            await store?.start()
        }
        .onDisappear { Task { await store?.stop() } }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { following = LiveActivityController.shared.isActive(matchId: matchId) }
            Task { await store?.setActive(phase == .active) }
        }
        // The activity ends itself when the match does, and can be dismissed from the Lock Screen.
        .onChange(of: store?.phase) { _, _ in following = LiveActivityController.shared.isActive(matchId: matchId) }
    }

    /// Starts or ends the Live Activity; a new one is seeded with the freshest payload, not the list snapshot.
    private func toggleFollow(_ store: MatchDetailStore, _ summary: MatchSummary) {
        let controller = LiveActivityController.shared
        if following {
            controller.end(matchId: matchId)
            following = false
        } else {
            following = controller.start(summary: summary)
            if following, let live = store.live { controller.update(matchId: matchId, payload: live) }
        }
    }

    private var loading: some View {
        ScrollView { VStack(spacing: 0) { heroSkeleton; DetailSkeleton() } }
    }

    private var heroSkeleton: some View {
        HStack {
            ForEach(0..<2, id: \.self) { _ in
                VStack(spacing: Spacing.sm) {
                    ShimmerBar(width: 56, height: 56).clipShape(Circle())
                    ShimmerBar(width: 40, height: 14)
                    ShimmerBar(width: 80, height: 32)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(Spacing.lg).background(BouncerColor.surface)
    }

    @ViewBuilder private func content(_ store: MatchDetailStore) -> some View {
        let hero = HeroModel(store: store)
        let graphs = GraphsModel(store: store)
        let showGraphs = (hero.teams.count >= 2 && graphs.hasWinProbability) || graphs.worm != nil || !graphs.wheels.isEmpty
        let tabs = DetailTab.tabs(for: hero.phase, graphs: showGraphs, highlights: !store.highlights.isEmpty)
        let current = selectedTab.flatMap { tabs.contains($0) ? $0 : nil } ?? tabs[0]
        let hasData = store.summary != nil || store.live != nil || store.context != nil
        VStack(spacing: 0) {
            if store.isQuotaBlocked && hasData {
                StaleBanner { Task { await store.catchUp() } }
            }
            ScrollView {
                LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
                    Group {
                        if hero.teams.count >= 2 {
                            MatchHero(model: hero, title: store.summary.map(MatchRowText.header) ?? "")
                        } else { heroSkeleton }
                    }
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { heroHeight = $0 }
                    Section {
                        if case .failed = store.loadState, !hasData {
                            DetailMessage(title: "Couldn't load this match",
                                          message: store.isQuotaBlocked ? "Live data is temporarily unavailable." : "Check your connection and try again.",
                                          systemImage: "wifi.exclamationmark", actionTitle: "Retry") {
                                Task { await store.stop(); await store.start() }
                            }
                        } else {
                            tabContent(current, store: store, hero: hero, graphs: graphs)
                        }
                    } header: {
                        TextTabBar(tabs: tabs.map(title), selection: Binding(
                            get: { tabs.firstIndex(of: current) ?? 0 },
                            set: { selectedTab = tabs[$0] }))
                    }
                }
            }
            .onScrollGeometryChange(for: Bool.self) { geo in
                geo.contentOffset.y + geo.contentInsets.top > heroHeight * 0.6
            } action: { _, isCollapsed in
                withAnimation(.snappy(duration: 0.2)) { collapsed = isCollapsed }
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                if collapsed && hero.teams.count >= 2 {
                    CollapsedHeroBar(model: hero).transition(.opacity)
                }
            }
        }
        .task(id: hero.phase == .upcoming ? nil : hero.highlightTarget) { await store.loadHighlights(for: hero.highlightTarget) }
    }

    private func title(_ tab: DetailTab) -> String {
        tab == .squads && squads?.hasPlayingXI == true ? "Playing XI" : tab.title
    }

    @ViewBuilder private func tabContent(_ tab: DetailTab, store: MatchDetailStore, hero: HeroModel, graphs: GraphsModel) -> some View {
        switch tab {
        case .live: LiveTab(store: store) { selectedTab = .commentary }
        case .summary: SummaryTab(store: store, hero: hero)
        case .scorecard: ScorecardTab(store: store)
        case .commentary: CommentaryTab(store: store)
        case .graphs: GraphsTab(model: graphs, teams: hero.teams)
        case .squads: SquadsTab(squads: squads, isLoading: squadsLoading, context: store.context) { Task { await loadSquads() } }
        case .info: InfoTab(store: store)
        case .highlights: HighlightsTab(clips: store.highlights)
        }
    }

    private func loadSquads() async {
        squadsLoading = true
        squads = await SquadsRepository.shared.squads(matchId: matchId)
        squadsLoading = false
    }
}
