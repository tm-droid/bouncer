import SwiftUI

struct NewsArticleScreen: View {
    let storyId: Int
    var body: some View {
        ScrollView { Color.clear }
            .background(BouncerColor.bg)
            .overlay { Text("News article").font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary) }
            .navigationTitle("News")
            .navigationBarTitleDisplayMode(.inline)
    }
}
