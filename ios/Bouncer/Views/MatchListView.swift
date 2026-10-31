import SwiftUI
import ActivityKit
// Remove BouncerKit import since module is not found
struct MatchListView: View {
    @EnvironmentObject private var liveActivityManager: LiveActivityManager
    
    var body: some View {
        List(liveActivityManager.currentMatches) { match in
            MatchRow(match: match)
                .environmentObject(liveActivityManager)
        }
    }
}