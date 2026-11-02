import SwiftUI

struct RootView: View {
    private enum Tab: Hashable { case home, series, news, more }

    @State private var selection: Tab = .home
    @State private var homePath: [Route] = []
    @State private var seriesPath: [Route] = []
    @State private var newsPath: [Route] = []
    @State private var morePath: [Route] = []

    var body: some View {
        TabView(selection: $selection) {
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
    }

    private func stack<Content: View>(_ path: Binding<[Route]>, @ViewBuilder _ root: () -> Content) -> some View {
        NavigationStack(path: path) {
            root()
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .match(let id): MatchDetailScreen(matchId: id)
                    case .news(let id): NewsArticleScreen(storyId: id)
                    case .series(let id): SeriesMatchesScreen(seriesId: id)
                    }
                }
        }
    }
}
