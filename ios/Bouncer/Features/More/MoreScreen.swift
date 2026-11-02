import SwiftUI

struct MoreScreen: View {
    var body: some View {
        ScrollView { Color.clear }
            .background(BouncerColor.bg)
            .overlay { Text("More").font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary) }
            .navigationTitle("More")
            .navigationBarTitleDisplayMode(.inline)
    }
}
