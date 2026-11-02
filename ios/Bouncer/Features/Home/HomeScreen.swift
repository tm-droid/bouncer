import SwiftUI

struct HomeScreen: View {
    var body: some View {
        ScrollView { Color.clear }
            .background(BouncerColor.bg)
            .overlay { Text("Home").font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary) }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .principal) { BouncerWordmark() } }
    }
}
