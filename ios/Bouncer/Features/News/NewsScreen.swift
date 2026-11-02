import SwiftUI

struct NewsScreen: View {
    var body: some View {
        ScrollView { Color.clear }
            .background(BouncerColor.bg)
            .overlay { Text("News").font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary) }
            .navigationTitle("News")
            .navigationBarTitleDisplayMode(.inline)
    }
}
