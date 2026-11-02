import SwiftUI

@main
struct BouncerApp: App {
    @StateObject private var liveActivityManager = LiveActivityManager()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(liveActivityManager)
        }
    }
}

