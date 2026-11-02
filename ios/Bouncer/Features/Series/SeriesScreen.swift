import SwiftUI

struct SeriesScreen: View {
    var body: some View {
        ScrollView { Color.clear }
            .background(BouncerColor.bg)
            .overlay { Text("Series").font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary) }
            .navigationTitle("Series")
            .navigationBarTitleDisplayMode(.inline)
    }
}
