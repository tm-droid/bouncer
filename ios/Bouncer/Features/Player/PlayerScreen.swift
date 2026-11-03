import SwiftUI

/// Player profile. `playerId` is Cricbuzz's profile id when known (squads); scorecard taps only have a name.
struct PlayerScreen: View {
    let playerId: String?
    let name: String

    var body: some View {
        EmptyState(title: name, message: "Player profile coming soon.")
            .navigationTitle(name)
    }
}
