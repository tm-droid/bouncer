import ActivityKit

@main
struct BouncerApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    // Minimal test live activity
                    Task {
                        let attributes = CricketMatchAttributes(matchId: "test", team1Name: "A", team2Name: "B", matchType: "Test")
                        let contentState = CricketMatchAttributes.ContentState(
                            matchId: "test",
                            team1Score: "100/2",
                            team2Score: "90/1",
                            currentInnings: "A Batting",
                            lastUpdated: Date(),
                            alertType: nil,
                            batsmen: nil,
                            bowler: nil,
                            recentBalls: ["1", "4", "W", "6"]
                        )
                        do {
                            _ = try Activity<CricketMatchAttributes>.request(attributes: attributes, contentState: contentState, pushType: nil)
                            print("Live activity started (test)")
                            print(Activity<CricketMatchAttributes>.activities)
                        } catch {
                            print("Failed to start live activity (test): \(error)")
                        }
                    }
                }
        }
    }
} 