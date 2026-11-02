import SwiftUI

struct MatchDetailScreen: View {
    let matchId: String
    var body: some View {
        ScrollView { Color.clear }
            .background(BouncerColor.bg)
            .overlay { Text("Match detail").font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary) }
            .navigationTitle("Match")
            .navigationBarTitleDisplayMode(.inline)
    }
}
