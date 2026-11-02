let appGroupID = "group.Testing.Bouncer"
let key = "favouriteTeams"

func getFavouriteTeams() -> [String] {
    if let defaults = UserDefaults(suiteName: appGroupID),
       let saved = defaults.stringArray(forKey: key) {
        return saved
    }
    return []
} 