import Foundation
import SwiftUI

class FavouriteTeamsManager: ObservableObject {
    @Published var favourites: [String] = [] // Team abbreviations, e.g. ["IND", "AUS"]

    private let key = "favouriteTeams"
    private let appGroupID = "group.Testing.Bouncer"

    init() {
        load()
    }

    func add(team: String) {
        if !favourites.contains(team) && favourites.count < 10 {
            favourites.append(team)
            save()
        }
    }

    func remove(team: String) {
        if let idx = favourites.firstIndex(of: team) {
            favourites.remove(at: idx)
            save()
        }
    }

    func move(fromOffsets: IndexSet, toOffset: Int) {
        favourites.move(fromOffsets: fromOffsets, toOffset: toOffset)
        save()
    }

    func isFavourite(_ team: String) -> Bool {
        favourites.contains(team)
    }

    private func save() {
        if let defaults = UserDefaults(suiteName: appGroupID) {
            defaults.set(favourites, forKey: key)
        }
    }

    private func load() {
        if let defaults = UserDefaults(suiteName: appGroupID),
           let saved = defaults.stringArray(forKey: key) {
            favourites = saved
        }
    }
} 