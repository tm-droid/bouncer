import SwiftUI

/// Flat surface card: radius 12, 1pt divider border, no shadow.
struct BouncerCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(BouncerColor.surface, in: RoundedRectangle(cornerRadius: Radius.md))
            .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(BouncerColor.divider, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }
}

#Preview("Light") {
    BouncerCard {
        VStack(spacing: 0) {
            StatRow(label: "Toss", value: "India")
            StatRow(label: "Venue", value: "Wankhede", showDivider: false)
        }
    }
    .padding().background(BouncerColor.bg)
}

#Preview("Dark") {
    BouncerCard { StatRow(label: "Toss", value: "India", showDivider: false) }
        .padding().background(BouncerColor.bg).preferredColorScheme(.dark)
}
