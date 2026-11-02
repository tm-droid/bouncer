import Foundation
import Observation

/// Shared dependencies, created once in `BouncerApp` and injected with `.environment(...)`.
@Observable @MainActor
final class AppEnvironment {
    let matches: MatchRepository
    let news: NewsRepository
    let series: SeriesRepository
    let favourites: FavouritesStore

    init(matches: MatchRepository = .shared, news: NewsRepository = .shared,
         series: SeriesRepository = .shared, favourites: FavouritesStore? = nil) {
        self.matches = matches; self.news = news; self.series = series; self.favourites = favourites ?? FavouritesStore()
    }
}
