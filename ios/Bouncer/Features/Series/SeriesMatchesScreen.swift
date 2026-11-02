import SwiftUI

struct SeriesMatchesScreen: View {
    let seriesId: String
    var body: some View {
        ScrollView { Color.clear }
            .background(BouncerColor.bg)
            .overlay { Text("Series matches").font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary) }
            .navigationTitle("Series")
            .navigationBarTitleDisplayMode(.inline)
    }
}
