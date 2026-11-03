import SwiftUI

/// Team page, keyed by the team's display name (as used by favourites and TeamLogos.json).
struct TeamScreen: View {
    let name: String

    var body: some View {
        EmptyState(title: name, message: "Team page coming soon.")
            .navigationTitle(name)
    }
}
