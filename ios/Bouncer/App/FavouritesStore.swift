import Foundation
import Observation
import WidgetKit

/// Favourite teams (names or acronyms), persisted in the app-group defaults under the legacy key.
@Observable @MainActor
final class FavouritesStore {
    nonisolated static let suiteName = "group.Testing.Bouncer"
    static let key = "favouriteTeams"
    static let maxTeams = 10

    private(set) var teams: [String] = []
    @ObservationIgnored private let defaults: UserDefaults?

    init(defaults: UserDefaults? = nil) {
        let defaults = defaults ?? UserDefaults(suiteName: Self.suiteName)
        self.defaults = defaults
        teams = defaults?.stringArray(forKey: Self.key) ?? []
    }

    func isFavourite(_ name: String) -> Bool {
        teams.contains { $0.caseInsensitiveCompare(name) == .orderedSame }
    }

    func add(_ name: String) {
        guard !isFavourite(name), teams.count < Self.maxTeams else { return }
        teams.append(name)
        save()
    }

    func remove(_ name: String) {
        guard let i = teams.firstIndex(where: { $0.caseInsensitiveCompare(name) == .orderedSame }) else { return }
        teams.remove(at: i)
        save()
    }

    func toggle(_ name: String) {
        isFavourite(name) ? remove(name) : add(name)
    }

    func move(fromOffsets: IndexSet, toOffset: Int) {
        teams.move(fromOffsets: fromOffsets, toOffset: toOffset)
        save()
    }

    /// True when either side's full name or initials-acronym equals a favourite (case-insensitive).
    func matches(_ match: MatchSummary) -> Bool {
        guard !teams.isEmpty else { return false }
        let favs = Set(teams.map { $0.lowercased() })
        return match.teams.contains { team in
            let name = team.name.lowercased()
            let acronym = name.split(separator: " ").compactMap(\.first).map(String.init).joined()
            return favs.contains(name) || (!acronym.isEmpty && favs.contains(acronym))
        }
    }

    /// Widgets filter by favourites, so they reload when the list changes.
    private func save() {
        defaults?.set(teams, forKey: Self.key)
        WidgetCenter.shared.reloadAllTimelines()
    }
}
