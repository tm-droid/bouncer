import Foundation

struct Achievement: Identifiable, Hashable {
    enum Kind: String, Codable, CaseIterable {
        // Batting
        case runs30, runs50, runs75, runs100, runs150, runs200, runs250, runs300
        case fastestFifty, fastestHundred, mostSixes
        // Bowling
        case wickets3, wickets4, wickets5, wickets10, maidenOver, hatTrick, bestEconomy
        // Team
        case team200, team10WktWin, successfulChase
        // Special
        case playerOfMatch, recordPartnership
    }
    let id: Kind
    let title: String
    let description: String
    let icon: String
    var unlocked: Bool = false
}

extension Achievement {
    static let all: [Achievement] = [
        // Batting
        Achievement(id: .runs30, title: "30 Runs", description: "Score 30 runs in an innings", icon: "3️⃣0️⃣"),
        Achievement(id: .runs50, title: "Fifty", description: "Score 50 runs in an innings", icon: "🏅"),
        Achievement(id: .runs75, title: "75 Runs", description: "Score 75 runs in an innings", icon: "7️⃣5️⃣"),
        Achievement(id: .runs100, title: "Century", description: "Score 100 runs in an innings", icon: "🎖️"),
        Achievement(id: .runs150, title: "150 Runs", description: "Score 150 runs in an innings", icon: "1️⃣5️⃣0️⃣"),
        Achievement(id: .runs200, title: "Double Century", description: "Score 200 runs in an innings", icon: "💯💯"),
        Achievement(id: .runs250, title: "250 Runs", description: "Score 250 runs in an innings", icon: "2️⃣5️⃣0️⃣"),
        Achievement(id: .runs300, title: "Triple Century", description: "Score 300 runs in an innings", icon: "3️⃣0️⃣0️⃣"),
        Achievement(id: .fastestFifty, title: "Fastest Fifty", description: "Score 50 runs in record balls", icon: "⚡️5️⃣0️⃣"),
        Achievement(id: .fastestHundred, title: "Fastest Hundred", description: "Score 100 runs in record balls", icon: "⚡️💯"),
        Achievement(id: .mostSixes, title: "Most Sixes", description: "Hit the most sixes in a match", icon: "6️⃣"),
        // Bowling
        Achievement(id: .wickets3, title: "Three-for", description: "Take 3 wickets in an innings", icon: "🎯"),
        Achievement(id: .wickets4, title: "Four-for", description: "Take 4 wickets in an innings", icon: "4️⃣"),
        Achievement(id: .wickets5, title: "Five-for", description: "Take 5 wickets in an innings", icon: "5️⃣"),
        Achievement(id: .wickets10, title: "10 Wickets", description: "Take 10 wickets in a match", icon: "🔟"),
        Achievement(id: .maidenOver, title: "Maiden Over", description: "Bowl a maiden over", icon: "🦢"),
        Achievement(id: .hatTrick, title: "Hat-trick", description: "Take 3 wickets in 3 balls", icon: "🎩"),
        Achievement(id: .bestEconomy, title: "Best Economy", description: "Best economy rate in a match", icon: "💸"),
        // Team
        Achievement(id: .team200, title: "200+ Team Total", description: "Team scores 200+ runs", icon: "2️⃣0️⃣0️⃣"),
        Achievement(id: .team10WktWin, title: "10 Wicket Win", description: "Win by 10 wickets", icon: "🔟🏏"),
        Achievement(id: .successfulChase, title: "Successful Chase", description: "Chase down a target", icon: "🏃‍♂️"),
        // Special
        Achievement(id: .playerOfMatch, title: "Player of the Match", description: "Awarded Player of the Match", icon: "⭐️"),
        Achievement(id: .recordPartnership, title: "Record Partnership", description: "Record partnership in a match", icon: "🤝")
    ]
}

extension Achievement.Kind {
    static let userDefaultsKey = "unlockedAchievements"
    static func loadUnlocked() -> Set<Achievement.Kind> {
        guard let data = UserDefaults.standard.data(forKey: userDefaultsKey),
              let raw = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return Set(raw.compactMap { Achievement.Kind(rawValue: $0) })
    }
    static func saveUnlocked(_ set: Set<Achievement.Kind>) {
        let raw = set.map { $0.rawValue }
        if let data = try? JSONEncoder().encode(raw) {
            UserDefaults.standard.set(data, forKey: userDefaultsKey)
        }
    }
} 