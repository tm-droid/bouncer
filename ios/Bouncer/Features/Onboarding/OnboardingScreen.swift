import SwiftUI

/// First-run flow. Calls `onFinish` once the user is done; RootView stores the flag.
struct OnboardingScreen: View {
    var onFinish: () -> Void

    var body: some View {
        VStack(spacing: Spacing.xl) {
            BouncerWordmark()
            Button("Get started", action: onFinish).buttonStyle(.borderedProminent).tint(BouncerColor.goldFill).foregroundStyle(BouncerColor.onGold)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(BouncerColor.bg)
    }
}
