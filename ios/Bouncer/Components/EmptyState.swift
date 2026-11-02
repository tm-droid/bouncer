import SwiftUI

struct EmptyState: View {
    let title: String
    let message: String
    var systemImage = "magnifyingglass"
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 0) {
            Image(systemName: systemImage)
                .font(.system(size: 40)).foregroundStyle(BouncerColor.inkSecondary)
                .frame(width: 96, height: 96).background(BouncerColor.surfaceVariant, in: Circle())
                .accessibilityHidden(true)
            Text(title).font(.titleLarge).foregroundStyle(BouncerColor.inkPrimary).padding(.top, Spacing.lg)
            Text(message).font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary).padding(.top, Spacing.sm)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle).font(.bouncer(14, .semibold, relativeTo: .subheadline)).foregroundStyle(BouncerColor.goldInk)
                        .frame(minHeight: 44).padding(.horizontal, Spacing.md)
                }
                .padding(.top, Spacing.sm)
            }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity).padding(Spacing.xxl)
    }
}

#Preview("Light") {
    EmptyState(title: "No matches", message: "Nothing is live right now.", actionTitle: "Retry") {}.background(BouncerColor.bg)
}

#Preview("Dark") {
    EmptyState(title: "No matches", message: "Nothing is live right now.").background(BouncerColor.bg).preferredColorScheme(.dark)
}
