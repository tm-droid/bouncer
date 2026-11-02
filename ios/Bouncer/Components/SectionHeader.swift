import SwiftUI

struct SectionHeader: View {
    let title: String
    var action: String?
    var onAction: (() -> Void)?

    var body: some View {
        HStack {
            Text(title).font(.titleSmall.weight(.semibold)).foregroundStyle(BouncerColor.inkPrimary)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            if let action, let onAction {
                Button(action: onAction) {
                    Text(action).font(.labelLarge).foregroundStyle(BouncerColor.goldInk)
                        .frame(minHeight: 44)
                }
            }
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
    }
}

#Preview("Light") {
    SectionHeader(title: "Live matches", action: "See all") {}.background(BouncerColor.bg)
}

#Preview("Dark") {
    SectionHeader(title: "Live matches", action: "See all") {}.background(BouncerColor.bg).preferredColorScheme(.dark)
}
