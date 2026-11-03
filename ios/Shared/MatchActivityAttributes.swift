import ActivityKit
import Foundation

/// Where a match is, as far as the Live Activity cares.
enum ActivityPhase: String, Codable, Hashable {
    case upcoming, live, finished
}

/// Live Activity for one match. Static fields are fixed when the activity starts; `ContentState`
/// is what the app rewrites on every score change. Sides are numbered in list order (`teams[0]` is
/// side 1), not in the order they batted.
struct MatchActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var team1Score: String = ""
        var team1Overs: String = ""
        var team2Score: String = ""
        var team2Overs: String = ""
        /// 1 or 2 for the side at the crease, 0 when nobody is (innings break, finished, upcoming).
        var battingSide: Int = 0
        var statusLine: String = ""
        /// Last deliveries of the current over, oldest first ("1", "W", "4", "1wd").
        var recentBalls: [String] = []
        var phase: ActivityPhase = .live
    }

    var matchId: String
    var team1Id: String = ""
    var team1Name: String
    var team1Acronym: String
    var team2Id: String = ""
    var team2Name: String
    var team2Acronym: String
    var seriesName: String = ""
    var matchFormat: String = ""
}
