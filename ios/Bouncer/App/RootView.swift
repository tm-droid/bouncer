import SwiftUI

struct RootView: View {
    private enum Tab: Hashable { case home, series, news, more }

    @State private var selection: Tab = .home
    @State private var homePath: [Route] = []
    @State private var seriesPath: [Route] = []
    @State private var newsPath: [Route] = []
    @State private var morePath: [Route] = []
    @Environment(AppEnvironment.self) private var env
    @AppStorage("onboardingComplete") private var onboardingComplete = false

    var body: some View {
        TabView(selection: tabSelection) {
            stack($homePath) { HomeScreen() }
                .tabItem { Label("Home", systemImage: "house") }.tag(Tab.home)
            stack($seriesPath) { SeriesScreen() }
                .tabItem { Label("Series", systemImage: "calendar") }.tag(Tab.series)
            stack($newsPath) { NewsScreen() }
                .tabItem { Label("News", systemImage: "newspaper") }.tag(Tab.news)
            stack($morePath) { MoreScreen() }
                .tabItem { Label("More", systemImage: "ellipsis") }.tag(Tab.more)
        }
        .tint(BouncerColor.goldInk)
        .onOpenURL { url in
            // Widgets and the Live Activity link to bouncer://match/{id}.
            guard url.scheme == "bouncer", url.host == "match", let id = url.pathComponents.dropFirst().first else { return }
            selection = .home
            if homePath.last != .match(id: id) { homePath.append(.match(id: id)) }
        }
        .fullScreenCover(isPresented: .constant(!onboardingComplete && env.favourites.teams.isEmpty)) {
            OnboardingScreen { onboardingComplete = true }
        }
    }

    /// Tapping the tab that is already showing pops its stack back to the root, as system apps do.
    private var tabSelection: Binding<Tab> {
        Binding(get: { selection }, set: { tapped in
            if tapped == selection {
                switch tapped {
                case .home: homePath = []
                case .series: seriesPath = []
                case .news: newsPath = []
                case .more: morePath = []
                }
            }
            selection = tapped
        })
    }

    private func stack<Content: View>(_ path: Binding<[Route]>, @ViewBuilder _ root: () -> Content) -> some View {
        NavigationStack(path: path) {
            root()
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .match(let id): MatchDetailScreen(matchId: id)
                    case .news(let id): NewsArticleScreen(storyId: id)
                    case .series(let id): SeriesDetailScreen(seriesId: id)
                    case .player(let id, let name): PlayerScreen(playerId: id, name: name)
                    case .team(let name): TeamScreen(name: name)
                    case .search: SearchScreen()
                    }
                }
        }
    }
}
